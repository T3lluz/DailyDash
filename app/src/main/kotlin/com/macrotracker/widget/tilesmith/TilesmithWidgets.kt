package com.macrotracker.widget.tilesmith

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.DpSize
import androidx.glance.LocalGlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import com.macrotracker.MainActivity
import com.macrotracker.widget.DashWidget
import com.macrotracker.widget.DashWidgetReceiver
import com.macrotracker.widget.DashWidgetSpec
import com.macrotracker.widget.DashWidgets
import com.macrotracker.widget.WidgetInstances
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetDataBus
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.openUrlAction
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.rememberWidgetData
import com.macrotracker.widget.settings.WidgetSettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tilesmith as the shared widget plumbing sees it: one [DashWidgetSpec] whose copies each show
 * a design ([TilesmithStore]), placed through five receivers, one per [TileSize], so a pinned
 * widget lands at its design's size. Not listed with the ready-made widgets; the Widgets screen
 * gives it its own studio.
 */
object TilesmithWidgetSpec : DashWidgetSpec {
    const val KEY = "tilesmith"

    override val key: String = KEY
    override val title = "Tilesmith"
    override val description = "Widgets you describe and AI builds, in Material 3 Expressive: your data, your shapes, your colours."
    override val tagline = "Your own widgets, made with AI"
    override val sizeLabel = "2×1 – 4×3"
    override val accent = WK.Ai
    override val receiver: Class<out GlanceAppWidgetReceiver> = TilesmithSmallReceiver::class.java
    override val receivers: List<Class<out GlanceAppWidgetReceiver>> = listOf(
        TilesmithMiniReceiver::class.java,
        TilesmithSmallReceiver::class.java,
        TilesmithBarReceiver::class.java,
        TilesmithWideReceiver::class.java,
        TilesmithLargeReceiver::class.java,
    )
    override val listed: Boolean = false
    override val previewSize: DpSize = WidgetDims.cells(2, 2)
    override val showcase = TileSize.entries.map { it.cols to it.rows }

    fun receiverFor(size: TileSize): Class<out GlanceAppWidgetReceiver> = when (size) {
        TileSize.MINI -> TilesmithMiniReceiver::class.java
        TileSize.SMALL -> TilesmithSmallReceiver::class.java
        TileSize.BAR -> TilesmithBarReceiver::class.java
        TileSize.WIDE -> TilesmithWideReceiver::class.java
        TileSize.LARGE -> TilesmithLargeReceiver::class.java
    }

    fun sizeOfReceiver(className: String?): TileSize? = TileSize.entries.firstOrNull { receiverFor(it).name == className }

    /** The size [appWidgetId] was placed at, from its receiver. */
    fun placedSize(context: Context, appWidgetId: Int): TileSize? = runCatching {
        sizeOfReceiver(AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className)
    }.getOrNull()

    /** A design's size as Pixel Launcher hands it out in portrait. */
    fun previewSizeOf(size: TileSize): DpSize = WidgetDims.cells(size.cols, size.rows)

    @Composable
    override fun Content(context: Context) {
        val glanceId = LocalGlanceId.current
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val design = rememberWidgetData(TilesmithStore.BUS_KEY) {
            val id = TilesmithStore.designIdFor(context, appWidgetId, placedSize(context, appWidgetId))
            TilesmithStore.design(context, id)?.let { it to TilesmithStore.values(context, it.id) }
        }
        val pick = settingsAction(context, appWidgetId)
        if (design == null) {
            TileEmpty(pick)
        } else {
            key(design.first.id, design.first.updatedAt) {
                TileRoot(design.first, design.second, preview = false, actions = actionsFor(context, design.first), widgetTap = widgetTap(context, design.first))
            }
        }
    }

    override suspend fun prepare(context: Context) {
        withContext(Dispatchers.IO) { TilesmithStore.designs(context) }
    }

    /** The designs at least one placed copy shows. */
    fun shownDesigns(context: Context): List<TileDesign> {
        val placed = WidgetInstances.idsShowing(context, this).toSet()
        val ids = TilesmithStore.assignments(context).filterKeys { it in placed }.values.toSet()
        return ids.mapNotNull { TilesmithStore.design(context, it) }
    }

    override suspend fun refresh(context: Context, force: Boolean) {
        val now = System.currentTimeMillis()
        for (d in shownDesigns(context)) {
            val every = TileRules.effectiveRefreshMinutes(d) * 60_000L
            val due = force || now - TilesmithStore.refreshedAt(context, d.id) >= every - 20_000L
            if (!due) continue
            runCatching { TileResolver.refresh(context, d, force) }
                .onFailure { Log.w("Tilesmith", "refresh ${d.id}: ${it.message}") }
        }
        WidgetDataBus.bump(TilesmithStore.BUS_KEY)
    }

    /** Reads what is due for [design] now, as a pass would. */
    suspend fun refreshDesign(context: Context, design: TileDesign) {
        TileResolver.refresh(context, design, force = false)
        WidgetDataBus.bump(TilesmithStore.BUS_KEY)
    }

    /**
     * The soonest a shown design wants its next pass: its own cadence, or the moment a countdown
     * on it runs out (so "in 25 min" turns into the next event right on time).
     */
    override fun refreshEveryMs(context: Context): Long? {
        val now = System.currentTimeMillis()
        return shownDesigns(context).minOfOrNull { d ->
            val every = TileRules.effectiveRefreshMinutes(d) * 60_000L
            val since = now - TilesmithStore.refreshedAt(context, d.id)
            val next = (every - since).coerceAtLeast(30_000L)
            val countdown = TilesmithStore.values(context, d.id).values.mapNotNull { it.atMs?.let { at -> at - now + 5_000L } }.filter { it > 0 }.minOrNull()
            minOf(next, countdown ?: Long.MAX_VALUE)
        }
    }

    override suspend fun renderPreview(context: Context, size: DpSize, view: String?): RemoteViews {
        val design = TilesmithStore.design(context, view) ?: TileStarters.forSize(size)
        return render(context, design, size, TilesmithStore.values(context, design.id))
    }

    /** [design] as RemoteViews at [size]: the studio's preview, the pin dialog's and the picker's. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun render(context: Context, design: TileDesign, size: DpSize, values: Map<String, TileValue>): RemoteViews =
        GlanceRemoteViews().compose(context, size) {
            TileRoot(design, values, preview = true, actions = null, widgetTap = null)
        }.remoteViews

    /** The picker's preview for each size's receiver: a starter made for it. */
    suspend fun pickerPreview(context: Context, receiverClass: Class<*>): RemoteViews? {
        val size = sizeOfReceiver(receiverClass.name) ?: return null
        val design = TileStarters.forSize(previewSizeOf(size)).copy(size = size)
        return render(context, design, previewSizeOf(size), emptyMap())
    }

    // ── Taps ────────────────────────────────────────────────────────────────

    private fun settingsAction(context: Context, appWidgetId: Int): Action =
        actionStartActivity(
            Intent(context, WidgetSettingsActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    private fun open(context: Context, where: String): Action =
        openAppAction(context) { putExtra(MainActivity.EXTRA_OPEN, where) }

    fun actionFor(context: Context, design: TileDesign, tap: String?): Action? {
        val t = TileTap.normalize(tap) ?: return null
        return when {
            t == TileTap.APP -> openAppAction(context)
            t == TileTap.REFRESH -> refreshAction(KEY)
            t == TileTap.EDIT -> open(context, "tilesmith:${design.id}")
            t.startsWith("app:") -> open(context, t.removePrefix("app:"))
            t.startsWith("url:") -> openUrlAction(t.removePrefix("url:"))
            else -> null
        }
    }

    private fun actionsFor(context: Context, design: TileDesign) = TileActions { tap, value ->
        when {
            // A block waiting on a permission opens the studio, where it can be allowed.
            value?.state == TileValueState.NEEDS -> open(context, "tilesmith:${design.id}")
            tap != null -> actionFor(context, design, tap)
            else -> null
        }
    }

    /** The widget's own tap: what the design says, or the screen its first source belongs on. */
    private fun widgetTap(context: Context, design: TileDesign): Action {
        actionFor(context, design, design.tap)?.let { return it }
        val group = design.blocks.firstNotNullOfOrNull { TileSources.byKey(it.bind)?.group }
        return when (group) {
            SourceGroup.HEALTH, SourceGroup.FOOD -> open(context, "health")
            SourceGroup.SERVER -> open(context, "servers")
            SourceGroup.DASHBOARD -> open(context, "hermes")
            else -> openAppAction(context)
        }
    }
}

// ── Receivers: one per size ─────────────────────────────────────────────────

abstract class TilesmithReceiver : DashWidgetReceiver() {
    override val spec: DashWidgetSpec get() = TilesmithWidgetSpec

    override fun preWarm(context: Context) {
        TilesmithStore.designs(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        TilesmithStore.unassign(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }
}

class TilesmithMiniReceiver : TilesmithReceiver()
class TilesmithSmallReceiver : TilesmithReceiver()
class TilesmithBarReceiver : TilesmithReceiver()
class TilesmithWideReceiver : TilesmithReceiver()
class TilesmithLargeReceiver : TilesmithReceiver()

// ── Pinning ─────────────────────────────────────────────────────────────────

/**
 * Puts a design on the home screen: one tap here, then Android's own confirm. The launcher
 * places a copy from the design's size receiver; [TilesmithPinReceiver] (the request's
 * callback) tells it which design it is, and [TilesmithStore.markPendingPin] covers a first
 * render that comes before the callback.
 */
object TilesmithPin {
    const val EXTRA_DESIGN = "tilesmith_design"

    fun supported(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

    suspend fun request(context: Context, design: TileDesign): Boolean {
        val app = context.applicationContext
        val saved = TilesmithStore.save(app, design)
        TilesmithStore.markPendingPin(app, saved)
        val manager = AppWidgetManager.getInstance(app)
        val extras = Bundle()
        withContext(Dispatchers.Default) {
            runCatching {
                val preview = TilesmithWidgetSpec.render(app, saved, TilesmithWidgetSpec.previewSizeOf(saved.size), TilesmithStore.values(app, saved.id))
                extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
            }
        }
        val callback = PendingIntent.getBroadcast(
            app,
            saved.id.hashCode(),
            Intent(app, TilesmithPinReceiver::class.java).putExtra(EXTRA_DESIGN, saved.id),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
        return withContext(Dispatchers.Main) {
            runCatching {
                manager.requestPinAppWidget(ComponentName(app, TilesmithWidgetSpec.receiverFor(saved.size)), extras, callback)
            }.getOrDefault(false)
        }
    }

    /** How many placed copies show [designId]. */
    fun placedCount(context: Context, designId: String): Int {
        val placed = WidgetInstances.idsShowing(context, TilesmithWidgetSpec).toSet()
        return TilesmithStore.assignments(context).count { (id, d) -> d == designId && id in placed }
    }

    /** Redraws every copy showing [designId] (after an edit in the studio). */
    suspend fun redraw(context: Context) {
        DashWidgets.render(context.applicationContext, TilesmithWidgetSpec)
    }
}

/** The pin request's callback: the new copy's id, and which design it shows. */
class TilesmithPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val design = intent.getStringExtra(TilesmithPin.EXTRA_DESIGN) ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val app = context.applicationContext
        TilesmithStore.assign(app, id, design)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TilesmithStore.design(app, design)?.let { runCatching { TileResolver.refresh(app, it, force = false) } }
                WidgetDataBus.bump(TilesmithStore.BUS_KEY)
                runCatching { DashWidget().update(app, GlanceAppWidgetManager(app).getGlanceIdBy(id)) }
                WidgetRefreshWorker.enqueuePeriodicRefresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
