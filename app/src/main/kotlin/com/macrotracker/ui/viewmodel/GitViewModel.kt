package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.hermes.DiffFile
import com.macrotracker.data.hermes.GitBranches
import com.macrotracker.data.hermes.GitCommit
import com.macrotracker.data.hermes.GitCommitInfo
import com.macrotracker.data.hermes.GitCompare
import com.macrotracker.data.hermes.GitDiff
import com.macrotracker.data.hermes.GitParse
import com.macrotracker.data.hermes.GitPr
import com.macrotracker.data.hermes.GitPrDetail
import com.macrotracker.data.hermes.GitRepo
import com.macrotracker.data.hermes.GitStatus
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Something being fetched: nothing yet, on its way, here, or why it did not come. */
data class Load<T>(val value: T? = null, val loading: Boolean = false, val error: String? = null)

/** Where a diff comes from; the review screen asks for its files and each file's lines through this. */
sealed interface DiffSource {
    val key: String

    data class Working(val staged: Boolean) : DiffSource { override val key get() = "w-$staged" }
    data class Commit(val hash: String) : DiffSource { override val key get() = "c-$hash" }
    data class Range(val range: String) : DiffSource { override val key get() = "r-$range" }
    data class Pr(val number: Int) : DiffSource { override val key get() = "p-$number" }
}

/** A note on one line of a diff, kept until it is sent to Hermes. */
data class ReviewComment(val path: String, val line: Int, val isNew: Boolean, val code: String, val text: String)

data class GitUi(
    val repos: List<GitRepo> = emptyList(),
    val repo: String? = null,
    val status: Load<GitStatus> = Load(loading = true),
    /** What is running (Pushing…), so the buttons wait. */
    val busy: String? = null,
    /** The last action's outcome, shown once. */
    val notice: String? = null,
    val reposError: String? = null,
)

/**
 * The git screen's state for the repo it is on: status, history, branches, pull requests, diffs, and the
 * review comments written on them. Everything goes through the bridge, which runs git on the server.
 */
@HiltViewModel
class GitViewModel @Inject constructor(
    private val client: HermesClient,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(GitUi())
    val ui: StateFlow<GitUi> = _ui

    private val _log = MutableStateFlow(Load<List<GitCommit>>())
    val log: StateFlow<Load<List<GitCommit>>> = _log
    private val _branches = MutableStateFlow(Load<GitBranches>())
    val branches: StateFlow<Load<GitBranches>> = _branches
    private val _compare = MutableStateFlow(Load<GitCompare>())
    val compare: StateFlow<Load<GitCompare>> = _compare
    private val _prs = MutableStateFlow(Load<List<GitPr>>())
    val prs: StateFlow<Load<List<GitPr>>> = _prs
    private val _pr = MutableStateFlow(Load<GitPrDetail>())
    val pr: StateFlow<Load<GitPrDetail>> = _pr
    private val _commit = MutableStateFlow(Load<GitCommitInfo>())
    val commit: StateFlow<Load<GitCommitInfo>> = _commit

    private val _comments = MutableStateFlow<List<ReviewComment>>(emptyList())
    val comments: StateFlow<List<ReviewComment>> = _comments

    private var prState = "open"
    private val diffCache = HashMap<String, List<DiffFile>>()

    init {
        loadRepos()
    }

    private val repo: String get() = _ui.value.repo.orEmpty()

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    fun loadRepos() {
        viewModelScope.launch {
            try {
                val list = client.gitGet("repos", "fresh" to "1").optJSONArray("repos").let { a ->
                    (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::repo) }
                }
                val saved = settings.gitRepo
                val pick = list.firstOrNull { it.path == saved }?.path ?: list.firstOrNull()?.path
                _ui.update { it.copy(repos = list, repo = _ui.value.repo ?: pick, reposError = null) }
                if (_ui.value.status.value == null && pick != null) refresh()
                if (pick == null) _ui.update { it.copy(status = Load(error = "No git repos on the server.")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(reposError = e.message ?: "Could not reach the bridge", status = Load(error = e.message)) }
            }
        }
    }

    fun selectRepo(path: String) {
        if (path == repo) return
        settings.gitRepo = path
        diffCache.clear()
        _comments.value = emptyList()
        _log.value = Load(); _branches.value = Load(); _compare.value = Load(); _prs.value = Load(); _pr.value = Load()
        _ui.update { it.copy(repo = path, status = Load(loading = true)) }
        refresh()
    }

    fun refresh() {
        val r = repo.ifEmpty { return }
        _ui.update { it.copy(status = it.status.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val st = GitParse.status(client.gitGet("status", "repo" to r))
                _ui.update { s -> if (s.repo == r) s.copy(status = Load(st), repos = s.repos.map { if (it.path == r) it.copy(branch = st.branch, ahead = st.ahead, behind = st.behind, dirty = st.files.size) else it }) else s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { s -> if (s.repo == r) s.copy(status = s.status.copy(loading = false, error = e.message)) else s }
            }
        }
    }

    /** One write, then the fresh status the bridge hands back; a refusal is the notice. */
    private fun act(label: String, route: String, body: JSONObject, after: (JSONObject) -> Unit = {}) {
        val r = repo.ifEmpty { return }
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = label, notice = null) }
        viewModelScope.launch {
            try {
                val res = client.gitPost(route, body.put("repo", r))
                val st = (res.optJSONObject("status") ?: res.takeIf { it.has("files") })?.let(GitParse::status)
                diffCache.clear()
                _ui.update { s ->
                    s.copy(
                        busy = null,
                        status = if (st != null) Load(st) else s.status,
                        notice = res.optString("out").lineSequence().lastOrNull { it.isNotBlank() }?.take(160),
                    )
                }
                after(res)
                if (st == null) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, notice = e.message ?: "$label failed") }
            }
        }
    }

    fun stage(paths: List<String>, unstage: Boolean = false) =
        act(if (unstage) "Unstaging" else "Staging", "stage", JSONObject().put("paths", JSONArray(paths)).put("unstage", unstage))

    fun discard(path: String, untracked: Boolean) =
        act("Discarding", "discard", JSONObject().put("path", path).put("untracked", untracked))

    fun commit(message: String, all: Boolean, amend: Boolean = false) =
        act("Committing", "commit", JSONObject().put("message", message).put("all", all).put("amend", amend)) { _log.value = Load() }

    fun sync(how: String) = act(when (how) { "pull" -> "Pulling"; "push" -> "Pushing"; else -> "Fetching" }, "sync", JSONObject().put("how", how)) {
        _log.value = Load(); _branches.value = Load()
    }

    fun checkout(branch: String, create: Boolean = false, start: String = "") =
        act(if (create) "Creating $branch" else "Switching to $branch", "checkout", JSONObject().put("branch", branch).put("create", create).put("start", start)) {
            _log.value = Load(); _branches.value = Load(); _compare.value = Load(); _prs.value = Load()
        }

    fun stash(pop: Boolean) = act(if (pop) "Restoring stash" else "Stashing", "stash", JSONObject().put("pop", pop))

    // ── Reads ──────────────────────────────────────────────────────────────

    private inline fun <T> load(target: MutableStateFlow<Load<T>>, force: Boolean = false, crossinline fetch: suspend (String) -> T) {
        val r = repo.ifEmpty { return }
        if (!force && (target.value.loading || target.value.value != null)) return
        target.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val v = fetch(r)
                if (repo == r) target.value = Load(v)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                target.update { it.copy(loading = false, error = e.message ?: "Failed") }
            }
        }
    }

    fun loadLog(force: Boolean = false) = load(_log, force) { r ->
        client.gitGet("log", "repo" to r, "n" to "60").optJSONArray("commits").let { a ->
            (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::commit) }
        }
    }

    fun loadBranches(force: Boolean = false) = load(_branches, force) { r ->
        val o = client.gitGet("branches", "repo" to r)
        fun list(k: String) = o.optJSONArray(k).let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::branch) } }
        GitBranches(list("local"), list("remote"), o.optString("base", "main"))
    }

    fun loadCompare(base: String) {
        _compare.value = Load(loading = true)
        val r = repo.ifEmpty { return }
        viewModelScope.launch {
            try {
                val o = client.gitGet("compare", "repo" to r, "base" to base)
                _compare.value = Load(
                    GitCompare(
                        o.optString("base"), o.optString("range"),
                        o.optJSONArray("files").let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::changed) } },
                        o.optJSONArray("commits").let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::commit) } },
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _compare.value = Load(error = e.message)
            }
        }
    }

    fun loadPrs(state: String = prState, force: Boolean = false) {
        val changed = state != prState
        prState = state
        load(_prs, force || changed) { r ->
            client.gitGet("prs", "repo" to r, "state" to state).optJSONArray("prs").let { a ->
                (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::pr) }
            }
        }
    }

    val prFilter: String get() = prState

    fun loadPr(n: Int) {
        _pr.value = Load(loading = true)
        val r = repo.ifEmpty { return }
        viewModelScope.launch {
            try {
                _pr.value = Load(GitParse.prDetail(client.gitGet("pr", "repo" to r, "n" to n.toString())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pr.value = Load(error = e.message)
            }
        }
    }

    fun loadCommit(hash: String) {
        _commit.value = Load(loading = true)
        val r = repo.ifEmpty { return }
        viewModelScope.launch {
            try {
                val o = client.gitGet("commit", "repo" to r, "hash" to hash)
                _commit.value = Load(
                    GitCommitInfo(
                        o.optString("hash"), o.optString("author"), o.optLong("at") * 1000, o.optString("message"),
                        o.optJSONArray("files").let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.let(GitParse::changed) } },
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commit.value = Load(error = e.message)
            }
        }
    }

    /** A pull request action (comment, review, merge): the page is read again with its result. */
    fun prAction(route: String, n: Int, vararg extra: Pair<String, String>) {
        val r = repo.ifEmpty { return }
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "Working", notice = null) }
        viewModelScope.launch {
            try {
                val body = JSONObject().put("repo", r).put("n", n)
                extra.forEach { (k, v) -> body.put(k, v) }
                val o = client.gitPost("pr/$route", body)
                if (o.has("number")) _pr.value = Load(GitParse.prDetail(o))
                _ui.update { it.copy(busy = null, notice = when (route) { "merge" -> "Merged #$n"; "review" -> "Review sent"; "comment" -> "Comment posted"; else -> "Done" }) }
                _prs.value = Load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, notice = e.message ?: "That did not work") }
            }
        }
    }

    fun createPr(title: String = "", body: String = "", onUrl: (String) -> Unit = {}) {
        val r = repo.ifEmpty { return }
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "Opening a pull request", notice = null) }
        viewModelScope.launch {
            try {
                val o = client.gitPost("pr/create", JSONObject().put("repo", r).put("title", title).put("body", body))
                _ui.update { it.copy(busy = null, notice = "Pull request opened") }
                _prs.value = Load()
                onUrl(o.optString("url"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, notice = e.message ?: "Could not open it") }
            }
        }
    }

    // ── Diffs and review comments ─────────────────────────────────────────

    /** The files of a diff with their hunks. A working file is read alone; the others once, whole, and kept. */
    suspend fun diffFiles(source: DiffSource, path: String? = null): List<DiffFile> {
        val r = repo
        val cacheKey = source.key + (if (source is DiffSource.Working) "|$path" else "")
        diffCache[cacheKey]?.let { return it }
        val params = ArrayList<Pair<String, String>>()
        params += "repo" to r
        val route = when (source) {
            is DiffSource.Working -> { params += "path" to path.orEmpty(); params += "staged" to (if (source.staged) "1" else "0"); "diff" }
            is DiffSource.Commit -> { params += "commit" to source.hash; "diff" }
            is DiffSource.Range -> { params += "range" to source.range; "diff" }
            is DiffSource.Pr -> { params += "n" to source.number.toString(); "pr/diff" }
        }
        val files = GitDiff.parse(client.gitGet(route, *params.toTypedArray()).optString("diff"))
        diffCache[cacheKey] = files
        return files
    }

    fun addComment(c: ReviewComment) = _comments.update { all -> all.filterNot { it.path == c.path && it.line == c.line && it.isNew == c.isNew } + c }
    fun removeComment(c: ReviewComment) = _comments.update { it - c }
    fun clearComments() { _comments.value = emptyList() }

    /** The comments as a message for Hermes: where each was made, the line it is about, and what was said. */
    fun reviewPrompt(context: String): String = buildString {
        val st = _ui.value.status.value
        append("Review comments on ").append(_ui.value.repos.firstOrNull { it.path == repo }?.name ?: "the repo")
        st?.branch?.takeIf { it.isNotBlank() }?.let { append(" (branch ").append(it).append(')') }
        if (context.isNotBlank()) append(", ").append(context)
        append(". Please go through them and make the changes:\n")
        _comments.value.groupBy { it.path }.forEach { (path, list) ->
            append("\n").append(path).append('\n')
            list.sortedBy { it.line }.forEach { c ->
                append("- line ").append(c.line).append(if (c.isNew) "" else " (the old side)")
                if (c.code.isNotBlank()) append(": `").append(c.code.trim().take(120)).append('`')
                append("\n  ").append(c.text.trim().replace("\n", "\n  ")).append('\n')
            }
        }
    }

    fun repoPath(): String = repo
}
