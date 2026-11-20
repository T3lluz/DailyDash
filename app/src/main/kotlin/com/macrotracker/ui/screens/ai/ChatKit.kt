package com.macrotracker.ui.screens.ai

import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.RoomNeutral
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.theme.Background
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.Success
import androidx.compose.ui.semantics.Role
import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.components.TypingDots
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.macrotracker.ui.theme.AppIcons

/**
 * Every visual primitive both chat bots share.
 *
 * The two bots mirror each other structurally rather than by two files kept in sync
 * by hand: they render through these same composables and differ only in
 * [BotIdentity]. A change to a bubble radius lands on both, or on neither.
 */
data class BotIdentity(
    val name: String,
    val accent: Color,
    /** Drawable avatar; when null a monogram badge in [accent] is drawn instead. */
    val avatarRes: Int? = null,
    val avatarIcon: ImageVector? = null,
    val composerHint: String,
)

/** Chat radii: square off the corner nearest the speaker, like a tail. */
val BotBubbleShape =
    RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
val UserBubbleShape =
    RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
val ChatPillShape = RoundedCornerShape(999.dp)
internal val ComposerShape = RoundedCornerShape(28.dp)

/** What you said: a room of its own, warmer than the page, the way Home's rooms carry a colour. */
private val UserBubbleFill = lerp(Background, AiAccent, 0.15f)

/** The accent-tinted bubble the Hermes chat draws a person's message in. */
private val AccentBubbleFill = lerp(Background, AiAccent, 0.22f)
private val ComposerSendShape = RoundedCornerShape(999.dp)

/** A room in the AI tab: the page's dark with a little of its orange in it, as Home's rooms carry their subject's colour. */
val AiRoom: Color = lerp(Background, AiAccent, 0.10f)

/** The AI tab's raised room, for the open or picked one of several. */
val AiRoomHi: Color = lerp(Background, AiAccent, 0.18f)

/** The AI tab's sheets: darker than a room, still warm. */
val AiSheet: Color = lerp(Background, AiAccent, 0.05f)

/** A menu in the AI tab: the app's menu card, warmed like the tab. */
val AiMenu: Color = lerp(com.macrotracker.ui.components.MenuColor, AiAccent, 0.07f)

/** Pill nav = 64dp + 8dp bottom pad; keep a little air above it. */
internal val PillNavClearance = 80.dp

// ── Scrolling ────────────────────────────────────────────────────────────────

/**
 * Scroll far enough that the newest message's bottom clears the floating composer. A
 * message already on screen is scrolled by what is left of it, so a reply streaming in
 * glides instead of jumping to its own top first.
 */
suspend fun LazyListState.followChatBottom(animate: Boolean = true) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) scrollToItem(lastIndex)
    val lastItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex } ?: return
    val visibleBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    val overflow = (lastItem.offset + lastItem.size) - visibleBottom
    if (overflow > 0) {
        if (animate) animateScrollBy(overflow.toFloat()) else scrollBy(overflow.toFloat())
    }
}

/**
 * The way back to the newest line from the jump button: one glide that eases into the end, as
 * a fling does, never a jump. From far up it first moves to about two screens short of the end
 * (scrolling the whole way would take seconds), then glides the rest, re-aimed every frame
 * from what the list has measured, so it lands exactly on the last line.
 */
suspend fun LazyListState.glideToChatBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    fun average(): Float = layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }?.map { it.size }?.average()?.toFloat()?.coerceAtLeast(1f) ?: viewport.toFloat()
    fun remaining(): Float {
        val info = layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val past = (last.offset + last.size - bottom).toFloat()
        return past + average() * (lastIndex - last.index)
    }
    if (remaining() > viewport * 2.5f) {
        val rows = kotlin.math.ceil(viewport * 2f / average()).toInt().coerceAtLeast(1)
        scrollToItem((lastIndex - rows).coerceAtLeast(0))
    }
    scroll {
        var then = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = ((now - then) / 1_000_000_000f).coerceIn(0f, 0.05f)
            then = now
            val left = remaining()
            if (left <= 0.5f) break
            // close a share of what is left each frame: quick at first, easing into the end
            val step = maxOf(left * (1f - kotlin.math.exp(-dt * GLIDE_RATE)), minOf(left, 2f))
            if (scrollBy(step) < 0.5f) break
        }
    }
}

/** How quickly the jump button's glide closes the way to the end, per second. */
private const val GLIDE_RATE = 9f

/**
 * Whether the chat follows its newest line. It starts pinned and stays pinned however much
 * is added under it. Only a finger changes that: a scroll that ends away from the end lets
 * go, and one that ends back at it (or a send, or the button that goes there) takes hold
 * again. Deciding from where the list happens to sit instead pulled a reader back down
 * each time a row arrived while the row before the last was still on screen.
 */
@Stable
class ChatPin {
    var pinned by mutableStateOf(true)
}

/** How far the chat's last line sits under the visible end; the most there is when it is not laid out. */
private fun LazyListState.hiddenBelow(): Float {
    val info = layoutInfo
    val last = info.totalItemsCount - 1
    val item = info.visibleItemsInfo.lastOrNull()
    return when {
        last < 0 || item == null -> 0f
        item.index < last -> Float.MAX_VALUE
        else -> ((item.offset + item.size) - (info.viewportEndOffset - info.afterContentPadding)).toFloat()
    }
}

@Composable
fun rememberChatPin(listState: LazyListState): ChatPin {
    val pin = remember(listState) { ChatPin() }
    val density = LocalDensity.current
    LaunchedEffect(listState, density) {
        val slack = with(density) { PIN_SLACK.toPx() }
        // set by a drag, and kept through the fling it throws: only that scroll may move the pin
        var byHand = false
        launch { listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) byHand = true } }
        snapshotFlow { listState.isScrollInProgress to listState.hiddenBelow() }.collect { (moving, hidden) ->
            if (!byHand) return@collect
            pin.pinned = hidden <= slack
            if (!moving) byHand = false
        }
    }
    return pin
}

/** A scroll that ends within this of the end is still following. */
private val PIN_SLACK = 24.dp

/**
 * Keeps the conversation pinned to its newest message while the space around it changes:
 * the keyboard sliding in or out, the composer growing a line, a reply streaming in. It
 * reacts to each layout with a plain scroll by what is hidden, never a fresh animation per
 * frame, so the chat moves with the keyboard instead of stuttering behind it. A message
 * that has just been added is left to the pane's own gliding follow.
 */
@Composable
fun FollowChatOnKeyboard(listState: LazyListState, shouldFollow: () -> Boolean) {
    val follow by rememberUpdatedState(shouldFollow)
    LaunchedEffect(listState) {
        var lastCount = -1
        snapshotFlow {
            val info = listState.layoutInfo
            val count = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.takeIf { it.index == count - 1 }
            val hidden = if (last == null) 0 else (last.offset + last.size) - (info.viewportEndOffset - info.afterContentPadding)
            count to hidden
        }
            .distinctUntilChanged()
            .collect { (count, hidden) ->
                val added = count != lastCount
                lastCount = count
                // A finger on the list, or a follow already gliding, has the scroll.
                if (added || hidden <= 0 || !follow() || listState.isScrollInProgress) return@collect
                try {
                    listState.scrollBy(hidden.toFloat())
                } catch (e: CancellationException) {
                    // The person grabbed the list mid-pin; keep listening unless this effect is gone.
                    currentCoroutineContext().ensureActive()
                }
            }
    }
}

/**
 * True once a finger has scrolled the chat well away from its end, for the button that
 * goes back there. Only a drag turns it on (a message arriving or a chat opening moves the
 * end, not the reader), and it turns on further out than it turns off, so the button does
 * not come and go around the line or as the composer changes height.
 */
@Composable
fun rememberAwayFromChatEnd(listState: LazyListState, pin: ChatPin? = null): State<Boolean> {
    val density = LocalDensity.current
    val away = remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState, density, pin) {
        val on = with(density) { 260.dp.toPx() }
        val off = with(density) { 40.dp.toPx() }
        var dragged = false
        launch { listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true } }
        // Away once a finger took the chat up, or whenever the chat has stopped following its
        // end (a jump to a found line, a chat opened part way): never while it follows a reply in.
        snapshotFlow { listState.hiddenBelow() to (pin?.pinned ?: true) }.collect { (hidden, following) ->
            if (hidden < off) {
                away.value = false
                dragged = false
            } else if (hidden > on && (dragged || !following)) {
                away.value = true
            }
        }
    }
    return away
}

/**
 * How far the floating composer sits above the bottom of its pane. The pane is lifted by
 * the keyboard (`imePadding`), so with the keyboard up only a small gap is left, and with
 * it down the composer clears the nav pill. Both ends follow the keyboard frame by frame,
 * so the composer never jumps when the keyboard starts to open or finishes closing.
 */
@Composable
internal fun composerBottomGap(clearsNavPill: Boolean = true): Dp {
    val density = LocalDensity.current
    val ime = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // A pushed screen has no nav pill, only the system bar.
    val clearNav = navBottom + if (clearsNavPill) PillNavClearance else 10.dp
    return (clearNav - ime).coerceAtLeast(10.dp)
}

// ── Pane header ──────────────────────────────────────────────────────────────

/**
 * The strip above each conversation: a live status line on the left, that bot's actions
 * on the right, in one row so the chat keeps the height. Both phone panes use it, so
 * switching tabs never shifts the chat up or down.
 */
@Composable
fun ChatPaneHeader(
    status: String,
    active: Boolean,
    accent: Color,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatStatusDot(active = active, accent = accent)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = status,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

// ── Avatar ───────────────────────────────────────────────────────────────────

@Composable
fun BotAvatar(
    identity: BotIdentity,
    size: Dp,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val ring by animateColorAsState(
        targetValue = if (live) identity.accent.copy(alpha = 0.55f) else Border,
        animationSpec = MacroMotion.colorTween(),
        label = "bot_ring",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RoomNeutral)
            .border(1.5.dp, ring, CircleShape)
            .padding(size * 0.08f),
        contentAlignment = Alignment.Center,
    ) {
        when {
            identity.avatarRes != null -> Image(
                painter = painterResource(identity.avatarRes),
                contentDescription = identity.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
            identity.avatarIcon != null -> Icon(
                imageVector = identity.avatarIcon,
                contentDescription = identity.name,
                tint = identity.accent,
                modifier = Modifier.size(size * 0.5f),
            )
            else -> Text(
                text = identity.name.take(1), style = HealthDisplay,
                color = identity.accent,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ChatStatusDot(active: Boolean, accent: Color) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = MacroMotion.fadeTween(),
        label = "chat_status_dot",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = alpha)),
    )
}

// ── Bubbles ──────────────────────────────────────────────────────────────────

/**
 * One assistant turn.
 *
 * [attachment] is the per-message slot Clanker hangs its estimate card off, and the
 * hook any future bot card uses — the bubble itself stays bot-agnostic.
 */
@Composable
fun BotBubble(
    identity: BotIdentity,
    text: String,
    isError: Boolean = false,
    /** Renders a caret and suppresses the copy button while text is still arriving. */
    streaming: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
    attachment: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        BotAvatar(identity, size = 30.dp, live = streaming, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Column(
            modifier = Modifier.widthIn(max = 330.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(BotBubbleShape)
                    .background(if (isError) Error.copy(alpha = 0.14f) else AiRoom)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    // Announce the finished reply once rather than on every token.
                    .semantics {
                        if (!streaming) liveRegion = LiveRegionMode.Polite
                    },
            ) {
                if (isError) {
                    Text(text = text, color = Error, fontSize = 14.sp, lineHeight = 21.sp)
                } else {
                    MarkdownText(
                        markdown = text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                        linkColor = identity.accent,
                        breaks = true,
                        streaming = streaming,
                    )
                }
            }

            if (!streaming && !isError && text.isNotBlank()) {
                CopyChip(text)
            }
            actions?.invoke()
            attachment?.let {
                Spacer(modifier = Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
fun UserBubble(text: String, onLongPress: (() -> Unit)? = null, onTap: (() -> Unit)? = null, accent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(UserBubbleShape)
                .background(if (accent) AccentBubbleFill else UserBubbleFill)
                .then(
                    if (onLongPress != null) {
                        // Held (and tapped, where a tap shows something): no ripple and no button role.
                        Modifier
                            .pointerInput(onLongPress, onTap) {
                                detectTapGestures(onLongPress = { onLongPress() }, onTap = onTap?.let { tap -> { tap() } })
                            }
                            .semantics { onLongClick("Message options") { onLongPress(); true } }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(text = text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun TypingBubble(identity: BotIdentity, label: String, onCancel: () -> Unit) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BotAvatar(identity, size = 30.dp, live = true)
        Spacer(modifier = Modifier.width(9.dp))
        Row(
            modifier = Modifier
                .clip(BotBubbleShape)
                .background(AiRoom)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypingDots(color = TextSecondary)
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Stop",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier
                .clip(ChatPillShape)
                .clickable(onClick = { haptics.tick(); onCancel() })
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Puts [text] on the clipboard with a tick of the motor. Android says "Copied" itself. */
@Composable
fun rememberCopy(text: String): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    // one lambda per text: a gesture keyed on it is not restarted by a recomposition mid-hold
    return remember(text, clipboard) {
        {
            haptics.click()
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Chat message", text))) }
            Unit
        }
    }
}

/** As [rememberCopy], for text worth building only when it is asked for (a whole chat). */
@Composable
fun rememberCopyLatest(text: () -> String): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val latest by rememberUpdatedState(text)
    return remember(clipboard) {
        {
            haptics.click()
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Chat", latest()))) }
            Unit
        }
    }
}

/** Copy, small and quiet, at the end of an answer's byline; it turns to a tick for a moment. */
@Composable
fun CopyButton(text: String) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    val copy = rememberCopy(text)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1600); copied = false }
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { haptics.tick(); copy(); copied = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (copied) AppIcons.Check else AppIcons.Copy,
            contentDescription = if (copied) "Copied" else "Copy the answer",
            tint = if (copied) Success else TextTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CopyChip(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(text) { mutableStateOf(false) }
    // "Copied" is a moment's confirmation, then the chip offers Copy again.
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Row(modifier = Modifier.padding(top = 6.dp)) {
        SmallActionChip(
            icon = AppIcons.Copy,
            label = if (copied) "Copied" else "Copy",
            onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Chat message", text))) }
                copied = true
            },
        )
    }
}

@Composable
fun SmallActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = ChatPillShape,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(WellFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

/** Starter questions on an empty thread. */
@Composable
fun ChatStarters(starters: List<String>, onPick: (String) -> Unit) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(starters) { starter ->
            Text(
                text = starter,
                fontSize = 12.sp,
                color = TextPrimary,
                modifier = Modifier
                    .clip(ChatPillShape)
                    .background(AiRoom)
                    .clickable { haptics.tick(); onPick(starter) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Composer ─────────────────────────────────────────────────────────────────

/**
 * The floating glass composer. [leading] is the per-bot slot — Clanker puts its
 * photo-attach menu there, Sysop leaves it empty.
 */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    hint: String,
    accent: Color,
    hazeState: HazeState?,
    leading: (@Composable () -> Unit)? = null,
    clearsNavPill: Boolean = true,
) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    val bottomPad = composerBottomGap(clearsNavPill)
    val canSend = enabled && value.isNotBlank()
    val sendBackground by animateColorAsState(
        targetValue = if (canSend) accent else Border,
        animationSpec = MacroMotion.colorTween(),
        label = "composer_send",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = bottomPad)
            // The navbar's and the island's glass: lift, blur, tint and edge alike.
            .chromeGlass(hazeState, ComposerShape)
            .padding(6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Joined.GlassGap),
    ) {
        // the line and send as joined tiles in the glass, as Hermes' composer draws them
        if (leading != null) leading()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(topStart = if (leading == null) 22.dp else 6.dp, bottomStart = if (leading == null) 22.dp else 6.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(vertical = 12.dp, horizontal = 14.dp),
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 4,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, color = TextSecondary, fontSize = 15.sp)
                }
                inner()
            },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp))
                .background(sendBackground)
                .clickable(enabled = canSend, onClick = { haptics.tick(); onSend() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Send,
                contentDescription = "Send",
                tint = if (canSend) OnAccent else TextSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
