package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics

/*
 * Finding a channel in a hub with a lot of them (YouTube's and Twitch's): a search button and
 * a star at the head of the channel row. The search opens a field over the row that narrows
 * the pictures (and the hub's feed) to what you type; the star keeps only the channels you
 * starred. A long press on a picture stars it, and starred channels lead the row.
 */

/** What a hub's channel row and feed are narrowed to. Saved with the card. */
@Stable
class ChannelFinderState(query: String = "", searching: Boolean = false, favouritesOnly: Boolean = false) {
    var query by mutableStateOf(query)
    var searching by mutableStateOf(searching)
    var favouritesOnly by mutableStateOf(favouritesOnly)

    /** Typed letters, trimmed; blank when nothing is typed. */
    val needle: String get() = query.trim()

    /** Whether anything narrows the hub at all. */
    val narrowing: Boolean get() = needle.isNotEmpty() || favouritesOnly

    /** Whether a channel called [name] with [id] stays in. */
    fun keeps(id: String, name: String, favourites: Set<String>): Boolean =
        (!favouritesOnly || id in favourites) && (needle.isEmpty() || name.contains(needle, ignoreCase = true))

    /** Whether any of [text] matches what is typed (a video's title, its channel). */
    fun matchesText(vararg text: String): Boolean = needle.isEmpty() || text.any { it.contains(needle, ignoreCase = true) }

    fun closeSearch() {
        query = ""
        searching = false
    }

    companion object {
        val Saver = listSaver<ChannelFinderState, Any>(
            save = { listOf(it.query, it.searching, it.favouritesOnly) },
            restore = { ChannelFinderState(it[0] as String, it[1] as Boolean, it[2] as Boolean) },
        )
    }
}

@Composable
fun rememberChannelFinderState(): ChannelFinderState = rememberSaveable(saver = ChannelFinderState.Saver) { ChannelFinderState() }

/** Starred channels first, each group in the row's own order. */
fun List<ChannelAvatar>.favouritesFirst(favourites: Set<String>): List<ChannelAvatar> =
    if (favourites.isEmpty()) this else sortedBy { if (it.id in favourites) 0 else 1 }

/** The star that marks a favourite, the island's amber. */
val FavouriteStar: Color = Warning

/**
 * A one-line search field in a round pill, for narrowing a list of channels. [onClose] (the
 * cross) clears and closes it. Focuses itself when [autoFocus].
 */
@Composable
fun ChannelSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    placeholder: String = "Find a channel",
    autoFocus: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = rememberHaptics()
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(Border.copy(alpha = 0.55f))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = TextTertiary, fontSize = 13.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (onClose != null || value.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (onClose != null) "Close search" else "Clear") {
                        haptics.tick()
                        if (onClose != null) {
                            keyboard?.hide()
                            onClose()
                        } else {
                            onValueChange("")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** The round button at the head of the channel row (search, favourites), as big as a picture. */
@Composable
internal fun FinderChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    size: Dp,
    onClick: () -> Unit,
    activeIcon: ImageVector = icon,
    activeTint: Color = accent,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f, MacroMotion.pressSpring(), label = "finder_press")
    val ring = animateColorAsState(if (active) accent else Border, MacroMotion.colorTween(), label = "finder_ring")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics {
                contentDescription = label
                selected = active
            }
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind {
                val w = (if (active) 2.5.dp else 1.dp).toPx()
                drawCircle(ring.value, radius = (this.size.minDimension - w) / 2f - 1.dp.toPx(), style = Stroke(w))
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = active,
            transitionSpec = { MacroMotion.iconSwapTransition },
            label = "finder_icon",
        ) { on ->
            Icon(
                if (on) activeIcon else icon,
                contentDescription = null,
                tint = if (on) activeTint else TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The star on a starred channel's picture: pops in when starred, out when not. */
@Composable
internal fun FavouriteBadge(visible: Boolean, background: Color, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.scaleIn(MacroMotion.bouncySpring()) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(background)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.StarFilled, contentDescription = null, tint = FavouriteStar, modifier = Modifier.size(11.dp))
        }
    }
}

/** A star button for a channel's row in a list: filled when it is a favourite. */
@Composable
fun FavouriteToggle(favourite: Boolean, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    // Pops when starred, not when the row first shows a star it already had.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(favourite) {
        if (shown && favourite) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, MacroMotion.bouncySpring())
        }
        shown = true
    }
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (favourite) "Remove $name from favourites" else "Add $name to favourites") {
                if (favourite) haptics.toggleOff() else haptics.toggleOn()
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favourite) AppIcons.StarFilled else AppIcons.Star,
            contentDescription = if (favourite) "Favourite" else "Not a favourite",
            tint = if (favourite) FavouriteStar else TextTertiary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}
