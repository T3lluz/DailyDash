package com.macrotracker.widget.tilesmith

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Where Tilesmith keeps its designs, which placed widget shows which design, what each
 * design's sources last read, and what you have allowed it to run or read.
 *
 * Everything is in SharedPreferences (`tilesmith`, `tilesmith_values`) and mirrored in
 * memory, so a widget render reads it without touching the disk twice.
 */
object TilesmithStore {
    private const val PREFS = "tilesmith"
    private const val VALUES = "tilesmith_values"
    private const val KEY_DESIGNS = "designs"
    private const val KEY_PENDING = "pending_pin"
    private const val KEY_SEEN = "seen"

    /** Bumped whenever a design or its values change, so live Glance sessions redraw. */
    const val BUS_KEY = "tilesmith"

    /** A pin dialog the launcher may still be showing: a copy that lands within this adopts its design. */
    private const val PENDING_PIN_MS = 3 * 60_000L

    private val _designs = MutableStateFlow<List<TileDesign>?>(null)

    @Volatile private var placedVersion = 0
    private val _placed = MutableStateFlow(0)

    /** Ticks whenever a placed copy is assigned or removed: the studio's "on your home screen". */
    val placedChanges: StateFlow<Int> = _placed

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun valuesPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(VALUES, Context.MODE_PRIVATE)

    // ── Designs ─────────────────────────────────────────────────────────────

    fun designs(context: Context): List<TileDesign> =
        _designs.value ?: synchronized(this) {
            _designs.value ?: TileCodec.parseList(prefs(context).getString(KEY_DESIGNS, null)).also { _designs.value = it }
        }

    /** The designs as they change, newest first. */
    fun designsFlow(context: Context): StateFlow<List<TileDesign>?> {
        designs(context)
        return _designs
    }

    fun design(context: Context, id: String?): TileDesign? = id?.let { k -> designs(context).firstOrNull { it.id == k } }

    /** Adds or replaces [design] (by id), newest first, and redraws every copy showing it. */
    fun save(context: Context, design: TileDesign): TileDesign {
        val clean = TileRules.sanitize(design.copy(updatedAt = System.currentTimeMillis()))
        synchronized(this) {
            val list = listOf(clean) + designs(context).filter { it.id != clean.id }
            prefs(context).edit().putString(KEY_DESIGNS, TileCodec.encodeList(list)).apply()
            _designs.value = list
        }
        return clean
    }

    fun delete(context: Context, id: String) {
        synchronized(this) {
            val list = designs(context).filter { it.id != id }
            prefs(context).edit().putString(KEY_DESIGNS, TileCodec.encodeList(list)).apply()
            _designs.value = list
        }
        valuesPrefs(context).edit().remove(valuesKey(id)).remove(stampKey(id)).apply()
    }

    // ── Placed copies ───────────────────────────────────────────────────────

    private fun instanceKey(appWidgetId: Int) = "inst_$appWidgetId"

    /**
     * The design [appWidgetId] shows. A copy with none yet that landed while a pin dialog for
     * its size was up takes that design: the launcher's success callback can arrive after the
     * first render, and the copy should never flash its empty state.
     */
    fun designIdFor(context: Context, appWidgetId: Int, size: TileSize?): String? {
        val p = prefs(context)
        p.getString(instanceKey(appWidgetId), null)?.let { return it }
        val pending = runCatching { JSONObject(p.getString(KEY_PENDING, null) ?: return null) }.getOrNull() ?: return null
        val fresh = System.currentTimeMillis() - pending.optLong("at") < PENDING_PIN_MS
        if (!fresh || (size != null && pending.optString("size") != size.id)) return null
        val id = pending.optString("design").ifBlank { return null }
        assign(context, appWidgetId, id)
        return id
    }

    fun assign(context: Context, appWidgetId: Int, designId: String) {
        val p = prefs(context)
        val pending = runCatching { JSONObject(p.getString(KEY_PENDING, null) ?: "{}") }.getOrNull()
        val edit = p.edit().putString(instanceKey(appWidgetId), designId)
        if (pending?.optString("design") == designId) edit.remove(KEY_PENDING)
        edit.apply()
        _placed.value = ++placedVersion
    }

    fun unassign(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(instanceKey(it)) }
        edit.apply()
        _placed.value = ++placedVersion
    }

    /** Marks a pin request in flight for [design]; see [designIdFor]. */
    fun markPendingPin(context: Context, design: TileDesign) {
        prefs(context).edit()
            .putString(KEY_PENDING, JSONObject().put("design", design.id).put("size", design.size.id).put("at", System.currentTimeMillis()).toString())
            .apply()
    }

    /** Every app-widget id that has a design, with it. */
    fun assignments(context: Context): Map<Int, String> =
        prefs(context).all.mapNotNull { (k, v) ->
            if (!k.startsWith("inst_") || v !is String) null else k.removePrefix("inst_").toIntOrNull()?.let { it to v }
        }.toMap()

    // ── Values ──────────────────────────────────────────────────────────────

    private fun valuesKey(designId: String) = "v_$designId"
    private fun stampKey(designId: String) = "at_$designId"

    fun values(context: Context, designId: String): Map<String, TileValue> =
        TileValueCodec.parseMap(valuesPrefs(context).getString(valuesKey(designId), null))

    fun saveValues(context: Context, designId: String, values: Map<String, TileValue>) {
        valuesPrefs(context).edit()
            .putString(valuesKey(designId), TileValueCodec.encodeMap(values))
            .putLong(stampKey(designId), System.currentTimeMillis())
            .apply()
    }

    /** When [designId]'s sources were last read. */
    fun refreshedAt(context: Context, designId: String): Long = valuesPrefs(context).getLong(stampKey(designId), 0L)

    // ── Approvals ───────────────────────────────────────────────────────────

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private fun commandKey(server: String?, cmd: String) = "ok_cmd_" + hash((server ?: "").trim() + "\u0000" + cmd.trim())

    /** Whether you have allowed exactly this command on exactly this server. */
    fun commandApproved(context: Context, server: String?, cmd: String): Boolean =
        prefs(context).getBoolean(commandKey(server, cmd), false)

    fun approveCommand(context: Context, server: String?, cmd: String) {
        prefs(context).edit().putBoolean(commandKey(server, cmd), true).apply()
    }

    fun siteHost(url: String): String? = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    fun siteApproved(context: Context, url: String): Boolean =
        siteHost(url)?.let { prefs(context).getBoolean("ok_site_$it", false) } ?: false

    fun approveSite(context: Context, url: String) {
        siteHost(url)?.let { prefs(context).edit().putBoolean("ok_site_$it", true).apply() }
    }

    // ── The "New" badge ─────────────────────────────────────────────────────

    /** Whether Tilesmith has been opened, so Settings stops calling it new. */
    fun seen(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_SEEN, true).apply()
    }
}
