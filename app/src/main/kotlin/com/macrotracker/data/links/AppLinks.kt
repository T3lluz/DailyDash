package com.macrotracker.data.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.core.content.edit

/**
 * The services the app links out to, each of which may have more than one app on the phone:
 * Gmail, Outlook or a browser for a mail; Google Calendar or another calendar for an event;
 * YouTube, NewPipe or a browser for a video. The first link of a kind asks which app to use
 * ([OpenWithActivity]) and the pick is kept, so every later link of that kind, from the app
 * or a widget, opens straight in it. Settings → Connections → Open links changes it.
 */
enum class LinkKind(val key: String, val label: String) {
    MAIL("mail", "Mail"),
    CALENDAR("calendar", "Calendar"),
    VIDEO("video", "YouTube"),
    STREAM("stream", "Twitch"),
    CODE("code", "GitHub"),
    ;

    companion object {
        fun of(key: String?): LinkKind? = entries.firstOrNull { it.key == key }

        /** The kind a link belongs to, from its scheme and host; null for anything else (left to Android). */
        fun forUrl(url: String): LinkKind? {
            val m = URL_PARTS.find(url.trim()) ?: return null
            val scheme = m.groupValues[1].lowercase()
            val host = m.groupValues[2].substringAfterLast('@').substringBefore(':').lowercase()
            val path = m.groupValues[3]
            return when (scheme) {
                "mailto" -> MAIL
                "content" -> if (host == "com.android.calendar") CALENDAR else null
                "https", "http" -> when (host.removePrefix("www.").removePrefix("m.")) {
                    "mail.google.com", "inbox.google.com" -> MAIL
                    "calendar.google.com" -> CALENDAR
                    "google.com" -> if (path.startsWith("/calendar")) CALENDAR else null
                    "youtube.com", "youtu.be" -> VIDEO
                    "twitch.tv", "clips.twitch.tv" -> STREAM
                    "github.com", "gist.github.com" -> CODE
                    else -> null
                }
                else -> null
            }
        }

        /** Scheme, host (with any user and port) and path; no android.net.Uri, so tests can run it. */
        private val URL_PARTS = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):(?://([^/?#]*))?([^?#]*)""")
    }
}

/** An app that can open a kind of link. */
data class LinkApp(val packageName: String, val label: String, val isBrowser: Boolean)

object AppLinks {
    private const val PREFS = "app_links"

    /** The app picked for [kind], when it is still installed. */
    fun chosen(context: Context, kind: LinkKind): String? {
        val pkg = prefs(context).getString(kind.key, null) ?: return null
        if (!installed(context, pkg)) {
            forget(context, kind)
            return null
        }
        return pkg
    }

    fun choose(context: Context, kind: LinkKind, packageName: String) {
        prefs(context).edit { putString(kind.key, packageName) }
    }

    fun forget(context: Context, kind: LinkKind) {
        prefs(context).edit { remove(kind.key) }
    }

    /**
     * Opens [url]: in the app picked for its kind, after asking once when more than one app
     * can open it, else in whatever Android picks. False for a blank link or nothing to open it.
     */
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val target = viewIntent(url)
        val kind = LinkKind.forUrl(url) ?: return start(context, target)
        return open(context, kind, target)
    }

    /** Opens [target] (a link, or an intent such as a calendar event's) the way [open] does. */
    fun open(context: Context, kind: LinkKind, target: Intent): Boolean {
        chosen(context, kind)?.let { pkg -> return openIn(context, pkg, target) }
        val apps = apps(context, kind, target)
        if (apps.size <= 1) {
            return apps.firstOrNull()?.let { openIn(context, it.packageName, target) } ?: start(context, target)
        }
        return start(context, OpenWithActivity.intent(context, kind, target))
    }

    /**
     * The intent a widget or notification fires for [url]: straight into the picked app,
     * through [OpenWithActivity] while the kind is still unpicked, else a plain view.
     */
    fun widgetIntent(context: Context, url: String): Intent {
        val kind = LinkKind.forUrl(url) ?: return viewIntent(url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return widgetIntent(context, kind, viewIntent(url))
    }

    fun widgetIntent(context: Context, kind: LinkKind, target: Intent): Intent {
        val pkg = chosen(context, kind)
        val intent = if (pkg != null) intentIn(context, pkg, target) ?: target else OpenWithActivity.intent(context, kind, target)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Opens [target] in [packageName]. An app that can't take this very link (Outlook for a
     * Gmail link) is opened on its own start screen, which is still the app that was picked.
     */
    fun openIn(context: Context, packageName: String, target: Intent): Boolean {
        val intent = intentIn(context, packageName, target) ?: return start(context, target)
        return start(context, intent) || start(context, target)
    }

    private fun intentIn(context: Context, packageName: String, target: Intent): Intent? {
        val pm = context.packageManager
        val direct = Intent(target).setPackage(packageName)
        if (pm.resolveActivity(direct, 0) != null) return direct
        // Some apps take their links only without the browsable category.
        val plain = Intent(direct).apply { removeCategory(Intent.CATEGORY_BROWSABLE) }
        if (pm.resolveActivity(plain, 0) != null) return plain
        return pm.getLaunchIntentForPackage(packageName)
    }

    /**
     * Every app that can open [kind]'s links, the service's own apps first and then
     * browsers. Mail and calendar apps count even when they can't open this link, since
     * opening them on their inbox or today is what picking them means.
     */
    fun apps(context: Context, kind: LinkKind, target: Intent? = null): List<LinkApp> {
        val pm = context.packageManager
        val browsers = query(pm, viewIntent("https://example.com")).toSet()
        val found = linkedSetOf<String>()
        target?.let { found += query(pm, Intent(it).setPackage(null)) }
        found += query(pm, viewIntent(kind.sampleUrl))
        when (kind) {
            LinkKind.MAIL -> {
                found += query(pm, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
                found += query(pm, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL))
            }
            LinkKind.CALENDAR -> {
                found += query(pm, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            }
            else -> Unit
        }
        found -= context.packageName
        return found
            .map { pkg -> LinkApp(pkg, label(pm, pkg), isBrowser = pkg in browsers) }
            .sortedWith(compareBy<LinkApp> { it.isBrowser }.thenBy { it.label.lowercase() })
    }

    private val LinkKind.sampleUrl: String
        get() = when (this) {
            LinkKind.MAIL -> "https://mail.google.com/mail/"
            LinkKind.CALENDAR -> "https://calendar.google.com/calendar/"
            LinkKind.VIDEO -> "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            LinkKind.STREAM -> "https://www.twitch.tv/directory"
            LinkKind.CODE -> "https://github.com/"
        }

    private fun query(pm: PackageManager, intent: Intent): List<String> {
        val infos: List<ResolveInfo> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
        }.getOrDefault(emptyList())
        return infos.mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    private fun label(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    /** A view of [url]; web links as a browser sends them, so only apps that take links answer. */
    fun viewIntent(url: String): Intent {
        val uri = Uri.parse(url.trim())
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "https" || scheme == "http") intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return intent
    }

    private fun start(context: Context, intent: Intent): Boolean {
        // The other app opens in its own task, also when the sheet (a task of its own) sends it.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
