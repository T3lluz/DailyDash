package com.macrotracker.ui.screens.tilesmith

import android.content.Context
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
data class StudioLine(val fromUser: Boolean, val text: String, val error: Boolean = false)

data class StudioState(
    /** The design on the stage; null before the first draft lands. */
    val design: TileDesign? = null,
    /** What its sources read, for the preview. */
    val values: Map<String, TileValue> = emptyMap(),
    val lines: List<StudioLine> = emptyList(),
    /** The AI is working; [busyFor] is what it was asked. */
    val busy: Boolean = false,
    val busyFor: String? = null,
    val asks: List<TileAsk> = emptyList(),
    val aiReady: Boolean = false,
    /** The design before the last change, for Undo. */
    val undo: TileDesign? = null,
    /** Placed copies showing this design. */
    val placed: Int = 0,
    /** A pin request is up: Android's dialog is showing. */
    val pinning: Boolean = false,
    /** Blocks the last size change left out. */
    val dropped: Int = 0,
)

/**
 * The Tilesmith studio: a design on the stage, the conversation that shapes it, and what it
 * still needs. Every change is saved straight away (the widgets showing it redraw), so there is
 * no Save step; Undo steps back one change.
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
            saved != null -> show(saved, lines = saved.chat.map { StudioLine(it.fromUser, it.text) })
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

    private fun show(d: TileDesign, lines: List<StudioLine>? = null, save: Boolean = true, undo: TileDesign? = _state.value.undo) {
        val design = if (save) TilesmithStore.save(context, d) else d
        _state.update { s -> s.copy(design = design, lines = lines ?: s.lines, undo = undo) }
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
            _state.update { if (it.design?.id == d.id) it.copy(values = TilesmithStore.values(context, d.id)) else it }
            val v = runCatching { TileResolver.refresh(context, d, force) }.getOrNull() ?: return@launch
            _state.update { if (it.design?.id == d.id) it.copy(values = v) else it }
            // Placed copies show what was just read, and keep to the design's (maybe new) pace.
            if (_state.value.placed > 0) {
                TilesmithPin.redraw(context)
                WidgetRefreshWorker.scheduleFastLane(context)
            }
        }
    }

    /** What the design still needs; called again after each grant. */
    fun recheck() {
        val d = _state.value.design ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val asks = runCatching { TileNeeds.check(context, d) }.getOrDefault(emptyList())
            _state.update { if (it.design?.id == d.id) it.copy(asks = asks, aiReady = credentials.hasCredentials()) else it }
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
            _state.update { it.copy(aiReady = false, lines = it.lines + StudioLine(false, "Connect an AI in Settings → AI and I can build this for you.", error = true)) }
            return
        }
        val current = _state.value.design?.takeIf { TilesmithStore.design(context, it.id) != null }
        _state.update { it.copy(busy = true, busyFor = ask, lines = it.lines + StudioLine(true, ask)) }
        aiJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sources = TileNeeds.sourceStates(context)
                    TilesmithAi.draft(context, ask, sources, current)
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
                _state.update { it.copy(busy = false, busyFor = null, lines = it.lines + StudioLine(false, reply)) }
                show(design, undo = current)
            }.onFailure { e ->
                _state.update {
                    it.copy(busy = false, busyFor = null, lines = it.lines + StudioLine(false, e.message?.take(240) ?: "That didn't work. Try again?", error = true))
                }
            }
        }
    }

    fun stop() {
        aiJob?.cancel()
        _state.update { it.copy(busy = false, busyFor = null) }
    }

    // ── Tweaks: one tap each, no AI ─────────────────────────────────────────

    private fun edit(change: (TileDesign) -> TileDesign) {
        val d = _state.value.design ?: return
        val next = change(d)
        if (next == d) return
        show(next, undo = d)
    }

    fun setSize(size: TileSize) {
        val d = _state.value.design ?: return
        val (next, dropped) = TileRules.reflow(d, size)
        _state.update { it.copy(dropped = dropped) }
        show(next, undo = d)
    }

    fun setPalette(p: TilePalette) = edit { it.copy(palette = p) }

    fun setSurface(s: TileSurface) = edit { it.copy(surface = s) }

    fun setRefresh(minutes: Int) = edit { it.copy(refreshMinutes = minutes) }

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
        val prev = _state.value.undo ?: return
        show(prev, undo = null)
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
        viewModelScope.launch(Dispatchers.IO) { TilesmithPin.redraw(context) }
    }

    fun duplicate(design: TileDesign): TileDesign =
        TilesmithStore.save(context, design.copy(id = TileDesign.newId(), name = (design.name + " copy").take(40), createdAt = System.currentTimeMillis(), chat = emptyList()))
}
