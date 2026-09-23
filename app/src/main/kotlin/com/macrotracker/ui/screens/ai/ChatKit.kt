package com.macrotracker.ui.screens.ai

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
import com.macrotracker.ui.components.LocalNavTabRise
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.components.TypingDots
import com.macrotracker.ui.components.dottedGlass
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.GlassHairline
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import com.macrotracker.ui.theme.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
val UserBubbleShape =
    RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
val ChatPillShape = RoundedCornerShape(999.dp)
internal val ComposerShape = RoundedCornerShape(22.dp)
private val ComposerSendShape = RoundedCornerShape(999.dp)

/** How much of the newest message may hide under the composer and still count as following along. */
private const val NEAR_BOTTOM_PX = 160

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

/** True while the newest message is (nearly) in view — i.e. the user is following along. */
@Composable
fun rememberNearChatBottom(listState: LazyListState): State<Boolean> = remember(listState) {
    derivedStateOf {
        val info = listState.layoutInfo
        val lastIndex = info.totalItemsCount - 1
        if (lastIndex < 0 || info.visibleItemsInfo.isEmpty()) return@derivedStateOf true
        val visibleBottom = info.viewportEndOffset - info.afterContentPadding
        // At most a little of the newest message (or, the moment one arrives, of the one
        // before it) is hidden under the composer.
        info.visibleItemsInfo.any { item ->
            item.index >= lastIndex - 1 && (item.offset + item.size) - visibleBottom <= NEAR_BOTTOM_PX
        }
    }
}

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
 * How far the floating composer sits above the bottom of its pane. The pane is lifted by
 * the keyboard (`imePadding`), so with the keyboard up only a small gap is left, and with
 * it down the composer clears the nav pill. Both ends follow the keyboard frame by frame,
 * so the composer never jumps when the keyboard starts to open or finishes closing.
 */
@Composable
internal fun composerBottomGap(): Dp {
    val density = LocalDensity.current
    val ime = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearNav = navBottom + PillNavClearance + LocalNavTabRise.current
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
            .background(Surface)
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
                text = identity.name.take(1),
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
                    .background(if (isError) Error.copy(alpha = 0.10f) else Surface)
                    .border(
                        1.dp,
                        if (isError) Error.copy(alpha = 0.35f) else Border,
                        BotBubbleShape,
                    )
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
fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(UserBubbleShape)
                .background(Primary)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(text = text, color = OnAccent, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun TypingBubble(identity: BotIdentity, label: String, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BotAvatar(identity, size = 30.dp, live = true)
        Spacer(modifier = Modifier.width(9.dp))
        Row(
            modifier = Modifier
                .clip(BotBubbleShape)
                .background(Surface)
                .border(1.dp, Border, BotBubbleShape)
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
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CopyChip(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(text) { mutableStateOf(false) }
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(ChatPillShape)
            .background(Surface)
            .border(1.dp, Border, ChatPillShape)
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
                    .background(Surface)
                    .border(1.dp, Border, ChatPillShape)
                    .clickable { onPick(starter) }
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
) {
    val bottomPad = composerBottomGap()
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
            .clip(ComposerShape)
            .dottedGlass(hazeState = hazeState, shape = ComposerShape)
            .border(1.dp, GlassHairline, ComposerShape)
            .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (leading != null) {
            leading()
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(vertical = 10.dp, horizontal = 4.dp),
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
                .padding(bottom = 2.dp)
                .size(38.dp)
                .clip(ComposerSendShape)
                .background(sendBackground, ComposerSendShape)
                .clickable(enabled = canSend, onClick = onSend),
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
