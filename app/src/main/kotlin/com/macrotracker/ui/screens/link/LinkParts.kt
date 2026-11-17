package com.macrotracker.ui.screens.link

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.link.LinkState
import com.macrotracker.data.link.LinkStatus
import com.macrotracker.data.link.switchDirection
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState

/** A monitor's name as a person says it: "LC27G5xT · HDMI" is the LC27G5xT, the panel is "Laptop screen". */
internal fun shortLabel(label: String?, name: String?): String =
    label?.substringBefore(" · ")?.ifBlank { null } ?: name.orEmpty()

/** Gives a little under the finger and springs back; the scale is read in the layer, never in composition. */
@Composable
internal fun Modifier.pressable(label: String, onClick: () -> Unit): Modifier {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) MacroMotion.Link.PRESSED_SCALE else 1f, MacroMotion.Link.press(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label) { haptics.tick(); onClick() }
}

/** A round button of the remote's header. */
@Composable
internal fun RoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.pressable(label, onClick).size(42.dp).clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(19.dp))
    }
}

/**
 * Which laptop, which of its monitors, and how far away it is. The monitor's name slides in
 * from the side the picture came from, so the pill moves with the swipe. Opens the sheet.
 */
@Composable
internal fun StatusPill(state: LinkState, laptopName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val back = state.status == LinkStatus.Reconnecting
    Row(
        modifier.pressable("Laptop and screen", onClick).clip(RoundedCornerShape(50)).background(Surface).padding(start = 14.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LivePulseDot(color = if (back) Warning else Success)
        Spacer(Modifier.width(8.dp))
        // both names give way before the ping does
        Text(laptopName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        // the separator stays put; only the monitor's name slides, inside its own space
        Text(" · ", color = TextTertiary, fontSize = 13.sp, maxLines = 1)
        val shown = state.shownMonitor
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val (dx, _) = switchDirection(initialState, targetState)
                if (dx == 0) {
                    fadeIn(MacroMotion.fadeTween()) togetherWith fadeOut(MacroMotion.fadeTween())
                } else {
                    (slideInHorizontally(MacroMotion.Link.swipe()) { it * dx } + fadeIn(MacroMotion.fadeTween())) togetherWith
                        (slideOutHorizontally(MacroMotion.Link.swipe()) { -it * dx } + fadeOut(MacroMotion.fadeTween()))
                } using SizeTransform(clip = true)
            },
            label = "monitor",
            // the name slides inside its own space, never over the laptop's, and gives way before the ping
            modifier = Modifier.weight(1f, fill = false).clipToBounds(),
        ) { m ->
            val name = if (back) "Reconnecting" else shortLabel(m?.label, state.monitor)
            if (name.isNotBlank()) {
                Text(name, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!back) {
            state.pingMs?.let { Text(" · $it ms", color = TextTertiary, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

/**
 * A tile of the dock, after Health's meters: a flat tint of its own colour with its icon in
 * that colour; the icon says it all, its name is for screen readers. On, the tile fills more of
 * the colour and the icon turns white, with a small hop each time it is flipped. [shape] lets
 * the end tiles take the trackpad's corners.
 */
@Composable
internal fun DockTile(icon: ImageVector, label: String, tone: Color, shape: Shape, modifier: Modifier = Modifier, on: Boolean = false, onClick: () -> Unit) {
    val fill by animateColorAsState(lerp(Background, tone, if (on) 0.42f else 0.14f), MacroMotion.colorTween(), label = "dock")
    val ink by animateColorAsState(if (on) Color.White else tone, MacroMotion.colorTween(), label = "dockInk")
    val hop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on != seen) {
            seen = on
            hop.snapTo(0.74f)
            hop.animateTo(1f, MacroMotion.Link.press())
        }
    }
    Box(modifier.pressable(label, onClick).height(54.dp).clip(shape).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = ink, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = hop.value; scaleY = hop.value })
    }
}

/** A control of the full-screen panel. Each arrives a beat after the one before it. */
@Composable
internal fun PanelButton(index: Int, icon: ImageVector, label: String, tone: Color = TextPrimary, on: Boolean = false, onClick: () -> Unit) {
    val arrived = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrived.animateTo(1f, MacroMotion.Link.arrive(index)) }
    val fill by animateColorAsState(if (on) tone.copy(alpha = 0.24f) else Color.Transparent, MacroMotion.colorTween(), label = "panel")
    Column(
        Modifier
            .graphicsLayer {
                alpha = arrived.value
                val s = 0.82f + 0.18f * arrived.value
                scaleX = s
                scaleY = s
            }
            .pressable(label, onClick)
            .width(62.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) tone else TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (on) tone else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun PanelDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 26.dp).background(Color.White.copy(alpha = 0.1f)))
}

/** Folds the panel away to this one button, and opens it again. */
@Composable
internal fun PanelHandle(open: Boolean, onClick: () -> Unit) {
    val turn = animateFloatAsState(if (open) 0f else 1f, MacroMotion.Link.panel(), label = "handle")
    Box(Modifier.pressable(if (open) "Hide the controls" else "Show the controls", onClick).size(46.dp), contentAlignment = Alignment.Center) {
        // one of the two carries the name, so a screen reader (and a test) can find the handle
        Icon(AppIcons.Close, if (open) "Hide the controls" else "Show the controls", tint = TextPrimary, modifier = Modifier.size(18.dp).graphicsLayer { alpha = 1f - turn.value; rotationZ = -90f * turn.value })
        Icon(AppIcons.Ellipsis, null, tint = TextPrimary, modifier = Modifier.size(20.dp).graphicsLayer { alpha = turn.value; rotationZ = 90f * (1f - turn.value) })
    }
}

/** A line that says what just happened, dropping in under the status bar. */
@Composable
internal fun LinkToast(message: String?, hazeState: HazeState?, modifier: Modifier = Modifier) {
    // the words stay while it leaves
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(MacroMotion.Link.swipe()) { -it * 2 } + fadeIn(MacroMotion.fadeTween()),
        exit = slideOutVertically(MacroMotion.slideTween()) { -it * 2 } + fadeOut(MacroMotion.fadeTween()),
        modifier = modifier.statusBarsPadding().padding(top = 58.dp, start = 24.dp, end = 24.dp),
    ) {
        Text(
            last,
            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier
                .chromeGlass(hazeState, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The keys a phone keyboard lacks, over it. Ctrl, alt, super and shift wait for the next key. */
@Composable
internal fun KeyRow(mods: Set<String>, tone: Color, onMods: (Set<String>) -> Unit, onKey: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("esc", tone) { onKey("esc") }
        KeyChip("tab", tone) { onKey("tab") }
        listOf("ctrl", "alt", "super", "shift").forEach { mod ->
            KeyChip(mod, tone, on = mod in mods) { onMods(if (mod in mods) mods - mod else mods + mod) }
        }
        KeyChip("←", tone) { onKey("left") }
        KeyChip("↓", tone) { onKey("down") }
        KeyChip("↑", tone) { onKey("up") }
        KeyChip("→", tone) { onKey("right") }
        KeyChip("del", tone) { onKey("delete") }
        KeyChip("home", tone) { onKey("home") }
        KeyChip("end", tone) { onKey("end") }
        KeyChip("pgup", tone) { onKey("pageup") }
        KeyChip("pgdn", tone) { onKey("pagedown") }
        (1..12).forEach { n -> KeyChip("F$n", tone) { onKey("f$n") } }
    }
}

@Composable
private fun KeyChip(label: String, tone: Color, on: Boolean = false, onClick: () -> Unit) {
    val fill by animateColorAsState(if (on) tone.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f), MacroMotion.colorTween(), label = "key")
    Box(
        Modifier
            .pressable(label, onClick)
            .height(38.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) tone else TextPrimary,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

/** A row of a room that is a switch: what it does, one short line on what that means, and the switch in the room's colour. */
@Composable
internal fun ToggleRow(title: String, sub: String?, on: Boolean, tone: Color, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch) { if (on) haptics.toggleOff() else haptics.toggleOn(); onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            if (sub != null) Text(sub, color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = tone, checkedThumbColor = Color.White, checkedBorderColor = tone,
                uncheckedTrackColor = Color.White.copy(alpha = 0.06f), uncheckedBorderColor = Color.White.copy(alpha = 0.16f), uncheckedThumbColor = TextTertiary,
            ),
        )
    }
}

/** A row of a room that does something once: a tinted icon and what it does. */
@Composable
internal fun ActionRow(icon: ImageVector, title: String, tone: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(title, onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/**
 * One of a few, as a track with a highlight that slides to the picked one. The highlight's
 * place is read while laying out, so the slide never recomposes.
 */
@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, picked: T, tone: Color, onPick: (T) -> Unit) {
    val haptics = rememberHaptics()
    val index = options.indexOfFirst { it.first == picked }.coerceAtLeast(0)
    val at = animateFloatAsState(index.toFloat(), MacroMotion.Link.segment(), label = "segment")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(3.dp),
    ) {
        val cell = maxWidth / options.size
        Box(
            Modifier
                .offset { IntOffset((cell.toPx() * at.value).toInt(), 0) }
                .width(cell)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(tone.copy(alpha = 0.3f)),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.RadioButton) { haptics.tick(); onPick(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, color = if (i == index) TextPrimary else TextSecondary,
                        fontSize = 13.sp, fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A room's name, in the display face and its colour, with its icon. */
@Composable
internal fun RoomTitle(icon: ImageVector, title: String, tone: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = HealthDisplay, fontSize = 19.sp, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
