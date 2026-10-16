package com.macrotracker.ui.screens.tilesmith

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.widget.tilesmith.TileDesign
import com.macrotracker.widget.tilesmith.TileStarters
import com.macrotracker.widget.tilesmith.TilesmithPin
import com.macrotracker.widget.tilesmith.TilesmithStore
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tilesmith's front door on the Widgets screen: describe a widget and go, or tap a starter, and
 * every widget you have made, each one tap from the studio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilesmithHero(
    onOpen: (designId: String?, prompt: String?, starterId: String?) -> Unit,
    viewModel: TilesmithViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val designs by viewModel.designs.collectAsState()
    val placedTick by TilesmithStore.placedChanges.collectAsState()
    val aiReady = viewModel.state.collectAsState().value.aiReady
    var prompt by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TileDesign?>(null) }

    MacroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(TilesmithAccent.copy(alpha = 0.32f), TilesmithAccent.copy(alpha = 0.08f)))),
                contentAlignment = Alignment.Center,
            ) {
                MorphingShape(HeroShapes, TilesmithAccent, Modifier.size(38.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tilesmith", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnAccent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TilesmithAccent).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "Describe a widget and AI builds it in Material 3 Expressive. Comment to change it.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(22.dp)
        val canGo = prompt.isNotBlank()
        fun go() {
            if (!canGo) return
            haptics.confirm()
            onOpen(null, prompt.trim(), null)
            prompt = ""
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SurfaceElevated)
                .border(1.dp, Border, shape)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(TilesmithAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                maxLines = 3,
                decorationBox = { inner ->
                    if (prompt.isEmpty()) {
                        Text(
                            if (aiReady) "A steps ring and my next meeting…" else "Pick a starter below (pick an AI to describe your own)",
                            color = TextTertiary,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                    inner()
                },
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canGo) TilesmithAccent else Border)
                    .clickable(enabled = canGo) { go() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Sparkles, contentDescription = "Make it", tint = if (canGo) OnAccent else TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Start from", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(TileStarters.all, key = { it.id }) { s ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .clickable { haptics.click(); onOpen(null, null, s.id) }
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeGlyph(
                        s.design.blocks.firstNotNullOfOrNull { it.shape } ?: com.macrotracker.widget.tilesmith.TileShape.COOKIE_9,
                        s.design.palette.takeIf { it != com.macrotracker.widget.tilesmith.TilePalette.WALLPAPER }?.let { p -> androidx.compose.ui.graphics.Color(p.seed) } ?: TilesmithAccent,
                        Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(s.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                        Text("${s.design.size.label} ${s.design.size.cells}", fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                    }
                }
            }
        }

        val mine = designs.orEmpty()
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Your widgets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(mine, key = { it.id }) { d ->
                    val placed by produceState(0, d.id, placedTick) {
                        value = withContext(Dispatchers.IO) { TilesmithPin.placedCount(context, d.id) }
                    }
                    Column(
                        Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { haptics.click(); onOpen(d.id, null, null) },
                                onLongClick = { haptics.tick(); deleting = d },
                            )
                            .padding(10.dp),
                    ) {
                        val size = TilesmithWidgetSpec.previewSizeOf(d.size)
                        TilePreview(
                            design = d,
                            values = remember(d.id, d.updatedAt) { TilesmithStore.values(context, d.id) },
                            size = size,
                            dark = context.isNight(),
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(d.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                        Text(
                            if (placed > 0) "On home screen" else "${d.size.label} · not placed",
                            fontSize = 11.sp,
                            color = if (placed > 0) Success else TextTertiary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${d.name}?") },
            text = { Text("Copies of it on your home screen will ask you to pick another design.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); viewModel.delete(d.id); deleting = null }) { Text("Delete", color = com.macrotracker.ui.theme.Error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.duplicate(d); deleting = null }) { Text("Duplicate", color = TilesmithAccent) }
                    TextButton(onClick = { deleting = null }) { Text("Cancel", color = TextSecondary) }
                }
            },
            containerColor = Surface,
        )
    }
}
