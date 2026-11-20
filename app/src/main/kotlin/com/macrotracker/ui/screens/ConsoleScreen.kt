package com.macrotracker.ui.screens

import com.macrotracker.ui.screens.home.RoomNeutral
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.screens.ai.MenuRow
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.viewmodel.ConsoleViewModel
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ConsoleBackground = Color(ConsoleViewModel.BACKGROUND)

/**
 * The dashboard server's shell, drawn by the phone (T3 Code's phone app has a native
 * terminal too). The shell itself is the server's (`console.py`): this streams what it
 * prints into a terminal emulator, draws the emulator's screen, and sends back what is
 * typed. The same shells are on the web console; leaving the screen leaves them running.
 * Reached from the AI tab's row, Home's Servers card, the Servers screen and the launcher.
 */
@Composable
fun ConsoleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberHaptics()
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var terminal by remember { mutableStateOf<TerminalCanvasView?>(null) }
    var ctrl by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    BackHandler { onNavigateBack() }
    // attached while the screen is in front; the shell outlives it either way
    val resumed = rememberIsResumed()
    DisposableEffect(viewModel, resumed) {
        if (resumed) viewModel.start()
        onDispose { viewModel.stop() }
    }

    fun type(text: String) {
        terminal?.scrollToEnd()
        viewModel.send(text)
    }
    /** A key by its Android code, as the shell's current mode wants it (arrows differ inside vim). */
    fun key(code: Int) {
        val e = viewModel.emulator
        KeyHandler.getCode(code, 0, e?.isCursorKeysApplicationMode == true, e?.isKeypadApplicationMode == true)?.let(::type)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ConsoleBackground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val current = state.current
        SubScreenHeader(
            title = "Console",
            subtitle = when {
                state.host.isBlank() -> "Set your dashboard server in Settings"
                current == null -> "fredde@${state.host.substringBefore('.')}"
                // the prompt says where the shell is; the header adds what runs in it
                else -> listOf("fredde@${state.host.substringBefore('.')}", current.cmd).filter { it.isNotBlank() }.joinToString(" · ")
            },
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                IconButton(onClick = { haptics.tick(); viewModel.newSession() }, modifier = Modifier.size(40.dp)) {
                    Icon(AppIcons.Add, "New shell", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Box {
                    IconButton(onClick = { haptics.tick(); viewModel.refreshSessions(); menu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.List, "Shells and more", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(RoomNeutral)) {
                        state.sessions.forEachIndexed { i, s ->
                            MenuRow(
                                label = "${i + 1}  ${s.cwd}${if (s.cmd.isNotBlank()) " · ${s.cmd}" else ""}${if (s.id == state.currentId) "  ✓" else ""}",
                                icon = AppIcons.Terminal,
                            ) { menu = false; viewModel.open(s.id) }
                        }
                        MenuRow("Copy the screen", AppIcons.Copy) {
                            menu = false
                            val text = viewModel.emulator?.screen?.transcriptText.orEmpty()
                            scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("Console", text))) }
                        }
                        MenuRow("Reconnect", AppIcons.Refresh) { menu = false; viewModel.reconnect() }
                        if (state.currentId != null) MenuRow("Close this shell", AppIcons.Close, danger = true) { menu = false; viewModel.closeCurrent() }
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { context ->
                    TerminalCanvasView(context).also { view ->
                        view.onSize = viewModel::setSize
                        view.onInput = { text -> viewModel.send(text) }
                        view.isCtrlHeld = { ctrl }
                        view.onCtrlUsed = { ctrl = false }
                        // the terminal has the keys from the start: with a keyboard attached, Enter
                        // went to the back arrow, which holds focus first on a new screen
                        view.post { view.requestFocus() }
                        terminal = view
                    }
                },
                update = { view ->
                    // read so a new frame redraws
                    state.frame
                    view.emulator = viewModel.emulator
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            )
            when {
                state.error != null || state.exited -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "The shell ended.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(14.dp))
                    PillButton(
                        icon = if (state.exited) AppIcons.Add else AppIcons.Refresh,
                        label = if (state.exited) "New shell" else "Reconnect",
                        accent = AiAccent, emphasized = true,
                        onClick = { if (state.exited) viewModel.newSession() else viewModel.reconnect() },
                    )
                }
                state.connecting && viewModel.emulator == null -> Box(Modifier.align(Alignment.Center)) { LoadingSpinner(color = AiAccent) }
            }
        }
        // The keys a phone keyboard lacks, over it: the row every terminal app grows.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConsoleKey("esc") { type("\u001b") }
            ConsoleKey("tab") { type("\t") }
            ConsoleKey("ctrl", on = ctrl) { ctrl = !ctrl }
            ConsoleKey("↑") { key(KeyEvent.KEYCODE_DPAD_UP) }
            ConsoleKey("↓") { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            ConsoleKey("←") { key(KeyEvent.KEYCODE_DPAD_LEFT) }
            ConsoleKey("→") { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            listOf("/", "-", "|", "~", "&", ";").forEach { c -> ConsoleKey(c) { type(c) } }
            ConsoleKey("home") { key(KeyEvent.KEYCODE_MOVE_HOME) }
            ConsoleKey("end") { key(KeyEvent.KEYCODE_MOVE_END) }
            ConsoleKey("paste") {
                scope.launch {
                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                    if (text.isNotEmpty()) type(text.replace("\r\n", "\r").replace('\n', '\r'))
                }
            }
            ConsoleKey("keys") { terminal?.showKeyboard() }
        }
    }
}

@Composable
private fun ConsoleKey(label: String, on: Boolean = false, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .height(38.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) AiAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button) { haptics.tick(); onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) AiAccent else TextPrimary,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

/**
 * The terminal's screen: draws what the emulator holds, takes the keyboard, and scrolls
 * back through what the shell printed. A drag inside a full-screen program (vim, less,
 * htop) is sent to it as the wheel or the arrows, as a terminal does.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class TerminalCanvasView(context: Context) : View(context) {

    var emulator: TerminalEmulator? = null
        set(value) {
            if (field !== value) topRow = 0
            field = value
        }
    var onSize: (cols: Int, rows: Int) -> Unit = { _, _ -> }
    var onInput: (String) -> Unit = {}
    var isCtrlHeld: () -> Boolean = { false }
    var onCtrlUsed: () -> Unit = {}

    private val renderer = TerminalRenderer((13 * resources.displayMetrics.scaledDensity).roundToInt(), Typeface.MONOSPACE)

    /** The first row drawn: 0 is the live screen, negative is that many rows back. */
    private var topRow = 0
    private var scrollRemainder = 0f

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showKeyboard()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val term = emulator ?: return true
            scrollRemainder += distanceY
            val rows = (scrollRemainder / renderer.fontLineSpacing).toInt()
            if (rows == 0) return true
            scrollRemainder -= rows * renderer.fontLineSpacing
            when {
                term.isMouseTrackingActive -> repeat(kotlin.math.abs(rows)) {
                    val button = if (rows < 0) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                    term.sendMouseEvent(button, (e2.x / renderer.fontWidth).toInt() + 1, (e2.y / renderer.fontLineSpacing).toInt() + 1, true)
                }
                term.isAlternateBufferActive -> {
                    val code = if (rows < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
                    KeyHandler.getCode(code, 0, term.isCursorKeysApplicationMode, term.isKeypadApplicationMode)?.let { c -> repeat(kotlin.math.abs(rows)) { onInput(c) } }
                }
                else -> {
                    topRow = (topRow + rows).coerceIn(-term.screen.activeTranscriptRows, 0)
                    invalidate()
                }
            }
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun scrollToEnd() {
        if (topRow != 0) {
            topRow = 0
            invalidate()
        }
    }

    fun showKeyboard() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(this, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        onSize((w / renderer.fontWidth).toInt(), h / renderer.fontLineSpacing)
    }

    override fun onDraw(canvas: Canvas) {
        val term = emulator ?: return
        // a screen shorter than the transcript it once had keeps to what there is
        topRow = topRow.coerceIn(-term.screen.activeTranscriptRows, 0)
        renderer.render(term, canvas, topRow, -1, -1, -1, -1)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) scrollRemainder = 0f
        gestures.onTouchEvent(event)
        return true
    }

    override fun onCheckIsTextEditor() = true

    /** What is typed goes to the shell as it is committed: no suggestions, no autocorrect, no composing left behind. */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                flush()
                return true
            }

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                flush()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtLeast(1)) { typed("\u007f") }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            private fun flush() {
                val content = editable ?: return
                if (content.isNotEmpty()) typed(content.toString())
                content.clear()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isSystem) return super.onKeyDown(keyCode, event)
        val term = emulator
        var mods = 0
        if (event.isCtrlPressed) mods = mods or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed) mods = mods or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) mods = mods or KeyHandler.KEYMOD_SHIFT
        val code = KeyHandler.getCode(keyCode, mods, term?.isCursorKeysApplicationMode == true, term?.isKeypadApplicationMode == true)
        if (code != null) {
            scrollToEnd()
            onInput(code)
            return true
        }
        val char = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (char == 0) return super.onKeyDown(keyCode, event)
        val text = String(Character.toChars(char))
        typed(if (event.isCtrlPressed) control(text) else if (event.isAltPressed) "\u001b$text" else text)
        return true
    }

    /** Text from the keyboard: Enter is a carriage return, and a held Ctrl turns the next letter into its control code. */
    private fun typed(text: String) {
        scrollToEnd()
        val out = text.replace("\r\n", "\r").replace('\n', '\r')
        if (isCtrlHeld() && out.isNotEmpty()) {
            onCtrlUsed()
            onInput(control(out.substring(0, 1)) + out.substring(1))
        } else {
            onInput(out)
        }
    }

    /** Ctrl+C is 0x03, Ctrl+[ is Esc, Ctrl+Space is NUL: the letter's code with its top bits off. */
    private fun control(key: String): String {
        val c = key[0]
        return when {
            c in 'a'..'z' -> (c - 'a' + 1).toChar().toString()
            c in 'A'..'Z' -> (c - 'A' + 1).toChar().toString()
            c == ' ' || c == '2' || c == '@' -> "\u0000"
            c == '[' || c == '3' -> "\u001b"
            c == '\\' || c == '4' -> "\u001c"
            c == ']' || c == '5' -> "\u001d"
            c == '^' || c == '6' -> "\u001e"
            c == '_' || c == '7' || c == '/' -> "\u001f"
            c == '8' || c == '?' -> "\u007f"
            else -> key
        }
    }
}
