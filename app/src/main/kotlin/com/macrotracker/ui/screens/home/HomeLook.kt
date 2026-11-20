package com.macrotracker.ui.screens.home

import androidx.compose.runtime.setValue
import com.macrotracker.ui.components.Joined
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.LocalRoomColor
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.HealthHeartTone
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics

// Home's look, the dashboard's rooms on a phone: every card is a tile, a flat dark tint of
// its subject's colour with 28 dp corners and no edge, its title and numbers in the display
// face. A tile is the glance; a tap opens the subject's own page (HomePage.kt), which has
// the room the old unfolding cards never had.

/** One colour per subject, the dashboard's rooms' own. */
object HomeTone {
    val Weather = Color(0xFF5AB4F5)
    val Calendar = Color(0xFFFF9F45)
    val Briefing = Color(0xFFFFC94D)
    val Mail = Color(0xFFFF6369)
    val Upcoming = Color(0xFFC38AF0)
    val F1 = Color(0xFFFF4B45)
    val GitHub = Color(0xFF45CFC0)
    val Servers = Color(0xFF5CC98A)
    val YouTube = Color(0xFFFF5252)
    val Twitch = Color(0xFFA970FF)
    val Body = HealthHeartTone
    val Food = HealthNutritionTone
    val Neutral = Color(0xFFA3A3A3)

    fun of(id: String): Color = when (id) {
        "WEATHER" -> Weather
        "CALENDAR" -> Calendar
        "BRIEFING" -> Briefing
        "MAIL" -> Mail
        "UPCOMING" -> Upcoming
        "F1" -> F1
        "GITHUB" -> GitHub
        "SERVERS" -> Servers
        "YOUTUBE" -> YouTube
        "TWITCH" -> Twitch
        "BODY_STATS" -> Body
        "FOOD" -> Food
        else -> Neutral
    }
}

val RoomShape = RoundedCornerShape(28.dp)

/**
 * The name of a group of tiles that stands on a page by itself (a day of videos, the channels
 * being watched): in the page's colour, with a quiet count and, at its end, an action of its own.
 */
@Composable
fun PageGroupLabel(
    text: String,
    tone: Color,
    note: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().pageRise().padding(start = 8.dp, end = 2.dp, top = Joined.GroupSpace + 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = HealthDisplay, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-0.2).sp, color = tone, maxLines = 1)
        if (note != null) {
            Spacer(Modifier.width(9.dp))
            Text(note, style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextTertiary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** A block set into a room: a little lighter than the room, no edge. */
val WellShape = RoundedCornerShape(18.dp)
val WellFill = Color.White.copy(alpha = 0.055f)

/** A block set into a room, tinted with the room's subject. */
fun wellTint(tone: Color): Color = tone.copy(alpha = 0.12f)

/**
 * True while the tile under a finger is being dragged to a new place. A hold that lifts a
 * tile and lets go without moving it ends in a click; the tile reads this to ignore it.
 */
val LocalTileDragging = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * A Home tile: a room in its subject's colour. The header names it; with [onOpen] the whole
 * tile is a button that opens the subject's page, and a chevron says so. [flush] content
 * runs edge to edge (a carousel); anything else gets the room's side padding.
 */
@Composable
fun HomeTile(
    title: String,
    tone: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    logo: (@Composable () -> Unit)? = null,
    meta: String? = null,
    onOpen: (() -> Unit)? = null,
    flush: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = roomColor(tone)
    val haptics = rememberHaptics()
    val dragging = LocalTileDragging.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed && onOpen != null) 0.982f else 1f, MacroMotion.giveSpring(), label = "tileSink")
    CompositionLocalProvider(LocalRoomColor provides fill) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .graphicsLayer {
                    scaleX = sink.value
                    scaleY = sink.value
                }
                .clip(RoomShape)
                .background(fill)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Open $title") {
                            if (!dragging()) {
                                haptics.click()
                                onOpen()
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(top = 16.dp, bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(start = 18.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    logo != null -> {
                        Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { logo() }
                        Spacer(Modifier.width(8.dp))
                    }
                    icon != null -> {
                        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    title,
                    style = HealthDisplay,
                    fontSize = 19.sp,
                    color = tone,
                    maxLines = 1,
                    softWrap = false,
                )
                if (meta != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        meta,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                actions()
                if (onOpen != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.ChevronRight, contentDescription = null, tint = tone, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (flush) {
                content()
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), content = content)
            }
        }
    }
}

/** A tile that is still being read: its header over the room's loading bars. */
@Composable
fun HomeTileLoading(title: String, tone: Color, icon: ImageVector? = null, logo: (@Composable () -> Unit)? = null, bars: Int = 3) {
    HomeTile(title = title, tone = tone, icon = icon, logo = logo) {
        RoomLoading(tone, bars = bars)
    }
}

/** A tile that needs something first (a permission, an account): one line and one button. */
@Composable
fun HomeTilePrompt(
    title: String,
    tone: Color,
    message: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: (() -> Unit)?,
    icon: ImageVector? = null,
    logo: (@Composable () -> Unit)? = null,
) {
    HomeTile(title = title, tone = tone, icon = icon, logo = logo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, fontSize = 14.sp, lineHeight = 19.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            if (onAction != null) {
                Spacer(Modifier.width(12.dp))
                TonePill(actionLabel, tone, icon = actionIcon, primary = true, onClick = onAction)
            }
        }
    }
}

/** A room inside a page: [HomeTile]'s fill and corners, a quieter header, never a button. */
@Composable
fun PageRoom(
    tone: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    meta: String? = null,
    flush: Boolean = false,
    /** Makes the whole room a button to a page of its own, with a chevron in the header. */
    onOpen: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = roomColor(tone)
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // a room that opens a page sinks a little under a finger, as Home's tiles do
    val sink = animateFloatAsState(if (pressed && onOpen != null) 0.982f else 1f, MacroMotion.giveSpring(), label = "roomSink")
    CompositionLocalProvider(LocalRoomColor provides fill) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .pageRise()
                .padding(vertical = 8.dp)
                .graphicsLayer {
                    scaleX = sink.value
                    scaleY = sink.value
                }
                .clip(RoomShape)
                .background(fill)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(interactionSource = source, indication = null, role = Role.Button) {
                            haptics.click()
                            onOpen()
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 18.dp),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        title,
                        style = HealthDisplay,
                        fontSize = 19.sp,
                        color = tone,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (meta != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(meta, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing()
                    if (onOpen != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(AppIcons.ChevronRight, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (flush) content() else Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), content = content)
        }
    }
}

/** The big number of a tile or a room, with a small unit after it. */
@Composable
fun RoomNumber(value: String, modifier: Modifier = Modifier, unit: String? = null, size: Int = 40, color: Color = TextPrimary) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = HealthDisplay, fontSize = size.sp, lineHeight = size.sp, color = color, maxLines = 1, softWrap = false)
        if (unit != null) {
            Text(
                unit,
                style = HealthDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size * 0.42f).coerceAtLeast(12f).sp,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 3.dp, bottom = (size * 0.1f).dp),
            )
        }
    }
}

/** A reading in a row of readings: the number over its label. */
@Composable
fun RoomStat(value: String, label: String, modifier: Modifier = Modifier, tone: Color = TextPrimary, size: Int = 22) {
    Column(modifier) {
        Text(value, style = HealthDisplay, fontSize = size.sp, color = tone, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The dashboard's pill: a tinted capsule, or filled in the tone when it is the one thing to do. */
@Composable
fun TonePill(
    label: String,
    tone: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    /** A capsule on its own, or its place in a joined row of controls. */
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) 0.95f else 1f, MacroMotion.giveSpring(), label = "pillSink")
    var play by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val ink = if (primary) OnAccent else TextPrimary
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = sink.value
                scaleY = sink.value
            }
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (primary) tone else tone.copy(alpha = 0.16f))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.tick()
                play++
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) com.macrotracker.ui.components.AnimatedIcon(icon, play = play, tint = { if (primary) ink else tone }, size = 16.dp)
        Text(label, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink, maxLines = 1, softWrap = false)
    }
}

/** A round icon button on a room, tinted with its tone. */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    tone: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    filled: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) 0.9f else 1f, MacroMotion.giveSpring(), label = "roundSink")
    var play by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = sink.value
                scaleY = sink.value
            }
            .size(size)
            .clip(shape)
            .background(if (filled) tone else tone.copy(alpha = 0.14f))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.tick()
                play++
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        com.macrotracker.ui.components.AnimatedIcon(icon, play = play, tint = { if (filled) OnAccent else tone }, size = size * 0.5f)
    }
}

/** One choice of several in a row, the dashboard's segmented control: the picked one on a tint. */
@Composable
fun SegRow(
    options: List<String>,
    selected: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    counts: List<Int?> = emptyList(),
    onSelect: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val fill by animateColorAsState(if (on) tone.copy(alpha = 0.28f) else Color.Transparent, MacroMotion.colorTween(), label = "seg")
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable(role = Role.Tab) {
                        if (!on) haptics.tick()
                        onSelect(i)
                    }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = HealthDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (on) TextPrimary else TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
                counts.getOrNull(i)?.takeIf { it > 0 }?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("$it", style = HealthDisplay, fontSize = 12.sp, color = if (on) tone else TextTertiary, maxLines = 1)
                }
            }
        }
    }
}

/** A quiet heading inside a room, over a list or a chart. */
@Composable
fun RoomLabel(text: String, modifier: Modifier = Modifier, note: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSecondary)
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, fontSize = 12.sp, color = TextTertiary, maxLines = 1)
        }
    }
}

/** The page's dark under a room's tint, for something that must read as cut out of the room. */
fun roomWell(tone: Color): Color = lerp(Background, tone, 0.04f)

/** Padding a tile's flush content uses to line up with the header. */
val TileSidePadding = PaddingValues(horizontal = 18.dp)

/** A switch as the rooms draw one: no edge, a quiet track when off, [tone] when on. */
@Composable
fun roomSwitchColors(tone: Color = HomeTone.Servers): androidx.compose.material3.SwitchColors =
    androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = tone,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = TextTertiary,
        uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
        uncheckedBorderColor = Color.Transparent,
    )

/** A room in no particular colour, for a card that stands on the page by itself. */
val RoomNeutral: Color = roomColor(HomeTone.Neutral)

/** A sheet over a page: a touch lighter than the page, never a grey slab. */
val SheetColor: Color = lerp(Background, HomeTone.Neutral, 0.07f)

/** A text field's shape: as round as a well in a room. */
val FieldShape = RoundedCornerShape(18.dp)

/** A text field as the rooms draw one: a quiet fill that brightens with focus, never an outline. */
@Composable
fun roomFieldColors(tone: Color = TextSecondary): androidx.compose.material3.TextFieldColors =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.11f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
        disabledContainerColor = Color.White.copy(alpha = 0.04f),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = com.macrotracker.ui.theme.Error.copy(alpha = 0.7f),
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = TextPrimary,
        focusedLabelColor = TextSecondary,
        unfocusedLabelColor = TextTertiary,
        focusedPlaceholderColor = TextTertiary,
        unfocusedPlaceholderColor = TextTertiary,
    )
