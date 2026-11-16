package com.macrotracker.ui.screens.link

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.macrotracker.data.link.LinkSession
import com.macrotracker.ui.theme.MacroMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** A finger still for this long starts a drag; lifting it ends it. */
private const val HOLD_TO_DRAG_MS = 380L

/** A touch shorter than this, that did not move, is a tap. */
private const val TAP_MS = 320L

/** Finger travel, in dp, for one notch of the wheel. */
private const val DP_PER_NOTCH = 26f

/** Two-finger speed, in notches per millisecond, that starts a roll, and where a roll ends. */
private const val FLING_MIN = 0.006f
private const val FLING_END = 0.0012f

/**
 * A laptop trackpad under the thumb. One finger moves the pointer (faster strokes travel
 * further), a tap clicks, a held finger drags, two fingers scroll and the page keeps rolling
 * after they lift, a two-finger tap is the right button and a three-finger tap the middle one.
 */
fun Modifier.trackpad(
    session: LinkSession,
    speed: Float,
    naturalScroll: Boolean,
    onTick: () -> Unit,
    onHold: () -> Unit,
    onTouched: () -> Unit = {},
): Modifier = pointerInput(session, speed, naturalScroll) {
    val slop = viewConfiguration.touchSlop
    val notch = DP_PER_NOTCH * density
    coroutineScope {
        var roll: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            roll?.cancel()   // a finger on the pad stops the page, as it does on a phone
            onTouched()
            val startedAt = SystemClock.uptimeMillis()
            var fingers = 1
            var travelled = Offset.Zero
            var moved = false
            var scrolled = false
            var dragging = false
            var scrollTravel = Offset.Zero
            // scroll speed in notches per millisecond, smoothed, for the roll after the fingers lift
            var speedX = 0f
            var speedY = 0f
            var scrolledAt = 0L
            down.consume()
            while (true) {
                val waiting = !moved && !dragging && fingers == 1
                val event: PointerEvent? = if (waiting) {
                    val left = HOLD_TO_DRAG_MS - (SystemClock.uptimeMillis() - startedAt)
                    if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    dragging = true
                    onHold()
                    session.button("left", true)
                    continue
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size > fingers) fingers = pressed.size
                if (pressed.size == 1 && (fingers == 1 || dragging)) {
                    val change = pressed[0]
                    val delta = change.positionChange()
                    travelled += delta
                    if (!moved && hypot(travelled.x, travelled.y) > slop) moved = true
                    if (moved || dragging) {
                        val ms = (change.uptimeMillis - change.previousUptimeMillis).coerceIn(1, 40)
                        val dpPerMs = hypot(delta.x, delta.y) / density / ms
                        // slow strokes stay precise, fast ones cross the screen
                        val gain = speed * (0.75f + 1.5f * dpPerMs.coerceAtMost(2.5f))
                        session.move(delta.x / density * gain, delta.y / density * gain)
                    }
                } else if (pressed.size >= 2) {
                    var sum = Offset.Zero
                    pressed.forEach { sum += it.positionChange() }
                    val delta = sum / pressed.size.toFloat()
                    scrollTravel += delta
                    if (!scrolled && hypot(scrollTravel.x, scrollTravel.y) > slop) scrolled = true
                    if (scrolled && pressed.size == 2) {
                        val sign = if (naturalScroll) 1f else -1f
                        // a mostly vertical stroke stays vertical
                        val dx = if (abs(delta.x) > abs(delta.y) * 0.6f) delta.x else 0f
                        val stepX = -sign * dx / notch
                        val stepY = sign * delta.y / notch
                        session.scroll(stepX, stepY)
                        val ms = (pressed[0].uptimeMillis - pressed[0].previousUptimeMillis).coerceIn(1, 40)
                        speedX = speedX * 0.6f + stepX / ms * 0.4f
                        speedY = speedY * 0.6f + stepY / ms * 0.4f
                        scrolledAt = pressed[0].uptimeMillis
                    }
                }
                event.changes.forEach { it.consume() }
            }
            if (dragging) {
                session.button("left", false)
            } else if (scrolled) {
                // fingers that stopped before lifting meant to stop; a flick keeps rolling
                if (SystemClock.uptimeMillis() - scrolledAt < 70 && hypot(speedX, speedY) > FLING_MIN) {
                    roll = launch {
                        var x = speedX
                        var y = speedY
                        while (hypot(x, y) > FLING_END) {
                            session.scroll(x * MacroMotion.Link.FLING_FRAME_MS, y * MacroMotion.Link.FLING_FRAME_MS)
                            x *= MacroMotion.Link.FLING_KEEP
                            y *= MacroMotion.Link.FLING_KEEP
                            delay(MacroMotion.Link.FLING_FRAME_MS)
                        }
                    }
                }
            } else if (!moved && SystemClock.uptimeMillis() - startedAt < TAP_MS) {
                onTick()
                session.click(
                    when (fingers) {
                        1 -> "left"
                        2 -> "right"
                        else -> "middle"
                    },
                )
            }
        }
    }
}

/**
 * The laptop's screen as a touchscreen, for the full-screen view: a tap clicks where it lands,
 * a drag scrolls the page with the finger, and a held finger is the right button. [toScreen]
 * turns a point of this surface into a point of the laptop's monitor (0..1), or null outside it.
 */
fun Modifier.touchscreen(
    session: LinkSession,
    toScreen: (Offset) -> Offset?,
    onTick: () -> Unit,
    onHold: () -> Unit,
): Modifier = pointerInput(session) {
    val slop = viewConfiguration.touchSlop
    val notch = DP_PER_NOTCH * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startedAt = SystemClock.uptimeMillis()
        var travelled = Offset.Zero
        var moved = false
        var held = false
        down.consume()
        toScreen(down.position)?.let { session.pointAt(it.x, it.y, click = false) }
        while (true) {
            val event: PointerEvent? = if (!moved && !held) {
                val left = HOLD_TO_DRAG_MS - (SystemClock.uptimeMillis() - startedAt)
                if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                held = true
                onHold()
                session.click("right")
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            var sum = Offset.Zero
            pressed.forEach { sum += it.positionChange() }
            val delta = sum / pressed.size.toFloat()
            travelled += delta
            if (!moved && hypot(travelled.x, travelled.y) > slop) moved = true
            if (moved && !held) {
                val dx = if (abs(delta.x) > abs(delta.y) * 0.6f) delta.x else 0f
                session.scroll(-dx / notch, delta.y / notch)
            }
            event.changes.forEach { it.consume() }
        }
        if (!moved && !held && SystemClock.uptimeMillis() - startedAt < TAP_MS) {
            onTick()
            session.click("left")
        }
    }
}

/** A mouse button you can hold: down on press, up on release, so a second finger can drag. */
fun Modifier.mouseButton(session: LinkSession, button: String, onTick: () -> Unit, onPressed: (Boolean) -> Unit): Modifier =
    pointerInput(session, button) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            onTick()
            onPressed(true)
            session.button(button, true)
            waitForUpOrCancellation()
            session.button(button, false)
            onPressed(false)
        }
    }

/**
 * Where the phone's keyboard types into. It is never drawn: it only holds focus so the
 * keyboard has somewhere to send text, and passes on each piece as it is committed (no
 * suggestions, nothing left composing), as the console's terminal does.
 */
@SuppressLint("ViewConstructor")
class LinkKeyView(context: Context) : View(context) {
    var onText: (String) -> Unit = {}
    var onKey: (String) -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun showKeyboard() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
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
                repeat(beforeLength.coerceIn(1, 64)) { onKey("backspace") }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                onKey("enter")
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) keyName(event.keyCode)?.let { onKey(it); return true }
                return keyName(event.keyCode) != null || super.sendKeyEvent(event)
            }

            private fun flush() {
                val content = editable ?: return
                if (content.isNotEmpty()) onText(content.toString())
                content.clear()
            }
        }
    }

    /** A hardware keyboard paired with the phone types on the laptop too. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isSystem) return super.onKeyDown(keyCode, event)
        keyName(keyCode)?.let { onKey(it); return true }
        val char = event.unicodeChar
        if (char != 0) {
            onText(String(Character.toChars(char)))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun keyName(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DEL -> "backspace"
        KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "enter"
        KeyEvent.KEYCODE_TAB -> "tab"
        KeyEvent.KEYCODE_ESCAPE -> "esc"
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_MOVE_HOME -> "home"
        KeyEvent.KEYCODE_MOVE_END -> "end"
        KeyEvent.KEYCODE_PAGE_UP -> "pageup"
        KeyEvent.KEYCODE_PAGE_DOWN -> "pagedown"
        else -> null
    }
}

/** The key a single typed character stands for, so ctrl + "c" is the chord and not a letter. */
fun chordKey(text: String): String? {
    if (text.length != 1) return null
    val c = text[0].lowercaseChar()
    return when {
        c in 'a'..'z' || c in '0'..'9' -> c.toString()
        c == ' ' -> "space"
        c == ',' -> "comma"
        c == '.' -> "dot"
        c == '-' -> "minus"
        c == '/' -> "slash"
        c == '=' -> "equal"
        else -> null
    }
}
