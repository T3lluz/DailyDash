package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import kotlin.math.max

@Composable
fun AnimatedHealthBarChart(
    values: List<Double>,
    labels: List<String>,
    selectedIndex: Int,
    color: Color,
    avgValue: Double,
    haptics: HapticHelper,
    valueFormatter: (Double) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartKey: Any? = null,
) {
    val chartMax = max(values.maxOrNull() ?: 1.0, 1.0)
    val reveal = remember(chartKey, values) { Animatable(0f) }
    LaunchedEffect(chartKey, values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val top = 24.dp.toPx()
            val bottom = size.height - 30.dp.toPx()
            val chartH = bottom - top
            for (i in 1..3) {
                val y = bottom - chartH * (i / 4f)
                drawLine(
                    color = Border.copy(alpha = 0.28f),
                    start = Offset(14f, y),
                    end = Offset(size.width - 14f, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (avgValue > 0) {
                val y = bottom - chartH * ((avgValue / chartMax).toFloat().coerceIn(0f, 1f))
                drawLine(
                    color = color.copy(alpha = 0.55f),
                    start = Offset(10f, y),
                    end = Offset(size.width - 10f, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .pointerInput(values) {
                    var last = -1
                    detectDragGestures(
                        onDragStart = {},
                        onDrag = { change, _ ->
                            if (values.isEmpty()) return@detectDragGestures
                            val x = change.position.x.coerceIn(0f, size.width.toFloat())
                            val idx = (x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                            if (idx != last) {
                                haptics.tick()
                                onSelect(idx)
                                last = idx
                            }
                        },
                    )
                }
                .pointerInput(values) {
                    detectTapGestures { touch ->
                        if (values.isEmpty()) return@detectTapGestures
                        val idx = (touch.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                        haptics.tick()
                        onSelect(idx)
                    }
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val selected = index == selectedIndex
                val fraction = ((value / chartMax).toFloat() * reveal.value).coerceIn(0f, 1f)
                val targetHeight = (14 + fraction * 140).dp
                val animatedHeight by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = MacroMotion.entranceSpring(),
                    label = "healthBar_$index",
                )
                val barAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.72f,
                    animationSpec = MacroMotion.colorTween(),
                    label = "healthBarAlpha_$index",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .graphicsLayer { alpha = 0.35f + 0.65f * reveal.value },
                ) {
                    Text(
                        text = if (value > 0 || selected) valueFormatter(value) else " ",
                        fontSize = if (selected) 11.sp else 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) color else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(if (selected) 26.dp else 18.dp)
                            .height(animatedHeight)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(color.copy(alpha = if (selected) barAlpha else barAlpha * 0.45f)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        labels.getOrElse(index) { "?" },
                        fontSize = 11.sp,
                        color = if (selected) TextPrimary else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedHealthAreaChart(
    values: List<Double>,
    labels: List<String>,
    selectedIndex: Int,
    color: Color,
    avgValue: Double,
    haptics: HapticHelper,
    valueFormatter: (Double) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartKey: Any? = null,
) {
    val chartMax = max(values.maxOrNull() ?: 1.0, 1.0)
    val chartMin = run {
        val positives = values.filter { it > 0 }
        if (positives.isEmpty()) 0.0 else (positives.minOrNull() ?: 0.0) * 0.85
    }
    val span = max(chartMax - chartMin, 0.1)
    val reveal = remember(chartKey, values) { Animatable(0f) }
    LaunchedEffect(chartKey, values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(850))
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(values) {
                var last = -1
                detectDragGestures(
                    onDragStart = {},
                    onDrag = { change, _ ->
                        if (values.isEmpty()) return@detectDragGestures
                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                        val idx = (x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                        if (idx != last) {
                            haptics.tick()
                            onSelect(idx)
                            last = idx
                        }
                    },
                )
            }
            .pointerInput(values) {
                detectTapGestures { touch ->
                    if (values.isEmpty()) return@detectTapGestures
                    val idx = (touch.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                    haptics.tick()
                    onSelect(idx)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = 16.dp.toPx()
            val right = size.width - 16.dp.toPx()
            // Extra top pad so the selected-day value fits above its point
            val top = 36.dp.toPx()
            val bottom = size.height - 34.dp.toPx()
            val width = right - left
            val height = bottom - top
            val progress = reveal.value

            for (i in 1..3) {
                val y = bottom - height * (i / 4f)
                drawLine(
                    color = Border.copy(alpha = 0.28f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            if (values.size >= 2) {
                val points = values.mapIndexed { i, v ->
                    val x = left + width * (i / (values.size - 1f))
                    val norm = if (v <= 0) 0f else ((v - chartMin) / span).toFloat().coerceIn(0f, 1f)
                    Offset(x, bottom - height * norm * progress)
                }

                val area = Path().apply {
                    moveTo(points.first().x, bottom)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, bottom)
                    close()
                }
                drawPath(path = area, color = color.copy(alpha = 0.12f * progress))

                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val cur = points[i]
                        val midX = (prev.x + cur.x) / 2f
                        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                    }
                }
                drawPath(
                    path = line,
                    color = color.copy(alpha = 0.95f * progress),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                if (avgValue > 0) {
                    val y = bottom - height * (((avgValue - chartMin) / span).toFloat().coerceIn(0f, 1f)) * progress
                    drawLine(
                        color = color.copy(alpha = 0.45f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                    )
                }

                points.forEachIndexed { i, p ->
                    val selected = i == selectedIndex
                    val radius = if (selected) 6.dp.toPx() else 3.5.dp.toPx()
                    if (values[i] > 0 || selected) {
                        drawCircle(color = Color.White.copy(alpha = progress), radius = radius + 2.dp.toPx(), center = p)
                        drawCircle(color = color.copy(alpha = progress), radius = radius, center = p)
                    }
                }

                // Value label anchored above the selected day's point
                if (selectedIndex in points.indices && values[selectedIndex] > 0) {
                    val p = points[selectedIndex]
                    val label = valueFormatter(values[selectedIndex])
                    val layout = textMeasurer.measure(
                        label,
                        TextStyle(
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    val padX = 8.dp.toPx()
                    val padY = 5.dp.toPx()
                    val tw = layout.size.width + padX * 2
                    val th = layout.size.height + padY * 2
                    var tipX = p.x - tw / 2f
                    tipX = tipX.coerceIn(6.dp.toPx(), size.width - tw - 6.dp.toPx())
                    var tipY = p.y - th - 10.dp.toPx()
                    if (tipY < 4.dp.toPx()) tipY = 4.dp.toPx()
                    drawRoundRect(
                        color = Surface.copy(alpha = 0.94f),
                        topLeft = Offset(tipX, tipY),
                        size = Size(tw, th),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                    drawRoundRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(tipX, tipY),
                        size = Size(tw, th),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(tipX + padX, tipY + padY),
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                labels.forEachIndexed { index, label ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (index == selectedIndex) TextPrimary else TextSecondary,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMacroBarChart(
    values: List<Int>,
    labels: List<String>,
    selectedIndex: Int,
    color: Color,
    avgValue: Double,
    haptics: HapticHelper,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxValue = max(values.maxOrNull() ?: 1, 1)
    val reveal = remember(values, color) { Animatable(0f) }
    LaunchedEffect(values, color) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (avgValue > 0) {
                val top = 16.dp.toPx()
                val bottom = size.height - 28.dp.toPx()
                val y = bottom - (bottom - top) * (avgValue / maxValue).toFloat().coerceIn(0f, 1f)
                drawLine(
                    color = color.copy(alpha = 0.4f),
                    start = Offset(8f, y),
                    end = Offset(size.width - 8f, y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )
            }
        }
        val scroll = rememberScrollState()
        val useScroll = values.size > 10
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .then(if (useScroll) Modifier.horizontalScroll(scroll) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val selected = index == selectedIndex
                val fraction = (value.toFloat() / maxValue) * reveal.value
                val h = (10 + fraction * 110).dp
                val animatedH by animateDpAsState(h, MacroMotion.entranceSpring(), label = "macroBar_$index")
                Column(
                    modifier = Modifier
                        .then(if (useScroll) Modifier.width(22.dp) else Modifier.weight(1f))
                        .clickable {
                            haptics.tick()
                            onSelect(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (useScroll) 1f else 0.7f)
                            .height(animatedH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) color else color.copy(alpha = 0.35f)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        labels.getOrElse(index) { "?" },
                        fontSize = 10.sp,
                        color = if (selected) TextPrimary else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
