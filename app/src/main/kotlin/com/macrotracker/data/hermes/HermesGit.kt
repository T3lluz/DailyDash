package com.macrotracker.data.hermes

import org.json.JSONArray
import org.json.JSONObject

/*
 * Git and pull requests as the bridge's git API (gitapi.py, under /_api/ai/git/) answers them: what T3 Code's phone app
 * has in its git controls, review screens and pull request pages, for the repos on the server.
 */

data class GitRepo(
    val path: String,
    val name: String,
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val dirty: Int,
    val remote: String,
    val lastMs: Long,
)

data class GitFile(
    val path: String,
    val x: Char,
    val y: Char,
    val staged: Boolean,
    val unstaged: Boolean,
    val untracked: Boolean,
    val conflict: Boolean,
    val plus: Int,
    val minus: Int,
    val from: String?,
) {
    /** `M`, `A`, `D`, `R`, `?`, `U`: one letter for the badge, the staged side first. */
    val badge: Char get() = when {
        conflict -> 'U'
        untracked -> '?'
        x != '.' && x != ' ' -> x
        else -> y
    }
    val name: String get() = path.substringAfterLast('/')
    val dir: String get() = path.substringBeforeLast('/', "")
}

data class GitStatus(
    val repo: String,
    val name: String,
    val branch: String,
    val upstream: String,
    val ahead: Int,
    val behind: Int,
    val files: List<GitFile>,
    val lastShort: String,
    val lastSubject: String,
    val lastMs: Long,
    val stashes: Int,
) {
    val clean: Boolean get() = files.isEmpty()
    val staged: List<GitFile> get() = files.filter { it.staged }
    val changed: List<GitFile> get() = files.filter { !it.staged && !it.untracked }
    val untracked: List<GitFile> get() = files.filter { it.untracked && !it.staged }
}

data class GitCommit(val hash: String, val short: String, val author: String, val atMs: Long, val subject: String, val refs: List<String>)

data class GitChangedFile(val path: String, val status: String, val plus: Int, val minus: Int)

data class GitCommitInfo(val hash: String, val author: String, val atMs: Long, val message: String, val files: List<GitChangedFile>)

data class GitBranch(val name: String, val current: Boolean, val upstream: String, val atMs: Long, val subject: String, val track: String)

data class GitBranches(val local: List<GitBranch>, val remote: List<GitBranch>, val base: String)

data class GitCompare(val base: String, val range: String, val files: List<GitChangedFile>, val commits: List<GitCommit>)

data class GitChecks(val total: Int, val ok: Int, val bad: Int, val pending: Int)

data class GitPr(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean,
    val author: String,
    val head: String,
    val base: String,
    val updated: String,
    val url: String,
    val decision: String,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val checks: GitChecks,
    val mergeState: String,
)

data class GitCheck(val name: String, val state: String, val url: String)
data class GitReview(val author: String, val state: String, val body: String)
data class GitComment(val author: String, val body: String, val at: String)

data class GitPrDetail(
    val pr: GitPr,
    val body: String,
    val files: List<GitChangedFile>,
    val commits: List<Pair<String, String>>,
    val reviews: List<GitReview>,
    val comments: List<GitComment>,
    val checkList: List<GitCheck>,
    val labels: List<String>,
)

object GitParse {
    private fun JSONObject.ints(k: String) = optInt(k)

    fun repo(o: JSONObject) = GitRepo(
        o.optString("path"), o.optString("name"), o.optString("branch"), o.optInt("ahead"), o.optInt("behind"),
        o.optInt("dirty"), o.optString("remote"), o.optLong("last") * 1000,
    )

    fun status(o: JSONObject): GitStatus {
        val files = o.optJSONArray("files").map { f ->
            GitFile(
                path = f.optString("path"),
                x = f.optString("x", ".").firstOrNull() ?: '.',
                y = f.optString("y", ".").firstOrNull() ?: '.',
                staged = f.optBoolean("staged"),
                unstaged = f.optBoolean("unstaged"),
                untracked = f.optBoolean("untracked"),
                conflict = f.optBoolean("conflict"),
                plus = f.optInt("plus"),
                minus = f.optInt("minus"),
                from = f.optString("from").takeIf { it.isNotBlank() },
            )
        }
        val last = o.optJSONObject("last")
        return GitStatus(
            o.optString("repo"), o.optString("name"), o.optString("branch"), o.optString("upstream"),
            o.optInt("ahead"), o.optInt("behind"), files,
            last?.optString("short").orEmpty(), last?.optString("subject").orEmpty(), (last?.optLong("at") ?: 0L) * 1000,
            o.optInt("stashes"),
        )
    }

    fun commit(o: JSONObject) = GitCommit(
        o.optString("hash"), o.optString("short"), o.optString("author"), o.optLong("at") * 1000, o.optString("subject"),
        (0 until (o.optJSONArray("refs")?.length() ?: 0)).map { i -> o.optJSONArray("refs")!!.optString(i) },
    )

    fun changed(o: JSONObject) = GitChangedFile(o.optString("path"), o.optString("status", "M"), o.optInt("plus"), o.optInt("minus"))

    fun branch(o: JSONObject) = GitBranch(o.optString("name"), o.optBoolean("current"), o.optString("upstream"), o.optLong("at") * 1000, o.optString("subject"), o.optString("track"))

    fun checks(o: JSONObject?) = GitChecks(o?.optInt("total") ?: 0, o?.optInt("ok") ?: 0, o?.optInt("bad") ?: 0, o?.optInt("pending") ?: 0)

    fun pr(o: JSONObject) = GitPr(
        o.optInt("number"), o.optString("title"), o.optString("state"), o.optBoolean("isDraft"), o.optString("author"),
        o.optString("headRefName"), o.optString("baseRefName"), o.optString("updatedAt"), o.optString("url"),
        o.optString("reviewDecision"), o.optInt("additions"), o.optInt("deletions"), o.optInt("changedFiles"),
        checks(o.optJSONObject("checks")), o.optString("mergeStateStatus"),
    )

    fun prDetail(o: JSONObject): GitPrDetail = GitPrDetail(
        pr = pr(o),
        body = o.optString("body"),
        files = o.optJSONArray("files").map { f ->
            GitChangedFile(f.optString("path"), "M", f.optInt("additions"), f.optInt("deletions"))
        },
        commits = o.optJSONArray("commits").map { it.optString("short") to it.optString("subject") },
        reviews = o.optJSONArray("reviews").map { GitReview(it.optString("author"), it.optString("state"), it.optString("body")) },
        comments = o.optJSONArray("comments").map { GitComment(it.optString("author"), it.optString("body"), it.optString("at")) },
        checkList = o.optJSONArray("checkList").map { GitCheck(it.optString("name"), it.optString("state"), it.optString("url")) },
        labels = (0 until (o.optJSONArray("labels")?.length() ?: 0)).map { o.optJSONArray("labels")!!.optString(it) },
    )

    private fun <T> JSONArray?.map(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }
}

/** One line of a diff, with the line numbers it has on each side (0 where it has none). */
data class DiffLine(val kind: Kind, val old: Int, val new: Int, val text: String) {
    enum class Kind { CONTEXT, ADD, DEL, NOTE }
}

data class DiffHunk(val header: String, val lines: List<DiffLine>)

data class DiffFile(
    val path: String,
    val oldPath: String?,
    val status: String,
    val hunks: List<DiffHunk>,
    val binary: Boolean,
) {
    val plus: Int get() = hunks.sumOf { h -> h.lines.count { it.kind == DiffLine.Kind.ADD } }
    val minus: Int get() = hunks.sumOf { h -> h.lines.count { it.kind == DiffLine.Kind.DEL } }
}

/** A unified diff, split per file and per hunk with its line numbers worked out: what the review screen draws. */
object GitDiff {
    private val hunkHead = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)$")

    fun parse(text: String): List<DiffFile> {
        if (text.isBlank()) return emptyList()
        val files = ArrayList<DiffFile>()
        var path = ""
        var oldPath: String? = null
        var status = "M"
        var binary = false
        var hunks = ArrayList<DiffHunk>()
        var lines = ArrayList<DiffLine>()
        var header = ""
        var oldNo = 0
        var newNo = 0
        var open = false
        fun closeHunk() {
            if (header.isNotEmpty() || lines.isNotEmpty()) hunks += DiffHunk(header, lines)
            lines = ArrayList()
            header = ""
        }
        fun closeFile() {
            closeHunk()
            if (path.isNotEmpty()) files += DiffFile(path, oldPath, status, hunks, binary)
            hunks = ArrayList()
            path = ""
            oldPath = null
            status = "M"
            binary = false
        }
        for (raw in text.lineSequence()) {
            when {
                raw.startsWith("diff --git ") -> {
                    if (open) closeFile()
                    open = true
                    val m = Regex("^diff --git a/(.*) b/(.*)$").find(raw)
                    path = m?.groupValues?.get(2) ?: raw.removePrefix("diff --git ")
                    oldPath = m?.groupValues?.get(1)?.takeIf { it != path }
                }
                !open -> Unit
                raw.startsWith("new file mode") -> status = "A"
                raw.startsWith("deleted file mode") -> status = "D"
                raw.startsWith("rename from") -> status = "R"
                raw.startsWith("Binary files") || raw.startsWith("GIT binary patch") -> binary = true
                raw.startsWith("--- ") && header.isEmpty() && lines.isEmpty() -> Unit
                raw.startsWith("+++ ") && header.isEmpty() && lines.isEmpty() -> {
                    val p = raw.removePrefix("+++ ")
                    if (p != "/dev/null" && p.startsWith("b/")) path = p.removePrefix("b/")
                }
                raw.startsWith("@@") -> {
                    closeHunk()
                    val m = hunkHead.find(raw)
                    oldNo = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    newNo = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    header = raw
                }
                header.isNotEmpty() && raw.startsWith("+") -> lines += DiffLine(DiffLine.Kind.ADD, 0, newNo++, raw.drop(1))
                header.isNotEmpty() && raw.startsWith("-") -> lines += DiffLine(DiffLine.Kind.DEL, oldNo++, 0, raw.drop(1))
                header.isNotEmpty() && raw.startsWith("\\") -> lines += DiffLine(DiffLine.Kind.NOTE, 0, 0, raw.drop(1).trim())
                header.isNotEmpty() && (raw.startsWith(" ") || raw.isEmpty()) ->
                    if (raw.isNotEmpty() || lines.isNotEmpty()) lines += DiffLine(DiffLine.Kind.CONTEXT, oldNo++, newNo++, raw.drop(1))
            }
        }
        if (open) closeFile()
        return files
    }
}
