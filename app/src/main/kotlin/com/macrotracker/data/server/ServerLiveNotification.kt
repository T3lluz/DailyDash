package com.macrotracker.data.server

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.macrotracker.R
import com.macrotracker.ui.components.activityLine
import com.macrotracker.ui.components.serverFacts
import kotlin.math.roundToInt

/**
 * The one thing the live notification leads with, and how loud it should be about it.
 * Offline beats a critical advisory, which beats a warning, which beats something
 * happening on the server (a film playing, a download landing), which beats nothing.
 */
data class LiveHeadline(val text: String, val tone: LiveTone)

enum class LiveTone { INFO, WARN, CRIT }

fun liveHeadline(runtime: ServerRuntime, link: DashboardLink?): LiveHeadline? {
    when (val connection = runtime.connection) {
        is ServerConnectionState.Offline -> return LiveHeadline("Offline · ${connection.reason.message}", LiveTone.CRIT)
        is ServerConnectionState.Connecting -> return LiveHeadline("Connecting…", LiveTone.INFO)
        else -> Unit
    }
    if (runtime.snapshot == null) return LiveHeadline("Waiting for the first sample…", LiveTone.INFO)
    val serious = runtime.advisories.filter { it.severity != AdvisorySeverity.INFO }
    serious.firstOrNull()?.let { worst ->
        val more = if (serious.size > 1) " · ${serious.size - 1} more" else ""
        return LiveHeadline(
            worst.title + more,
            if (worst.severity == AdvisorySeverity.CRITICAL) LiveTone.CRIT else LiveTone.WARN,
        )
    }
    activityLine(link)?.let { (icon, text) ->
        val mark = if (icon == com.macrotracker.ui.theme.AppIcons.Play) "▶" else "↓"
        return LiveHeadline("$mark $text", LiveTone.INFO)
    }
    return null
}

/**
 * Builds the ongoing server notification.
 *
 * It follows the server rather than sitting still: the status-bar icon, the accent and
 * the header's summary change with how the machine is doing; the collapsed line leads
 * with whatever matters most right now; and expanded it shows the dials, ten minutes of
 * history, the network, every core, the facts line and the other servers. That panel
 * is behind More, because Android opens whichever notification sits on top of the shade.
 * Its actions are the useful next steps — ask Tech support about the problem, step to
 * the next server, stop.
 */
class ServerLiveNotification(private val context: Context, private val notifier: ServerNotifier) {

    fun build(
        runtime: ServerRuntime?,
        link: DashboardLink?,
        others: List<ServerRuntime>,
        serviceClass: Class<*>,
        /** The full panel when opened; otherwise opening it shows the compact row and the actions. */
        detailed: Boolean = false,
    ): Notification {
        val spec = ServerLiveGraphics.specFor(context)
        val collapsed = RemoteViews(context.packageName, R.layout.notification_server_live_collapsed)
        val expanded = RemoteViews(context.packageName, R.layout.notification_server_live_expanded)
        val headline = runtime?.let { liveHeadline(it, link) }
        val label = runtime?.profile?.label ?: context.getString(R.string.server_channel_live)

        // ── Collapsed: name and uptime, then the headline or the vitals ─────────
        val uptime = runtime?.snapshot?.uptimeSeconds?.let { "up ${formatUptime(it)}" }
        collapsed.setTextViewText(R.id.server_live_title, listOfNotNull(label, uptime).joinToString(" · "))
        collapsed.setTextViewText(
            R.id.server_live_summary,
            headline?.text ?: runtime?.let(::vitalsLine) ?: "Starting…",
        )
        headline?.let { tint(collapsed, R.id.server_live_summary, it.tone) }

        // ── Expanded ────────────────────────────────────────────────────────────
        expanded.setTextViewText(
            R.id.server_live_header,
            listOfNotNull(
                label,
                runtime?.hostProfile?.prettyName?.takeIf { it.isNotBlank() } ?: runtime?.profile?.displayTarget,
            ).joinToString(" · "),
        )
        if (headline != null) {
            expanded.setViewVisibility(R.id.server_live_headline, View.VISIBLE)
            expanded.setTextViewText(R.id.server_live_headline, headline.text)
            tint(expanded, R.id.server_live_headline, headline.tone)
        }
        expanded.setTextViewText(
            R.id.server_live_facts,
            runtime?.let { r ->
                // Compact: the notification has two lines for these, not a card.
                serverFacts(r, link, compact = true).joinToString(" · ") { it.text }
                    .ifBlank { vitalsLine(r) }
            } ?: "",
        )
        if (others.isNotEmpty()) {
            expanded.setViewVisibility(R.id.server_live_others, View.VISIBLE)
            expanded.setTextViewText(R.id.server_live_others, "Also " + others.joinToString(" · ", transform = ::otherLine))
        }

        if (runtime != null) {
            collapsed.setImageViewBitmap(R.id.server_live_gauges, ServerLiveGraphics.renderStrip(runtime, link, spec))
            // The panel is the heaviest bitmap here; it is only drawn when it will be shown.
            if (detailed) {
                expanded.setImageViewBitmap(R.id.server_live_panel, ServerLiveGraphics.renderPanel(runtime, link, spec))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                collapsed.setViewOutlinePreferredRadius(
                    R.id.server_live_gauges, ServerLiveGraphics.CORNER_DP * 0.75f, TypedValue.COMPLEX_UNIT_DIP,
                )
                expanded.setViewOutlinePreferredRadius(
                    R.id.server_live_panel, ServerLiveGraphics.CORNER_DP, TypedValue.COMPLEX_UNIT_DIP,
                )
            }
        } else {
            collapsed.setViewVisibility(R.id.server_live_gauges, View.GONE)
            expanded.setViewVisibility(R.id.server_live_panel, View.GONE)
        }

        val builder = NotificationCompat.Builder(context, ServerNotifier.CHANNEL_LIVE)
            .setSmallIcon(smallIcon(runtime, headline))
            .setColor(accent(headline))
            .setSubText(subText(runtime, link, others))
            .setCustomContentView(collapsed)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        // SystemUI always opens the notification at the top of the shade, and a foreground
        // service's channel cannot be turned down far enough to stop that. So the opened
        // view is the compact row plus the actions unless the full panel was asked for,
        // with More / Less to switch; the style builds that view from the content view.
        if (detailed) builder.setCustomBigContentView(expanded)
        builder
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Android 12+ holds a new foreground-service notification back for up to ten
            // seconds; a monitor that was just switched on should show at once.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(notifier.openServersIntent(runtime?.profile?.id))

        // The shade shows three actions at most. More / Less always; then Ask on the
        // compact view, and Next server on the panel, which is where the fleet is listed.
        if (runtime != null) {
            builder.addAction(
                0,
                context.getString(if (detailed) R.string.server_live_less else R.string.server_live_more),
                serviceIntent(serviceClass, ACTION_TOGGLE_DETAIL, 2),
            )
        }
        val canAsk = runtime?.snapshot != null || runtime?.connection is ServerConnectionState.Offline
        if (detailed && others.isNotEmpty()) {
            builder.addAction(0, context.getString(R.string.server_live_next), serviceIntent(serviceClass, ACTION_NEXT_SERVER, 1))
        } else if (canAsk) {
            val about = runtime.advisories.firstOrNull { it.severity != AdvisorySeverity.INFO }?.key
                ?: ServerNotifier.ASK_OVERVIEW
            builder.addAction(0, notifier.askLabel(), notifier.askIntent(runtime.profile.id, about))
        }
        builder.addAction(0, context.getString(R.string.server_live_stop), serviceIntent(serviceClass, ACTION_STOP, 0))
        return builder.build()
    }

    private fun serviceIntent(serviceClass: Class<*>, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, serviceClass).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Only a warning or a fault gets a colour; everything else keeps the shade's own text colour. */
    private fun tint(views: RemoteViews, id: Int, tone: LiveTone) {
        when (tone) {
            LiveTone.CRIT -> views.setTextColor(id, CRIT)
            LiveTone.WARN -> views.setTextColor(id, WARN)
            LiveTone.INFO -> Unit
        }
    }

    private fun accent(headline: LiveHeadline?): Int = when (headline?.tone) {
        LiveTone.CRIT -> CRIT
        LiveTone.WARN -> WARN
        else -> BRAND
    }

    /** The status-bar icon says whether anything is wrong without the shade being opened. */
    private fun smallIcon(runtime: ServerRuntime?, headline: LiveHeadline?): Int = when {
        runtime?.connection is ServerConnectionState.Offline -> R.drawable.ic_server_offline
        headline?.tone == LiveTone.CRIT || headline?.tone == LiveTone.WARN -> R.drawable.ic_server_gauge
        else -> R.drawable.ic_server_live
    }

    /** The header's summary: the state in two or three words, or the fleet when there is one. */
    private fun subText(runtime: ServerRuntime?, link: DashboardLink?, others: List<ServerRuntime>): String? {
        if (runtime == null) return null
        if (others.isNotEmpty()) {
            val all = others + runtime
            return "${all.count { it.isOnline }} of ${all.size} online"
        }
        if (!runtime.isOnline) return "offline"
        val serious = runtime.advisories.count { it.severity != AdvisorySeverity.INFO }
        return when {
            serious == 1 -> "1 to look at"
            serious > 1 -> "$serious to look at"
            link != null && link.services.isNotEmpty() -> "${link.servicesUp}/${link.services.size} services up"
            else -> "all good"
        }
    }

    private fun vitalsLine(runtime: ServerRuntime): String {
        val s = runtime.snapshot ?: return "Collecting…"
        return buildList {
            s.cpu?.let { add("CPU ${it.totalPercent.roundToInt()}%") }
            s.temperatures.firstOrNull { it.kind == SensorKind.CPU }?.let { add("${it.celsius.roundToInt()}°C") }
            s.memory?.let { add("RAM ${it.usedPercent.roundToInt()}%") }
            s.network?.let { add("↓ ${formatRate(it.rxBytesPerSec)} ↑ ${formatRate(it.txBytesPerSec)}") }
        }.joinToString(" · ").ifBlank { "Collecting…" }
    }

    private fun otherLine(runtime: ServerRuntime): String = runtime.profile.label + " " + when {
        runtime.connection is ServerConnectionState.Offline -> "offline"
        runtime.advisories.any { it.severity == AdvisorySeverity.CRITICAL } -> "⚠"
        else -> runtime.snapshot?.cpu?.totalPercent?.let { "${it.roundToInt()}%" } ?: "…"
    }

    companion object {
        const val ACTION_STOP = "com.macrotracker.server.STOP_LIVE"
        const val ACTION_NEXT_SERVER = "com.macrotracker.server.NEXT_SERVER"
        const val ACTION_TOGGLE_DETAIL = "com.macrotracker.server.TOGGLE_DETAIL"

        private const val CRIT = 0xFFE34671.toInt()
        private const val WARN = 0xFFE29A2F.toInt()
        private const val BRAND = 0xFF88C0D0.toInt()
    }
}
