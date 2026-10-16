package com.macrotracker.ui.screens.tilesmith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.widget.tilesmith.TileAsk
import com.macrotracker.widget.tilesmith.TileChatTurn
import com.macrotracker.widget.tilesmith.TileDesign
import com.macrotracker.widget.tilesmith.TileNeeds
import com.macrotracker.widget.tilesmith.TilePalette
import com.macrotracker.widget.tilesmith.TileResolver
import com.macrotracker.widget.tilesmith.TileRules
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TileSize
import com.macrotracker.widget.tilesmith.TileStarters
import com.macrotracker.widget.tilesmith.TileSurface
import com.macrotracker.widget.tilesmith.TileValue
import com.macrotracker.widget.tilesmith.BlockStyle
import com.macrotracker.widget.tilesmith.BlockType
import com.macrotracker.widget.tilesmith.TileBlock
import com.macrotracker.widget.tilesmith.TilesmithAi
import com.macrotracker.widget.tilesmith.TilesmithPin
import com.macrotracker.widget.tilesmith.TilesmithStore
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.widgetEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One line in the studio's conversation. */
data class StudioLine(val fromUser: Boolean, val text: String, val error: Boolean = false, val picture: Bitmap? = null)

/** A picture to build from (a widget you like, a screenshot, a sketch), ready to send. */
class StudioAttachment(val jpegBase64: String, val thumb: Bitmap)

data class StudioState(
    /** The design on the stage; null before the first draft lands. */
    val design: TileDesign? = null,
    /** What its sources read, for the preview. */
    val values: Map<String, TileValue> = emptyMap(),
    val lines: List<StudioLine> = emptyList(),
    /** The AI is working; [busyFor] is what it was asked. */
    val busy: Boolean = false,
    val busyFor: String? = null,
    /** What it is doing right now ("Thinking", "Adding a ring · Steps"), and since when. */
    val step: String? = null,
    val stepDetail: String? = null,
    val busySince: Long = 0L,
    /** The preview is reading the design's sources. */
    val reading: Boolean = false,
    val asks: List<TileAsk> = emptyList(),
    val aiReady: Boolean = false,
    /** The designs before the last changes, newest last, for Undo. */
    val undo: List<TileDesign> = emptyList(),
    /** One-tap next changes the AI offered with its last answer. */
    val suggestions: List<String> = emptyList(),
    /** A picture to send with the next message. */
    val attachment: StudioAttachment? = null,
    /** Placed copies showing this design. */
    val placed: Int = 0,
    /** A pin request is up: Android's dialog is showing. */
    val pinning: Boolean = false,
    /** Blocks the last size change left out. */
    val dropped: Int = 0,
)

/**
 * The Tilesmith studio: a design on the stage, the conversation that shapes it, and what it
 * still needs; with no design open, your widgets and the starters. Every change is saved
 * straight away (the widgets showing it redraw), so there is no Save step; Undo steps back
 * through the last [UNDO_DEPTH] changes.
 */
@HiltViewModel
class TilesmithViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentials: AiCredentialResolver,
) : ViewModel() {
    private val _state = MutableStateFlow(StudioState(aiReady = credentials.hasCredentials()))
    val state: StateFlow<StudioState> = _state.asStateFlow()

    /** Every saved design, newest first. */
    val designs: StateFlow<List<TileDesign>?> = TilesmithStore.designsFlow(context)

    private var aiJob: Job? = null
    private var opened: String? = null

    private companion object {
        const val UNDO_DEPTH = 20
    }

    init {
        TilesmithStore.markSeen(context)
        viewModelScope.launch {
            TilesmithStore.placedChanges.collect {
                val d = _state.value.design ?: return@collect
                val n = withContext(Dispatchers.IO) { TilesmithPin.placedCount(context, d.id) }
                _state.update { s -> s.copy(placed = n, pinning = if (n > s.placed) false else s.pinning) }
            }
        }
    }

    /**
     * Opens the studio on a saved design, a starter, or a request for the AI (the starter that
     * matches it goes up first, so the stage is never empty while the AI works).
     */
    fun open(designId: String?, prompt: String?, starterId: String?) {
        val key = listOf(designId, prompt, starterId).joinToString("|")
        if (opened == key) return
        opened = key
        _state.update { it.copy(aiReady = credentials.hasCredentials()) }
        val saved = TilesmithStore.design(context, designId)
        when {
            saved != null -> show(saved, lines = saved.chat.map { StudioLine(it.fromUser, it.text) }, undo = emptyList())
            starterId != null -> TileStarters.byId(starterId)?.let { s ->
                val d = TilesmithStore.save(context, TileStarters.instantiate(s))
                show(d, lines = listOf(StudioLine(false, "Here's ${s.title}. Tell me what to change, or add it as it is.")))
            }
            !prompt.isNullOrBlank() -> {
                TileStarters.match(prompt)?.let { show(TileStarters.instantiate(it).copy(name = it.title), lines = emptyList(), save = false) }
                send(prompt)
            }
        }
    }

    /** Opens another of your designs in place (from the studio's own list). */
    fun switchTo(designId: String) {
        if (_state.value.busy || _state.value.design?.id == designId) return
        val d = TilesmithStore.design(context, designId) ?: return
        opened = "$designId|null|null"
        _state.update { it.copy(dropped = 0, suggestions = emptyList(), asks = emptyList(), values = emptyMap()) }
        show(d, lines = d.chat.map { StudioLine(it.fromUser, it.text) }, undo = emptyList())
    }

    /** Back to the empty studio: your widgets, the starters and a blank composer. */
    fun newWidget() {
        if (_state.value.busy) return
        opened = "new|${System.currentTimeMillis()}"
        valuesJob?.cancel()
        _state.update { StudioState(aiReady = credentials.hasCredentials()) }
    }

    /** Starts from a starter, from the empty studio. */
    fun startFrom(starterId: String) {
        val s = TileStarters.byId(starterId) ?: return
        val d = TilesmithStore.save(context, TileStarters.instantiate(s))
        opened = "${d.id}|null|null"
        show(d, lines = listOf(StudioLine(false, "Here's ${s.title}. Tell me what to change, or add it as it is.")), undo = emptyList())
    }

    private fun show(d: TileDesign, lines: List<StudioLine>? = null, save: Boolean = true, undo: List<TileDesign> = _state.value.undo) {
        val design = if (save) TilesmithStore.save(context, d) else d
        _state.update { s -> s.copy(design = design, lines = lines ?: s.lines, undo = undo.takeLast(UNDO_DEPTH)) }
        refreshPlaced()
        loadValues(design)
        recheck()
        if (save) viewModelScope.launch(Dispatchers.IO) { TilesmithPin.redraw(context) }
    }

    private fun refreshPlaced() {
        val d = _state.value.design ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val n = TilesmithPin.placedCount(context, d.id)
            _state.update { it.copy(placed = n) }
        }
    }

    private var valuesJob: Job? = null

    /** Reads the design's sources for the preview (what's due only; the widget shares the cache). */
    fun loadValues(design: TileDesign? = _state.value.design, force: Boolean = false) {
        val d = design ?: return
        valuesJob?.cancel()
        valuesJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { if (it.design?.id == d.id) it.copy(values = TilesmithStore.values(context, d.id), reading = true) else it }
            val v = runCatching { TileResolver.refresh(context, d, force) }.getOrNull()
            _state.update { if (it.design?.id == d.id) it.copy(values = v ?: it.values, reading = false) else it }
            v ?: return@launch
            // Placed copies show what was just read, and keep to the design's (maybe new) pace.
            if (_state.value.placed > 0) {
                TilesmithPin.redraw(context)
                WidgetRefreshWorker.scheduleFastLane(context)
            }
        }
    }

    /** What the design still needs; called again after each grant. */
    fun recheck() {
        viewModelScope.launch(Dispatchers.IO) {
            val d = _state.value.design
            val asks = if (d != null) {
                runCatching { TileNeeds.check(context, d) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val ready = credentials.hasCredentials()
            _state.update { s ->
                if (d != null && s.design?.id != d.id) s
                else s.copy(asks = if (d != null) asks else s.asks, aiReady = ready)
            }
        }
    }

    /** A grant came back: check again and read again. */
    fun granted() {
        // Health Connect's granted set is cached for half a minute; a fresh grant must show now.
        runCatching { context.widgetEntryPoint().healthConnectRepository().clearMetricCache() }
        _state.update { it.copy(aiReady = credentials.hasCredentials()) }
        recheck()
        loadValues(force = true)
    }

    // ── The conversation ────────────────────────────────────────────────────

    /** Sends [text]: a first draft when there's no design yet (or only the matched starter), else a revision. */
    fun send(text: String) {
        val ask = text.trim()
        if (ask.isEmpty() || _state.value.busy) return
        if (!credentials.hasCredentials()) {
            _state.update { it.copy(aiReady = false, lines = it.lines + StudioLine(false, "Pick an AI in Settings → AI and I can build this for you.", error = true)) }
            return
        }
        val current = _state.value.design?.takeIf { TilesmithStore.design(context, it.id) != null }
        val picture = _state.value.attachment
        _state.update {
            it.copy(
                busy = true, busyFor = ask, busySince = System.currentTimeMillis(), step = "Reading your sources", stepDetail = null,
                suggestions = emptyList(), attachment = null, lines = it.lines + StudioLine(true, ask, picture = picture?.thumb),
            )
        }
        aiJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sources = TileNeeds.sourceStates(context)
                    TilesmithAi.draft(context, ask, sources, current, picture?.jpegBase64) { p ->
                        _state.update { s -> if (s.busy) s.copy(step = p.step, stepDetail = p.detail) else s }
                    }
                }
            }
            result.onSuccess { r ->
                val now = System.currentTimeMillis()
                val chat = (current?.chat.orEmpty() + TileChatTurn(true, ask, now) + TileChatTurn(false, r.reply, now)).takeLast(24)
                val reply = (listOf(r.reply) + r.notes).joinToString(" ")
                val design = r.design.copy(
                    id = current?.id ?: _state.value.design?.id ?: r.design.id,
                    createdAt = current?.createdAt ?: now,
                    chat = chat,
                )
                _state.update { it.copy(busy = false, busyFor = null, step = null, stepDetail = null, suggestions = r.suggestions, lines = it.lines + StudioLine(false, reply)) }
                show(design, undo = _state.value.undo + listOfNotNull(current))
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                // The picture goes back in the composer, so a retry doesn't need picking it again.
                _state.update {
                    it.copy(busy = false, busyFor = null, step = null, stepDetail = null, attachment = it.attachment ?: picture, lines = it.lines + StudioLine(false, e.message?.take(240) ?: "That didn't work. Try again?", error = true))
                }
            }
        }
    }

    fun stop() {
        aiJob?.cancel()
        _state.update { it.copy(busy = false, busyFor = null, step = null, stepDetail = null, lines = it.lines + StudioLine(false, "Stopped. Nothing changed.")) }
    }

    // ── Tweaks: one tap each, no AI ─────────────────────────────────────────

    private fun edit(change: (TileDesign) -> TileDesign) {
        val d = _state.value.design ?: return
        val next = change(d)
        if (next == d) return
        show(next, undo = _state.value.undo + d)
    }

    fun setSize(size: TileSize) {
        val d = _state.value.design ?: return
        val (next, dropped) = TileRules.reflow(d, size)
        _state.update { it.copy(dropped = dropped) }
        show(next, undo = _state.value.undo + d)
    }

    // ── One block at a time (the Blocks list) ───────────────────────────────

    /** [change] applied to the block at [index] in reading order. */
    fun updateBlock(index: Int, change: (TileBlock) -> TileBlock) = edit { d ->
        var i = 0
        d.copy(rows = d.rows.map { r -> r.copy(cells = r.cells.map { b -> if (i++ == index) change(b) else b }) })
    }

    fun removeBlock(index: Int) = edit { d ->
        if (d.blocks.size <= 1) return@edit d
        var i = 0
        d.copy(rows = d.rows.map { r -> r.copy(cells = r.cells.filter { i++ != index }) }.filter { it.cells.isNotEmpty() })
    }

    /** Moves the block at [index] one place earlier ([delta] -1) or later (+1) in reading order, across rows. */
    fun moveBlock(index: Int, delta: Int) = edit { d ->
        val flat = d.rows.flatMapIndexed { ri, r -> r.cells.map { ri to it } }.toMutableList()
        val j = index + delta
        if (index !in flat.indices || j !in flat.indices) return@edit d
        val a = flat[index]
        val b = flat[j]
        flat[index] = a.first to b.second
        flat[j] = b.first to a.second
        d.copy(rows = d.rows.mapIndexed { ri, r -> r.copy(cells = flat.filter { it.first == ri }.map { it.second }) })
    }

    /** Makes the block at [index] its row's hero (wider) or one share like the rest. */
    fun setWeight(index: Int, weight: Int) = updateBlock(index) { it.copy(weight = weight.coerceIn(1, 4)) }

    // ── A picture to build from ─────────────────────────────────────────────

    /** Reads the picked picture, shrinks it to a size every provider takes, and holds it for the next message. */
    fun attach(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val a = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val long = maxOf(raw.width, raw.height)
                val scaled = if (long > 1024) {
                    val f = 1024f / long
                    Bitmap.createScaledBitmap(raw, (raw.width * f).toInt().coerceAtLeast(1), (raw.height * f).toInt().coerceAtLeast(1), true)
                } else raw
                val bytes = java.io.ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    out.toByteArray()
                }
                val tl = maxOf(scaled.width, scaled.height)
                val thumb = Bitmap.createScaledBitmap(scaled, (scaled.width * 240f / tl).toInt().coerceAtLeast(1), (scaled.height * 240f / tl).toInt().coerceAtLeast(1), true)
                StudioAttachment(Base64.encodeToString(bytes, Base64.NO_WRAP), thumb)
            }.getOrNull()
            _state.update {
                if (a != null) it.copy(attachment = a)
                else it.copy(lines = it.lines + StudioLine(false, "Couldn't read that picture. Try another one?", error = true))
            }
        }
    }

    fun clearAttachment() = _state.update { it.copy(attachment = null) }

    fun setPalette(p: TilePalette) = edit { it.copy(palette = p) }

    fun setSurface(s: TileSurface) = edit { it.copy(surface = s) }

    fun setRefresh(minutes: Int) = edit { it.copy(refreshMinutes = minutes) }

    /** This design's AI lines on or off; its other blocks keep updating either way. */
    fun setAi(on: Boolean) {
        edit { it.copy(ai = on) }
        if (on) loadValues(force = true)
    }

    fun rename(name: String) = edit { it.copy(name = name.trim().take(40).ifBlank { it.name }) }

    /**
     * Puts every shape in the design on [shape]: shaped blocks change theirs, and when there is
     * none the first badge, metric or clock takes it.
     */
    fun setShape(shape: TileShape) = edit { d ->
        var touched = false
        val rows = d.rows.map { r ->
            r.copy(cells = r.cells.map { b ->
                if (b.style == BlockStyle.SHAPE || b.type == BlockType.BADGE) {
                    touched = true
                    b.copy(shape = shape, style = if (b.type == BlockType.BADGE && b.style != BlockStyle.FILLED) BlockStyle.SHAPE else b.style)
                } else {
                    b
                }
            })
        }
        if (touched) {
            d.copy(rows = rows)
        } else {
            var done = false
            d.copy(rows = d.rows.map { r ->
                r.copy(cells = r.cells.map { b ->
                    if (!done && b.type in setOf(BlockType.METRIC, BlockType.CLOCK)) {
                        done = true
                        b.copy(shape = shape, style = BlockStyle.SHAPE)
                    } else {
                        b
                    }
                })
            })
        }
    }

    fun undo() {
        val stack = _state.value.undo
        val prev = stack.lastOrNull() ?: return
        _state.update { it.copy(dropped = 0) }
        show(prev, undo = stack.dropLast(1))
    }

    // ── Approvals and the home screen ───────────────────────────────────────

    fun approve(ask: TileAsk) {
        when {
            ask.cmd != null -> TilesmithStore.approveCommand(context, ask.server, ask.cmd)
            ask.url != null -> TilesmithStore.approveSite(context, ask.url)
        }
        granted()
    }

    /** Asks the launcher to place the design; Android shows its own confirm. */
    fun pin(onUnsupported: () -> Unit) {
        val d = _state.value.design ?: return
        if (!TilesmithPin.supported(context)) {
            onUnsupported()
            return
        }
        _state.update { it.copy(pinning = true) }
        viewModelScope.launch {
            val ok = TilesmithPin.request(context, d)
            if (!ok) _state.update { it.copy(pinning = false) }
            // Android doesn't say when its dialog is dismissed; the button settles after a while.
            kotlinx.coroutines.delay(20_000)
            _state.update { it.copy(pinning = false) }
        }
    }

    fun delete(designId: String) {
        TilesmithStore.delete(context, designId)
        if (_state.value.design?.id == designId) newWidget()
        viewModelScope.launch(Dispatchers.IO) { TilesmithPin.redraw(context) }
    }

    fun duplicate(design: TileDesign): TileDesign =
        TilesmithStore.save(context, design.copy(id = TileDesign.newId(), name = (design.name + " copy").take(40), createdAt = System.currentTimeMillis(), chat = emptyList()))
}
