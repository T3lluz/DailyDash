package com.macrotracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics

/** What a channel's picture carries in a hub's filter row. */
enum class ChannelMark {
    None,

    /** On air, as Twitch marks it: a scarlet ring with a LIVE tag on its lower edge. */
    Live,

    /** Posted in the last day: a dot on the picture. */
    New,
}

@Immutable
data class ChannelAvatar(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val mark: ChannelMark = ChannelMark.None,
)

private val AvatarSize = 40.dp
private val RingGap = 2.dp
private val RingIdle = 1.dp
private val RingMarked = 2.dp
private val RingSelected = 2.5.dp

/** How far the LIVE tag hangs below the ring. */
private val TagDrop = 4.dp

private const val DIMMED_ALPHA = 0.42f
private const val DIMMED_SCALE = 0.9f
private const val PRESSED_SCALE = 0.92f

/**
 * A round channel picture inside a ring, with a sliver of card between the two, the way
 * Twitch rings a channel that is live. The ring is drawn from [ringColor] and [ringWidth]
 * in the draw phase, so animating it never recomposes the picture.
 */
@Composable
fun RingedAvatar(
    url: String?,
    name: String,
    ringColor: () -> Color,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSize,
    ringWidth: () -> Dp = { RingMarked },
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val request = remember(url, px) {
        ImageRequest.Builder(context).data(url).size(px).crossfade(true).build()
    }
    val imageRadius = size / 2
    Box(
        modifier = modifier
            .size(size + (RingSelected + RingGap) * 2)
            .drawBehind {
                val stroke = ringWidth().toPx()
                val color = ringColor()
                if (stroke > 0f && color.alpha > 0f) {
                    drawCircle(
                        color = color,
                        radius = imageRadius.toPx() + RingGap.toPx() + stroke / 2f,
                        style = Stroke(stroke),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(Border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().take(1).uppercase(),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
            )
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Twitch's LIVE tag: scarlet, white caps, cut out of whatever it sits on by a card-coloured edge. */
@Composable
fun LiveTag(color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        "LIVE",
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.5.dp, Surface, shape)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}

/**
 * The channel filter the YouTube and Twitch hubs share, collapsed and open: one round
 * picture per channel. Tapping one keeps its ring and fades the rest back; tapping it
 * again shows every channel. The ring says which one is picked, so nothing is written
 * under the row. Fires its own haptics.
 */
@Composable
fun ChannelAvatarRow(
    channels: List<ChannelAvatar>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    selectedRing: Color,
    markColor: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val leadingCount = if (leading != null) 1 else 0

    // Bring a picked channel into view, e.g. when the card opens on a filter picked while closed.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedId) {
        val index = channels.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            val target = index + leadingCount
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target }
            val inView = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!inView) {
                val to = (target - 1).coerceAtLeast(0)
                if (placed) listState.animateScrollToItem(to) else listState.scrollToItem(to)
            }
        }
        placed = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(rememberWidgetCrossAxisScrollLock()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                item(key = "leading") {
                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = MacroMotion.fadeTween(),
                                placementSpec = MacroMotion.slideTween(),
                                fadeOutSpec = MacroMotion.fadeTween(),
                            )
                            .padding(end = 2.dp),
                    ) { leading() }
                }
            }
            items(channels, key = { it.id }) { channel ->
                ChannelChip(
                    channel = channel,
                    selected = channel.id == selectedId,
                    dimmed = selectedId != null && channel.id != selectedId,
                    selectedRing = selectedRing,
                    markColor = markColor,
                    onClick = {
                        haptics.tick()
                        onSelect(if (channel.id == selectedId) null else channel.id)
                    },
                    modifier = Modifier.animateItem(
                        fadeInSpec = MacroMotion.fadeTween(),
                        placementSpec = MacroMotion.slideTween(),
                        fadeOutSpec = MacroMotion.fadeTween(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(
    channel: ChannelAvatar,
    selected: Boolean,
    dimmed: Boolean,
    selectedRing: Color,
    markColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val live = channel.mark == ChannelMark.Live
    // Kept as States and read in the layer and the ring's draw, so a tap never recomposes the picture.
    val scale = animateFloatAsState(
        targetValue = when {
            pressed -> PRESSED_SCALE
            dimmed -> DIMMED_SCALE
            else -> 1f
        },
        animationSpec = MacroMotion.pressSpring(),
        label = "channelChipScale",
    )
    val alpha = animateFloatAsState(
        targetValue = if (dimmed) DIMMED_ALPHA else 1f,
        animationSpec = MacroMotion.fadeTween(),
        label = "channelChipAlpha",
    )
    val ringColor = animateColorAsState(
        targetValue = when {
            selected -> selectedRing
            live -> markColor
            else -> Border
        },
        animationSpec = MacroMotion.colorTween(),
        label = "channelChipRing",
    )
    val ringWidth = animateDpAsState(
        targetValue = when {
            selected -> RingSelected
            live -> RingMarked
            else -> RingIdle
        },
        animationSpec = MacroMotion.pressSpring(),
        label = "channelChipRingWidth",
    )
    val ringBox = AvatarSize + (RingSelected + RingGap) * 2

    Box(
        modifier = modifier
            .width(ringBox)
            .height(if (live) ringBox + TagDrop else ringBox)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (selected) "Show every channel" else "Show only ${channel.name}",
                onClick = onClick,
            ),
    ) {
        RingedAvatar(
            url = channel.imageUrl,
            name = channel.name,
            ringColor = { ringColor.value },
            ringWidth = { ringWidth.value },
            contentDescription = channel.name,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        when (channel.mark) {
            ChannelMark.Live -> LiveTag(color = markColor, modifier = Modifier.align(Alignment.BottomCenter))
            ChannelMark.New -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(markColor),
            )
            ChannelMark.None -> Unit
        }
    }
}
