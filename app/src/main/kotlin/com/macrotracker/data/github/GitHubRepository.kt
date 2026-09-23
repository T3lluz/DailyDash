package com.macrotracker.data.github

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class GitHubNeedsAuthException : IOException("Connect GitHub to load your issues, PRs, and activity")

interface GitHubRepository {
    suspend fun getDashboard(forceRefresh: Boolean = false): Result<GitHubSnapshot>
    suspend fun getRepoFocus(
        fullName: String,
        me: String,
        forceRefresh: Boolean = false,
    ): Result<GitHubRepoFocus>
    fun getCachedDashboard(): GitHubSnapshot?
    fun hasToken(): Boolean
    fun invalidateCache()
    val lastFetchTimeMs: Long
}

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val authClient: GitHubAuthClient,
    @ApplicationContext private val context: Context,
) : GitHubRepository {

    companion object {
        private const val TAG = "GitHubRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L
        private const val FOCUS_CACHE_MS = 2 * 60 * 1000L
        private const val PREFS = "github_dashboard_cache"
        private const val KEY_SNAPSHOT = "snapshot_json"
        private const val KEY_FETCH_TIME = "fetch_time"
        private const val KEY_CACHE_USER = "cache_user"
        private const val CACHE_VERSION = 4
        private const val KEY_CACHE_VERSION = "cache_version"
        private const val API = "https://api.github.com"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val fetchMutex = Mutex()
    private val focusMutex = Mutex()

    @Volatile private var cached: GitHubSnapshot? = null
    @Volatile private var lastFetchTime: Long = 0L
    @Volatile private var cachedUserKey: String = ""
    private val focusCache = LinkedHashMap<String, Pair<Long, GitHubRepoFocus>>()

    override val lastFetchTimeMs: Long get() = lastFetchTime
    override fun getCachedDashboard(): GitHubSnapshot? = cached
    override fun hasToken(): Boolean =
        authClient.isConnected() ||
            settings.githubToken.value.isNotBlank() ||
            BuildConfig.GITHUB_TOKEN.isNotBlank()

    init {
        restoreDiskCache()
    }

    override fun invalidateCache() {
        cached = null
        lastFetchTime = 0L
        cachedUserKey = ""
        focusCache.clear()
        prefs.edit {
            remove(KEY_SNAPSHOT)
            remove(KEY_FETCH_TIME)
            remove(KEY_CACHE_USER)
        }
    }

    override suspend fun getDashboard(forceRefresh: Boolean): Result<GitHubSnapshot> = fetchMutex.withLock {
        val token = resolveToken()
        if (token.isBlank()) {
            return@withLock Result.failure(GitHubNeedsAuthException())
        }
        val cacheKey = token.hashCode().toString()
        val now = System.currentTimeMillis()

        if (cachedUserKey != cacheKey) {
            cached = null
            lastFetchTime = 0L
        }

        val cacheValid = !forceRefresh &&
            cached != null &&
            cachedUserKey == cacheKey &&
            lastFetchTime > 0L &&
            (now - lastFetchTime) < CACHE_DURATION_MS

        if (cacheValid) {
            return@withLock Result.success(cached!!)
        }

        return@withLock try {
            withContext(Dispatchers.IO) { fetchDashboard(token) }
                .also { snapshot ->
                    cached = snapshot
                    lastFetchTime = System.currentTimeMillis()
                    cachedUserKey = cacheKey
                    persistDiskCache(snapshot, cacheKey)
                }
                .let { Result.success(it) }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub dashboard fetch failed: ${e.message}", e)
            val fallback = cached?.takeIf { cachedUserKey == cacheKey }
            if (fallback != null) Result.success(fallback) else Result.failure(e)
        }
    }

    private suspend fun fetchDashboard(token: String): GitHubSnapshot = coroutineScope {
        val meResp = get("$API/user", token)
        val user = parseUser(JSONObject(meResp.body))
        val login = user.login

        val issuesSearch = async {
            runCatching { searchIssues("is:open is:issue involves:@me", login, token) }.getOrNull()
        }
        val prSearch = async {
            runCatching { searchIssues("is:open is:pr involves:@me", login, token) }.getOrNull()
        }
        val reviewSearch = async {
            runCatching { searchIssues("is:open is:pr review-requested:@me", login, token) }.getOrNull()
        }
        val reposDeferred = async {
            runCatching {
                get("$API/user/repos?sort=pushed&per_page=50&affiliation=owner,collaborator,organization_member", token)
            }.getOrNull()
        }
        val eventsDeferred = async {
            runCatching { get("$API/users/$login/events?per_page=50", token) }.getOrNull()
        }
        val notifDeferred = async {
            runCatching { fetchNotifications(token) }.getOrElse { emptyList<GitHubNotification>() to true }
        }
        val contributionsDeferred = async {
            runCatching { fetchContributions(token) }
                .onFailure { Log.w(TAG, "Contribution calendar unavailable: ${it.message}") }
                .getOrNull()
        }

        val issuePage = issuesSearch.await()
        val prPage = prSearch.await()
        val reviewPage = reviewSearch.await()
        val reposResp = reposDeferred.await()
        val eventsResp = eventsDeferred.await()
        val (notifications, notifNeedReconnect) = notifDeferred.await()

        val reviewKeys = reviewPage?.items
            ?.map { it.repoFullName to it.number }
            ?.toSet()
            .orEmpty()

        val pulls = LinkedHashMap<Pair<String, Int>, GitHubPullRequest>()
        prPage?.items?.forEach { issue ->
            val pr = issue.toPull(me = login, reviewRequested = (issue.repoFullName to issue.number) in reviewKeys)
            pulls[issue.repoFullName to issue.number] = pr
        }
        reviewPage?.items?.forEach { issue ->
            val key = issue.repoFullName to issue.number
            val existing = pulls[key]
            pulls[key] = existing?.copy(reviewRequested = true)
                ?: issue.toPull(me = login, reviewRequested = true)
        }

        val remaining = listOfNotNull(
            meResp.remaining,
            issuePage?.remaining,
            prPage?.remaining,
            reviewPage?.remaining,
            reposResp?.remaining,
            eventsResp?.remaining,
        ).minOrNull()
        val limit = listOfNotNull(
            meResp.limit,
            issuePage?.limit,
            prPage?.limit,
            reviewPage?.limit,
            reposResp?.limit,
            eventsResp?.limit,
        ).maxOrNull()

        GitHubSnapshot(
            user = user,
            issues = issuePage?.items.orEmpty().filter { !it.isPullRequest },
            pullRequests = pulls.values.toList(),
            activity = eventsResp?.let { parseEvents(it.body) }.orEmpty(),
            repos = reposResp?.let { parseRepos(it.body) }.orEmpty().sortedByRecent(),
            notifications = notifications,
            issueTotal = issuePage?.totalCount ?: 0,
            pullTotal = prPage?.totalCount ?: 0,
            reviewRequestedCount = reviewPage?.totalCount ?: reviewKeys.size,
            unreadNotificationCount = notifications.count { it.unread },
            notificationsNeedReconnect = notifNeedReconnect,
            rateLimitRemaining = remaining,
            rateLimitLimit = limit,
            contributions = contributionsDeferred.await() ?: cached?.contributions,
        )
    }

    /**
     * The year of contributions, the way the profile page counts them. One GraphQL
     * query, one point of the 5,000/hour budget — the same query the t3lluz
     * dashboard's collector runs.
     */
    private fun fetchContributions(token: String): GitHubContributions? {
        val query = """
            query {
              viewer {
                contributionsCollection {
                  restrictedContributionsCount
                  contributionCalendar {
                    totalContributions
                    weeks { contributionDays { date contributionCount contributionLevel } }
                  }
                }
              }
            }
        """.trimIndent()
        val body = JSONObject().put("query", query).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$API/graphql")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                if (authClient.isConnected()) authClient.markDisconnected()
                throw GitHubNeedsAuthException()
            }
            if (!response.isSuccessful) throw IOException("GitHub GraphQL HTTP ${response.code}")
            return parseContributions(response.body?.string().orEmpty())
        }
    }

    internal fun parseContributions(raw: String): GitHubContributions? {
        val collection = JSONObject(raw)
            .optJSONObject("data")
            ?.optJSONObject("viewer")
            ?.optJSONObject("contributionsCollection")
            ?: return null
        val calendar = collection.optJSONObject("contributionCalendar") ?: return null
        val weeks = calendar.optJSONArray("weeks") ?: return null
        val days = buildList {
            for (w in 0 until weeks.length()) {
                val week = weeks.optJSONObject(w)?.optJSONArray("contributionDays") ?: continue
                for (d in 0 until week.length()) {
                    val day = week.optJSONObject(d) ?: continue
                    val date = day.optString("date").takeIf { it.length == 10 } ?: continue
                    add(
                        GitHubContributionDay(
                            date = date,
                            count = day.optInt("contributionCount", 0),
                            level = contributionLevel(day.optString("contributionLevel")),
                        ),
                    )
                }
            }
        }.sortedBy { it.date }
        if (days.isEmpty()) return null
        return GitHubContributions(
            total = calendar.optInt("totalContributions", days.sumOf { it.count }),
            days = days,
            restricted = collection.optInt("restrictedContributionsCount", 0),
        )
    }

    private fun contributionLevel(raw: String): Int = when (raw) {
        "FIRST_QUARTILE" -> 1
        "SECOND_QUARTILE" -> 2
        "THIRD_QUARTILE" -> 3
        "FOURTH_QUARTILE" -> 4
        else -> 0
    }

    override suspend fun getRepoFocus(
        fullName: String,
        me: String,
        forceRefresh: Boolean,
    ): Result<GitHubRepoFocus> {
        val token = resolveToken()
        if (token.isBlank()) return Result.failure(GitHubNeedsAuthException())
        val key = fullName.trim()
        if (key.split('/').size != 2) {
            return Result.failure(IOException("Invalid repository"))
        }
        return focusMutex.withLock {
            val now = System.currentTimeMillis()
            val cachedFocus = focusCache[key]
            if (!forceRefresh && cachedFocus != null && now - cachedFocus.first < FOCUS_CACHE_MS) {
                return@withLock Result.success(cachedFocus.second)
            }
            try {
                withContext(Dispatchers.IO) { fetchRepoFocus(key, me, token) }
                    .also { focus ->
                        if (focusCache.size > 12) {
                            val oldest = focusCache.keys.firstOrNull()
                            if (oldest != null) focusCache.remove(oldest)
                        }
                        focusCache[key] = System.currentTimeMillis() to focus
                    }
                    .let { Result.success(it) }
            } catch (e: Exception) {
                Log.e(TAG, "GitHub repo focus fetch failed: ${e.message}", e)
                cachedFocus?.second?.let { Result.success(it) } ?: Result.failure(e)
            }
        }
    }

    private suspend fun fetchRepoFocus(fullName: String, me: String, token: String): GitHubRepoFocus = coroutineScope {
        val base = "$API/repos/$fullName"

        val repoDeferred = async { runCatching { get(base, token) }.getOrNull() }
        val issuesDeferred = async { runCatching { get("$base/issues?state=open&per_page=30", token) }.getOrNull() }
        val pullsDeferred = async { runCatching { get("$base/pulls?state=open&per_page=30", token) }.getOrNull() }
        val eventsDeferred = async { runCatching { getOptional("$base/events?per_page=30", token) }.getOrNull() }
        val commitsDeferred = async { runCatching { getOptional("$base/commits?per_page=12", token) }.getOrNull() }
        val runsDeferred = async { runCatching { getOptional("$base/actions/runs?per_page=8", token) }.getOrNull() }
        val releaseDeferred = async { runCatching { getOptional("$base/releases/latest", token) }.getOrNull() }

        val repoResp = repoDeferred.await() ?: throw IOException("Couldn’t load $fullName")
        val repo = parseRepo(JSONObject(repoResp.body))
            ?: throw IOException("Couldn’t load $fullName")
        val issueItems = issuesDeferred.await()?.let { parseIssueArray(JSONArray(it.body), me) }.orEmpty()
        val issues = issueItems.filter { !it.isPullRequest }.map {
            if (it.repoFullName.isBlank()) it.copy(repoFullName = fullName) else it
        }
        val pulls = pullsDeferred.await()
            ?.let { parseRepoPulls(JSONArray(it.body), me, fullName) }
            .orEmpty()
        val activity = eventsDeferred.await()?.let { parseEvents(it.body) }.orEmpty()
        val commits = commitsDeferred.await()?.let { parseCommits(it.body) }.orEmpty()
        val runs = runsDeferred.await()?.let { parseWorkflowRuns(it.body) }.orEmpty()
        val release = releaseDeferred.await()?.let { parseRelease(it.body) }

        GitHubRepoFocus(
            repo = repo,
            issues = issues,
            pullRequests = pulls,
            activity = activity,
            commits = commits,
            workflowRuns = runs,
            latestRelease = release,
        )
    }

    private data class SearchPage(
        val items: List<GitHubIssue>,
        val totalCount: Int,
        val remaining: Int?,
        val limit: Int?,
    )

    private fun searchIssues(query: String, me: String, token: String): SearchPage {
        val url = API.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("issues")
            .addQueryParameter("q", query)
            .addQueryParameter("sort", "updated")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", "50")
            .build()
            .toString()
        val resp = get(url, token)
        val root = JSONObject(resp.body)
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = parseIssueArray(arr, me)
        return SearchPage(
            items = items,
            totalCount = root.optInt("total_count", items.size),
            remaining = resp.remaining,
            limit = resp.limit,
        )
    }

    private suspend fun resolveToken(): String {
        authClient.validAccessToken()?.takeIf { it.isNotBlank() }?.let { return it }
        val stored = settings.githubToken.value.trim()
        if (stored.isNotBlank()) return stored
        return BuildConfig.GITHUB_TOKEN.trim()
    }

    private data class GhResponse(
        val body: String,
        val remaining: Int?,
        val limit: Int?,
    )

    private fun get(url: String, token: String): GhResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        okHttpClient.newCall(builder.build()).execute().use { response ->
            val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
            val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
            when (response.code) {
                401 -> {
                    if (authClient.isConnected()) authClient.markDisconnected()
                    throw GitHubNeedsAuthException()
                }
                403, 429 -> throw IOException("GitHub rate limited. Try again in a few minutes.")
                404 -> throw IOException("GitHub returned 404. Check OAuth scopes (repo + read:user).")
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub HTTP ${response.code}")
            }
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty GitHub response")
            return GhResponse(body, remaining, limit)
        }
    }

    private fun getOptional(url: String, token: String): GhResponse? {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (response.code == 401) {
                    if (authClient.isConnected()) authClient.markDisconnected()
                    throw GitHubNeedsAuthException()
                }
                if (!response.isSuccessful) return@use null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                GhResponse(
                    body = body,
                    remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull(),
                    limit = response.header("X-RateLimit-Limit")?.toIntOrNull(),
                )
            }
        } catch (e: GitHubNeedsAuthException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Optional GitHub GET failed: ${e.message}")
            null
        }
    }

    /** Returns notifications and whether the token is missing the notifications scope. */
    private fun fetchNotifications(token: String): Pair<List<GitHubNotification>, Boolean> {
        val url = "$API/notifications?all=true&per_page=30"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .header("Authorization", "Bearer $token")
            .get()
        return try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    401 -> throw GitHubNeedsAuthException()
                    403 -> return@use emptyList<GitHubNotification>() to true
                    404, 429 -> return@use emptyList<GitHubNotification>() to false
                }
                if (!response.isSuccessful) return@use emptyList<GitHubNotification>() to false
                val body = response.body?.string().orEmpty()
                parseNotifications(body) to false
            }
        } catch (e: GitHubNeedsAuthException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Notifications fetch failed: ${e.message}")
            emptyList<GitHubNotification>() to false
        }
    }

    private fun parseUser(o: JSONObject): GitHubUser = GitHubUser(
        login = o.str("login") ?: throw IOException("GitHub user missing login"),
        name = o.str("name"),
        avatarUrl = o.str("avatar_url"),
        htmlUrl = o.str("html_url") ?: "https://github.com/${o.str("login")}",
        bio = o.str("bio"),
        publicRepos = o.optInt("public_repos"),
        totalPrivateRepos = o.optInt("total_private_repos"),
        followers = o.optInt("followers"),
        following = o.optInt("following"),
    )

    private fun parseIssueArray(arr: JSONArray, me: String): List<GitHubIssue> {
        val out = ArrayList<GitHubIssue>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += parseIssue(o, me)
        }
        return out
    }

    private fun parseIssue(o: JSONObject, me: String): GitHubIssue {
        val user = o.optJSONObject("user")
        val assignees = o.optJSONArray("assignees")
        var assignedToMe = false
        if (assignees != null) {
            for (i in 0 until assignees.length()) {
                if (assignees.optJSONObject(i).str("login").equals(me, ignoreCase = true)) {
                    assignedToMe = true
                    break
                }
            }
        }
        return GitHubIssue(
            number = o.optInt("number"),
            title = o.str("title") ?: "",
            state = o.str("state") ?: "open",
            htmlUrl = o.str("html_url") ?: "",
            repoFullName = repoFullName(o),
            userLogin = user.str("login") ?: "unknown",
            userAvatarUrl = user.str("avatar_url"),
            comments = o.optInt("comments"),
            labels = parseLabels(o.optJSONArray("labels")),
            createdAt = o.str("created_at") ?: "",
            updatedAt = o.str("updated_at") ?: "",
            assignedToMe = assignedToMe,
            isPullRequest = o.optJSONObject("pull_request") != null,
            draft = o.optBoolean("draft", false),
        )
    }

    private fun GitHubIssue.toPull(me: String, reviewRequested: Boolean): GitHubPullRequest {
        return GitHubPullRequest(
            number = number,
            title = title,
            state = state,
            htmlUrl = htmlUrl,
            repoFullName = repoFullName,
            userLogin = userLogin,
            userAvatarUrl = userAvatarUrl,
            draft = draft,
            labels = labels,
            createdAt = createdAt,
            updatedAt = updatedAt,
            reviewRequested = reviewRequested,
            authoredByMe = userLogin.equals(me, ignoreCase = true),
        )
    }

    private fun parseRepos(body: String): List<GitHubRepo> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubRepo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseRepo(o)?.let { out += it }
        }
        return out
    }

    private fun parseRepo(o: JSONObject): GitHubRepo? {
        val ownerObj = o.optJSONObject("owner")
        return GitHubRepo(
            owner = ownerObj.str("login") ?: "",
            name = o.str("name") ?: return null,
            fullName = o.str("full_name") ?: return null,
            description = o.str("description"),
            htmlUrl = o.str("html_url") ?: return null,
            language = o.str("language"),
            stars = o.optInt("stargazers_count"),
            forks = o.optInt("forks_count"),
            openIssuesCount = o.optInt("open_issues_count"),
            isPrivate = o.optBoolean("private", false),
            pushedAt = o.str("pushed_at"),
            updatedAt = o.str("updated_at"),
            defaultBranch = o.str("default_branch"),
            license = o.optJSONObject("license").str("spdx_id") ?: o.optJSONObject("license").str("name"),
            homepage = o.str("homepage"),
            ownerAvatarUrl = ownerObj.str("avatar_url"),
        )
    }

    private fun parseRepoPulls(arr: JSONArray, me: String, repoFullName: String): List<GitHubPullRequest> {
        val out = ArrayList<GitHubPullRequest>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val user = o.optJSONObject("user")
            val reviewers = o.optJSONArray("requested_reviewers")
            var reviewRequested = false
            if (reviewers != null) {
                for (j in 0 until reviewers.length()) {
                    if (reviewers.optJSONObject(j).str("login").equals(me, ignoreCase = true)) {
                        reviewRequested = true
                        break
                    }
                }
            }
            out += GitHubPullRequest(
                number = o.optInt("number"),
                title = o.str("title") ?: "",
                state = o.str("state") ?: "open",
                htmlUrl = o.str("html_url") ?: "",
                repoFullName = repoFullName,
                userLogin = user.str("login") ?: "unknown",
                userAvatarUrl = user.str("avatar_url"),
                draft = o.optBoolean("draft", false),
                mergedAt = o.str("merged_at"),
                labels = parseLabels(o.optJSONArray("labels")),
                createdAt = o.str("created_at") ?: "",
                updatedAt = o.str("updated_at") ?: "",
                reviewRequested = reviewRequested,
                authoredByMe = user.str("login").equals(me, ignoreCase = true),
            )
        }
        return out
    }

    private fun parseCommits(body: String): List<GitHubCommit> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubCommit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sha = o.str("sha") ?: continue
            val commit = o.optJSONObject("commit")
            val message = commit.str("message")?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            val author = o.optJSONObject("author")
            out += GitHubCommit(
                sha = sha,
                message = message.ifBlank { sha.take(7) },
                htmlUrl = o.str("html_url") ?: "https://github.com",
                authorLogin = author.str("login") ?: commit?.optJSONObject("author").str("name"),
                authorAvatarUrl = author.str("avatar_url"),
                committedAt = commit?.optJSONObject("committer").str("date")
                    ?: commit?.optJSONObject("author").str("date"),
            )
        }
        return out
    }

    private fun parseWorkflowRuns(body: String): List<GitHubWorkflowRun> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("workflow_runs") ?: return emptyList()
        val out = ArrayList<GitHubWorkflowRun>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += GitHubWorkflowRun(
                id = o.optLong("id"),
                name = o.str("name") ?: "Workflow",
                displayTitle = o.str("display_title") ?: o.str("name") ?: "Workflow",
                status = o.str("status") ?: "",
                conclusion = o.str("conclusion"),
                htmlUrl = o.str("html_url") ?: "",
                headBranch = o.str("head_branch"),
                updatedAt = o.str("updated_at") ?: o.str("created_at"),
            )
        }
        return out
    }

    private fun parseRelease(body: String): GitHubRelease? {
        val o = JSONObject(body)
        val tag = o.str("tag_name") ?: return null
        return GitHubRelease(
            tagName = tag,
            name = o.str("name"),
            htmlUrl = o.str("html_url") ?: return null,
            publishedAt = o.str("published_at"),
            prerelease = o.optBoolean("prerelease", false),
        )
    }

    private fun parseNotifications(body: String): List<GitHubNotification> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubNotification>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val subject = o.optJSONObject("subject")
            val repo = o.optJSONObject("repository")
            val repoName = repo.str("full_name") ?: continue
            val type = subject.str("type") ?: "Notice"
            val apiUrl = subject.str("url")
            val id = o.str("id") ?: continue
            out += GitHubNotification(
                id = id,
                unread = o.optBoolean("unread", false),
                reason = o.str("reason") ?: "",
                title = subject.str("title") ?: type,
                type = type,
                repoFullName = repoName,
                updatedAt = o.str("updated_at") ?: "",
                htmlUrl = notificationHtmlUrl(apiUrl, type, repoName),
            )
        }
        return out
    }

    private fun notificationHtmlUrl(apiUrl: String?, type: String, repoFullName: String): String? {
        if (apiUrl.isNullOrBlank()) return "https://github.com/$repoFullName"
        val path = apiUrl.removePrefix("https://api.github.com/repos/")
        return when (type) {
            "PullRequest" -> {
                val n = path.substringAfter("/pulls/").substringBefore('/')
                "https://github.com/$repoFullName/pull/$n".takeIf { n.isNotBlank() && n != path }
            }
            "Issue" -> {
                val n = path.substringAfter("/issues/").substringBefore('/')
                "https://github.com/$repoFullName/issues/$n".takeIf { n.isNotBlank() && n != path }
            }
            "Commit" -> {
                val sha = path.substringAfter("/commits/")
                "https://github.com/$repoFullName/commit/$sha".takeIf { sha.isNotBlank() && sha != path }
            }
            "Release" -> "https://github.com/$repoFullName/releases"
            else -> "https://github.com/$repoFullName"
        }
    }

    private fun parseEvents(body: String): List<GitHubActivity> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubActivity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseEvent(o)?.let { out += it }
        }
        return out
    }

    private fun parseEvent(o: JSONObject): GitHubActivity? {
        val type = o.str("type") ?: return null
        val repoName = o.optJSONObject("repo").str("name") ?: return null
        val actor = o.optJSONObject("actor")
        val payload = o.optJSONObject("payload")
        val createdAt = o.str("created_at") ?: ""
        val id = o.optLong("id").takeIf { it > 0 }?.toString() ?: "$type-$repoName-$createdAt"
        val repoUrl = "https://github.com/$repoName"

        val title: String
        val url: String?
        when (type) {
            "PushEvent" -> {
                val commits = payload?.optJSONArray("commits")
                val n = commits?.length() ?: 0
                val first = commits?.optJSONObject(0).str("message")
                    ?.lineSequence()?.firstOrNull()?.trim()
                val ref = payload.str("ref")?.removePrefix("refs/heads/")
                val head = payload.str("head")
                title = buildString {
                    append("$n commit")
                    if (n != 1) append("s")
                    if (!ref.isNullOrBlank()) append(" → $ref")
                    if (!first.isNullOrBlank()) append(" · $first")
                }
                url = if (!head.isNullOrBlank()) {
                    "https://github.com/$repoName/commit/$head"
                } else {
                    repoUrl
                }
            }
            "PullRequestEvent" -> {
                val pr = payload?.optJSONObject("pull_request")
                val action = payload.str("action") ?: "updated"
                val number = pr?.optInt("number") ?: 0
                title = "$action PR #$number ${pr.str("title").orEmpty()}".trim()
                url = pr.str("html_url")
            }
            "IssuesEvent" -> {
                val issue = payload?.optJSONObject("issue")
                val action = payload.str("action") ?: "updated"
                val number = issue?.optInt("number") ?: 0
                title = "$action issue #$number ${issue.str("title").orEmpty()}".trim()
                url = issue.str("html_url")
            }
            "IssueCommentEvent", "PullRequestReviewCommentEvent" -> {
                val issue = payload?.optJSONObject("issue") ?: payload?.optJSONObject("pull_request")
                val number = issue?.optInt("number") ?: 0
                title = "commented on #$number ${issue.str("title").orEmpty()}".trim()
                url = payload?.optJSONObject("comment").str("html_url") ?: issue.str("html_url")
            }
            "PullRequestReviewEvent" -> {
                val pr = payload?.optJSONObject("pull_request")
                val state = payload?.optJSONObject("review").str("state") ?: "reviewed"
                title = "$state PR #${pr?.optInt("number") ?: 0}"
                url = payload?.optJSONObject("review").str("html_url") ?: pr.str("html_url")
            }
            "CreateEvent" -> {
                val refType = payload.str("ref_type") ?: "ref"
                val ref = payload.str("ref")
                title = "created $refType${if (ref.isNullOrBlank()) "" else " $ref"}"
                url = repoUrl
            }
            "ReleaseEvent" -> {
                val rel = payload?.optJSONObject("release")
                title = "released ${rel.str("tag_name") ?: rel.str("name") ?: "release"}"
                url = rel.str("html_url")
            }
            "WatchEvent" -> {
                title = "starred $repoName"
                url = repoUrl
            }
            "ForkEvent" -> {
                title = "forked $repoName"
                url = payload?.optJSONObject("forkee").str("html_url")
            }
            "DeleteEvent" -> {
                val refType = payload.str("ref_type") ?: "ref"
                title = "deleted $refType ${payload.str("ref").orEmpty()}".trim()
                url = repoUrl
            }
            else -> {
                title = type.removeSuffix("Event")
                url = repoUrl
            }
        }

        return GitHubActivity(
            id = id,
            type = type,
            repoFullName = repoName,
            createdAt = createdAt,
            title = title,
            htmlUrl = url,
            actorLogin = actor.str("login") ?: "",
            actorAvatarUrl = actor.str("avatar_url"),
        )
    }

    private fun parseLabels(arr: JSONArray?): List<GitHubLabel> {
        if (arr == null) return emptyList()
        val out = ArrayList<GitHubLabel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("name") ?: continue
            out += GitHubLabel(name = name, color = o.optString("color", "8B949E"))
        }
        return out
    }

    private fun repoFullName(o: JSONObject): String {
        o.optJSONObject("repository")?.str("full_name")?.let { return it }
        o.str("repository_url")
            ?.removePrefix("https://api.github.com/repos/")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        o.str("html_url")?.let { url ->
            val parts = url.removePrefix("https://github.com/").split('/')
            if (parts.size >= 2) return "${parts[0]}/${parts[1]}"
        }
        return ""
    }

    private fun persistDiskCache(snapshot: GitHubSnapshot, userKey: String) {
        runCatching {
            prefs.edit {
                putString(KEY_SNAPSHOT, json.encodeToString(GitHubSnapshot.serializer(), snapshot))
                putLong(KEY_FETCH_TIME, lastFetchTime)
                putString(KEY_CACHE_USER, userKey)
                putInt(KEY_CACHE_VERSION, CACHE_VERSION)
            }
        }.onFailure { Log.w(TAG, "Failed to persist GitHub cache", it) }
    }

    private fun restoreDiskCache() {
        if (prefs.getInt(KEY_CACHE_VERSION, 0) != CACHE_VERSION) return
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return
        runCatching {
            cached = json.decodeFromString(GitHubSnapshot.serializer(), raw)
            lastFetchTime = prefs.getLong(KEY_FETCH_TIME, 0L)
            cachedUserKey = prefs.getString(KEY_CACHE_USER, "") ?: ""
        }.onFailure {
            Log.w(TAG, "Discarding GitHub disk cache", it)
            cached = null
            lastFetchTime = 0L
        }
    }
}

private fun JSONObject?.str(key: String): String? {
    if (this == null) return null
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}
