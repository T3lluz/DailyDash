package com.macrotracker.widget.github

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import com.macrotracker.data.github.GitHubNeedsAuthException
import com.macrotracker.data.github.GitHubSnapshot
import com.macrotracker.data.github.openIssueCount
import com.macrotracker.data.github.openPrCount
import com.macrotracker.data.github.parseGitHubInstant
import com.macrotracker.data.github.statusKey
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.time.LocalDate

/**
 * The GitHub widget's cache: memory → `daily_dash_widget_github` SharedPrefs → the app's
 * own GitHub disk cache (so a freshly placed widget has data straight away).
 *
 * Renders only ever call [load] and [avatar]; [refresh] is the one place that touches
 * the network, through the app's [com.macrotracker.data.github.GitHubRepository] (its
 * 5-minute cache also protects the API budget).
 */
internal object GitHubWidgetStore {
    private const val TAG = "GitHubWidget"
    private const val PREFS = "daily_dash_widget_github"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_AVATAR_AT = "avatar_at"
    private const val AVATAR_FILE = "widget_github_avatar.png"
    private const val AVATAR_PX = 96

    /** Just under the shared worker's 15 minutes, so every pass fetches. */
    private const val TTL_MS = 12 * 60 * 1000L

    /** The repository's own memory cache; a fetch older than this that didn't move failed. */
    private const val REPO_CACHE_MS = 5 * 60 * 1000L
    private const val AVATAR_TTL_MS = 24 * 60 * 60 * 1000L
    private const val AI_MIN_INTERVAL_MS = 60 * 60 * 1000L
    private const val AI_MAX_AGE_MS = 12 * 60 * 60 * 1000L

    private val state = MutableStateFlow<GitHubWidgetSnapshot?>(null)

    /** Every save lands here, so a widget session that is still running redraws with it. */
    val updates: StateFlow<GitHubWidgetSnapshot?> = state.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Fast and offline: memory, then prefs, then the app's GitHub cache. Null before
     * anything exists. Follows the app's sign-in state straight away rather than at the
     * next refresh.
     */
    fun load(context: Context): GitHubWidgetSnapshot? {
        val app = context.applicationContext
        val snapshot = stored(app) ?: return null
        val token = hasToken(app)
        if (snapshot.connected && !token) return snapshot.copy(connected = false)
        if (!snapshot.connected && token) {
            seed(app)?.takeIf { it.connected && it.hasData }?.let {
                state.value = it
                return it
            }
        }
        return snapshot
    }

    private fun stored(app: Context): GitHubWidgetSnapshot? =
        state.value
            ?: GhCodec.decode(prefs(app).getString(KEY_SNAPSHOT, null))?.also { state.value = it }
            ?: seed(app)?.also { state.value = it }

    private fun hasToken(app: Context): Boolean =
        runCatching { app.widgetEntryPoint().gitHubRepository().hasToken() }.getOrDefault(true)

    private fun seed(app: Context): GitHubWidgetSnapshot? = runCatching {
        val repo = app.widgetEntryPoint().gitHubRepository()
        if (!repo.hasToken()) return@runCatching GitHubWidgetSnapshot(connected = false)
        // attemptAt = 0: the next refresh pass fetches rather than trusting this copy.
        repo.getCachedDashboard()?.toWidget(fetchedAt = repo.lastFetchTimeMs, attemptAt = 0L)
    }.getOrNull()

    private fun save(app: Context, snapshot: GitHubWidgetSnapshot) {
        state.value = snapshot
        prefs(app).edit().putString(KEY_SNAPSHOT, GhCodec.encode(snapshot)).apply()
    }

    /** The cached avatar (≤ 96 px, already circular) for [url], or null to draw the GitHub mark. */
    fun avatar(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val app = context.applicationContext
        if (prefs(app).getString(KEY_AVATAR_URL, null) != url) return null
        val file = File(app.cacheDir, AVATAR_FILE)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    /** Never throws. Quick when nothing is due. */
    suspend fun refresh(context: Context, force: Boolean) {
        val app = context.applicationContext
        runCatching { withContext(Dispatchers.IO) { refreshNow(app, force) } }
            .onFailure { Log.w(TAG, "refresh failed: ${it.message}") }
    }

    private suspend fun refreshNow(app: Context, force: Boolean) {
        val repo = app.widgetEntryPoint().gitHubRepository()
        val prev = stored(app)
        val now = System.currentTimeMillis()

        if (!repo.hasToken()) {
            disconnect(app, prev, now)
            return
        }
        if (!force && prev != null && prev.connected && prev.hasData && now - prev.attemptAt < TTL_MS) return

        val before = repo.lastFetchTimeMs
        val result = repo.getDashboard(forceRefresh = force)
        val snap = result.getOrElse { e ->
            if (e is GitHubNeedsAuthException) {
                disconnect(app, prev, now)
            } else {
                val base = prev?.takeIf { it.connected } ?: GitHubWidgetSnapshot()
                save(app, base.copy(attemptAt = now, error = GhFormat.errorLabel(e.message)))
            }
            return
        }

        // The repository hands back its last copy when a fetch fails; its fetch time then stays put.
        val after = repo.lastFetchTimeMs
        val fellBack = after == before && (force || now - before >= REPO_CACHE_MS)
        val fetched = snap.toWidget(fetchedAt = if (after > 0L) after else now, attemptAt = now).let {
            if (fellBack) it.copy(error = if ((snap.rateLimitRemaining ?: 1) <= 0) "rate-limited" else "offline") else it
        }

        runCatching { ensureAvatar(app, fetched.avatarUrl) }
            .onFailure { Log.w(TAG, "avatar: ${it.message}") }

        // Regenerated only when what needs you changed (and an hour passed) or it is 12 h old.
        val brief = runCatching {
            WidgetAi.brief(
                context = app,
                key = GitHubWidgetSpec.KEY,
                fingerprint = GhAi.fingerprint(fetched),
                minIntervalMs = AI_MIN_INTERVAL_MS,
                maxAgeMs = AI_MAX_AGE_MS,
                maxChars = 130,
            ) { GhAi.prompt(fetched, now, LocalDate.now()) }
        }.getOrNull()

        save(app, fetched.copy(brief = brief))
    }

    private fun disconnect(app: Context, prev: GitHubWidgetSnapshot?, now: Long) {
        if (prev != null && !prev.connected) return
        save(app, GitHubWidgetSnapshot(connected = false, attemptAt = now))
        WidgetAi.clear(app, GitHubWidgetSpec.KEY)
    }

    /** Downloads the avatar at most daily (or when it changes) as a small circular PNG in the cache dir. */
    private fun ensureAvatar(app: Context, url: String?) {
        if (url.isNullOrBlank()) return
        val p = prefs(app)
        val file = File(app.cacheDir, AVATAR_FILE)
        val now = System.currentTimeMillis()
        val fresh = file.exists() &&
            p.getString(KEY_AVATAR_URL, null) == url &&
            now - p.getLong(KEY_AVATAR_AT, 0L) < AVATAR_TTL_MS
        if (fresh) return

        val sized = if ('?' in url) "$url&s=$AVATAR_PX" else "$url?s=$AVATAR_PX"
        val bytes = app.widgetEntryPoint().okHttpClient()
            .newCall(Request.Builder().url(sized).build())
            .execute()
            .use { response -> if (response.isSuccessful) response.body?.bytes() else null }
            ?: return
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val round = circle(source, AVATAR_PX)
        val tmp = File(app.cacheDir, "$AVATAR_FILE.tmp")
        tmp.outputStream().use { round.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        p.edit().putString(KEY_AVATAR_URL, url).putLong(KEY_AVATAR_AT, now).apply()
    }

    private fun circle(source: Bitmap, px: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (side == px) square else Bitmap.createScaledBitmap(square, px, px, true)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(px / 2f, px / 2f, px / 2f, paint)
        return out
    }
}

/** Rows kept per list; more than any size shows, so the lists still scroll. */
private const val LIST_CAP = 30

private fun ms(iso: String?): Long = parseGitHubInstant(iso)?.toEpochMilli() ?: 0L

/** The app's dashboard snapshot, cut down to what the widget draws. */
internal fun GitHubSnapshot.toWidget(fetchedAt: Long, attemptAt: Long): GitHubWidgetSnapshot {
    val login = user.login
    return GitHubWidgetSnapshot(
        connected = true,
        fetchedAt = fetchedAt,
        attemptAt = attemptAt,
        login = login,
        name = user.name,
        profileUrl = user.htmlUrl,
        avatarUrl = user.avatarUrl,
        openPrs = openPrCount(),
        reviewCount = reviewRequestedCount.takeIf { it > 0 } ?: pullRequests.count { it.reviewRequested },
        issueCount = openIssueCount(),
        unreadCount = unreadNotificationCount,
        inboxNeedsReconnect = notificationsNeedReconnect,
        rateRemaining = rateLimitRemaining,
        rateLimit = rateLimitLimit,
        inbox = notifications.take(LIST_CAP).map {
            GhInboxItem(
                id = it.id,
                title = it.title,
                repo = it.repoFullName,
                reason = it.reason,
                type = it.type,
                unread = it.unread,
                at = ms(it.updatedAt),
                url = it.htmlUrl,
            )
        },
        prs = pullRequests.take(LIST_CAP).map {
            GhPrItem(
                repo = it.repoFullName,
                number = it.number,
                title = it.title,
                status = it.statusKey(),
                author = it.userLogin,
                mine = it.authoredByMe,
                review = it.reviewRequested,
                at = ms(it.updatedAt),
                url = it.htmlUrl,
            )
        },
        issues = issues.take(LIST_CAP).map {
            GhIssueItem(
                repo = it.repoFullName,
                number = it.number,
                title = it.title,
                assigned = it.assignedToMe,
                comments = it.comments,
                label = it.labels.firstOrNull()?.name,
                at = ms(it.updatedAt),
                url = it.htmlUrl,
            )
        },
        events = activity.take(LIST_CAP).map {
            GhEventItem(
                id = it.id,
                type = it.type,
                repo = it.repoFullName,
                title = it.title,
                at = ms(it.createdAt),
                url = it.htmlUrl,
            )
        },
        contrib = contributions?.let { c ->
            GhContribMath.fromDays(c.days.map { it.date }, c.days.map { it.count }, c.days.map { it.level }, c.total)
        },
    )
}
