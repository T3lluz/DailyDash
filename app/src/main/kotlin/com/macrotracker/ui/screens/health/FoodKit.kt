package com.macrotracker.ui.screens.health

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary

// What Food is built from, in the Health tab's look: the card that slides up for estimating a
// meal or scanning a label, and the fields, tiles and buttons the composer and both cards
// share. Everything is a flat tint of the room's green or of white; nothing has an edge.

/** A tile's corners, everywhere in Food. */
val FoodTile = RoundedCornerShape(20.dp)

/** The quiet fill of a field or a secondary tile on the room. */
val FoodWell = Color.White.copy(alpha = 0.06f)

/**
 * The card that slides up over Food: the room's own green-tinted dark, round at the top, most
 * of the screen tall, with a grab handle and a header (its name in the display face, a line
 * under it, a few round buttons and a close). A swipe down or the close puts it away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = roomColor(HealthNutritionTone)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = fill,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(HealthNutritionTone.copy(alpha = 0.45f)),
            )
        },
    ) {
        CompositionLocalProvider(LocalRoomColor provides fill) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = HealthDisplay, fontSize = 24.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                        if (subtitle != null) {
                            Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        actions()
                        FoodRoundButton(AppIcons.Close, "Close", onClick = onDismiss)
                    }
                }
                content()
            }
        }
    }
}

/** A round button on the room: a glyph on a quiet disc, or on the green one when it is [lit]. */
@Composable
fun FoodRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressSink(press)
            .size(size)
            .clip(CircleShape)
            .background(if (lit) HealthNutritionTone else FoodWell)
            .clickable(interactionSource = press, indication = LocalIndication.current, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> TextTertiary
                lit -> Color.Black
                else -> TextPrimary
            },
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** The one thing a card is there to do: a full-width green button with its words in the display face. */
@Composable
fun FoodPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressSink(press)
            .clip(FoodTile)
            .background(if (enabled) HealthNutritionTone else FoodWell)
            .clickable(interactionSource = press, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color.Black else TextTertiary, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = HealthDisplay, fontSize = 17.sp, color = if (enabled) Color.Black else TextTertiary, maxLines = 1)
    }
}

/** A second choice beside or under the main one: a tile tinted green with green words. */
@Composable
fun FoodGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: Color = HealthNutritionTone) {
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressSink(press)
            .clip(FoodTile)
            .background(tone.copy(alpha = 0.14f))
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1, softWrap = false)
    }
}

/** A line of words to type into: a quiet tile, the hint in grey until something is typed. */
@Composable
fun FoodField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(FoodTile)
            .background(FoodWell)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(HealthNutritionTone),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A number to type, set large on a tile in its own colour: what it is above, its unit after
 * it. [below] is a row under the number (the composer's quick-add chips).
 */
@Composable
fun FoodNumberTile(
    label: String,
    unit: String,
    tone: Color,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    below: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(FoodTile)
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) Text("0", style = HealthDisplay, fontSize = 30.sp, color = TextTertiary)
                BasicTextField(
                    value = value,
                    onValueChange = { typed -> onValueChange(foodNumberInput(typed, decimal)) },
                    singleLine = true,
                    textStyle = HealthDisplay.copy(color = TextPrimary, fontSize = 30.sp),
                    cursorBrush = SolidColor(tone),
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(unit, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
        if (below != null) {
            Spacer(modifier = Modifier.height(8.dp))
            below()
        }
    }
}

/** Digits and one decimal mark, no minus: a typo can't log −100 kcal. */
fun foodNumberInput(typed: String, decimal: Boolean = true): String {
    val kept = typed.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(7)
    val mark = kept.indexOfFirst { it == '.' || it == ',' }
    return if (mark < 0) kept else kept.substring(0, mark + 1) + kept.substring(mark + 1).filter(Char::isDigit)
}

/** What was typed as a whole number: "2,5" and "2.5" both read, nothing reads as zero. */
fun foodNumber(text: String): Int = text.replace(',', '.').toDoubleOrNull()?.let { kotlin.math.round(it).toInt() }?.coerceAtLeast(0) ?: 0

/** A small pill to tap: a quick-add step, a recent meal, a suggestion. */
@Composable
fun FoodChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: Color = TextPrimary, fill: Color = FoodWell, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1, softWrap = false)
    }
}
