package com.macrotracker.ui.screens.link

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.link.LinkConfig
import com.macrotracker.data.link.LinkSession
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.LinkHandsTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.viewmodel.LinkViewModel
import org.intellij.lang.annotations.Language

/*
 * The trackpad as cloth stretched over a frame. A finger pushes a dent into it: the weave
 * around the finger is drawn in toward it and the dent's walls are a shade darker all round,
 * while the pad's edges, held by the frame, stay put. Lifting the finger lets the cloth spring
 * back, a touch past flat, then settle. Android 13+ (RuntimeShader); older phones get a soft
 * shadow under the finger instead.
 */
@Language("AGSL")
private const val CLOTH_DENT_AGSL = """
    uniform shader content;
    uniform float2 uTouch;
    uniform float uDepth;
    uniform float uRadius;
    uniform float2 uSize;
    uniform float uEdge;

    half4 main(float2 p) {
        float2 d = p - uTouch;
        float x = length(d) / uRadius;
        float f = exp(-x * x);
        // the frame holds the cloth: the pull fades out toward the pad's edges, which stay put
        float edge = clamp(min(min(p.x, uSize.x - p.x), min(p.y, uSize.y - p.y)) / uEdge, 0.0, 1.0);
        // the cloth is pulled into the dent: each point shows what lay further out, so the
        // weave stretches toward the finger (never folding over: the pull stays under 2.2)
        float2 src = uTouch + d * (1.0 + 1.25 * uDepth * f * edge);
        src = clamp(src, float2(0.5), uSize - 0.5);
        half4 c = content.eval(src);
        // lit from straight above, the dent's walls are a little darker than the flat cloth,
        // most where they are steepest, so the shading rings the finger evenly
        float slope = x * f * 2.33;
        // shade only while pressed in: springing back past flat must not brighten the cloth
        c.rgb *= 1.0 - 0.12 * max(uDepth, 0.0) * slope * edge;
        return c;
    }
"""

/** The weave: one faint dot every this many dp, offset every other row. */
private const val WEAVE_DP = 13f

/**
 * The trackpad, as a room in the hands' green with cloth on it. Until a finger has been on
 * it, it says what fingers do. Its left and right buttons sit at its foot, outside the dent.
 */
@Composable
internal fun LinkTrackpad(
    viewModel: LinkViewModel,
    config: LinkConfig,
    hint: Boolean,
    buttons: Boolean,
    modifier: Modifier = Modifier,
    /** A finger is on the pad (true) or has lifted (false). */
    onTouch: (Boolean) -> Unit = {},
) {
    val session = viewModel.session
    val haptics = rememberHaptics()
    val reduced = rememberReducedMotion()
    val room = roomColor(LinkHandsTone)
    val shape = RoundedCornerShape(28.dp)
    // where the finger is and how far the cloth is pressed; both read only while drawing
    var finger by remember { mutableStateOf(Offset.Unspecified) }
    val depth = remember { Animatable(0f) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(touching) {
        if (touching) depth.animateTo(1f, MacroMotion.Link.dentIn()) else depth.animateTo(0f, MacroMotion.Link.dentOut())
    }

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .then(if (reduced) Modifier else Modifier.clothDent({ finger }, { depth.value }))
                .background(room)
                .drawWithCache {
                    // the weave: one path of dots, made once for the pad's size
                    val step = WEAVE_DP * density
                    val r = 1.1f * density
                    val weave = Path()
                    var row = 0
                    var y = step / 2
                    while (y < size.height) {
                        var x = if (row % 2 == 0) step / 2 else step
                        while (x < size.width) {
                            weave.addOval(androidx.compose.ui.geometry.Rect(x - r, y - r, x + r, y + r))
                            x += step
                        }
                        y += step * 0.87f
                        row++
                    }
                    onDrawBehind { drawPath(weave, Color.White.copy(alpha = 0.07f)) }
                }
                .drawBehind {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && finger != Offset.Unspecified && depth.value > 0f) {
                        val r = 70.dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.14f * depth.value), Color.Transparent), finger, r), r, finger)
                    }
                }
                .trackpad(
                    session, config.speed, config.naturalScroll,
                    onTick = haptics::tick, onHold = haptics::click, onTouched = { viewModel.touched = true },
                    onFinger = { at ->
                        if (at != null) finger = at
                        if (touching != (at != null)) onTouch(at != null)
                        touching = at != null
                    },
                ),
        ) {
            // what the pad does, until a finger has been on it
            AnimatedVisibility(
                visible = hint,
                enter = fadeIn(MacroMotion.fadeTween()), exit = fadeOut(MacroMotion.revealTween()),
                modifier = Modifier.align(Alignment.Center).padding(bottom = 56.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Gesture(AppIcons.MousePointer, "Tap", "to click")
                    Gesture(AppIcons.Move, "Hold", "to drag")
                    Gesture(AppIcons.SwapVertical, "Two fingers", "to scroll")
                }
            }
        }
        AnimatedVisibility(
            visible = buttons,
            enter = fadeIn(MacroMotion.fadeTween()), exit = fadeOut(MacroMotion.fadeTween()),
            // 8 dp in from the pad's sides and foot, their outer corners the pad's (28) less that
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PadButton(session, "left", "Left", RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 20.dp), Modifier.weight(1f))
                PadButton(session, "right", "Right", RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 20.dp, bottomStart = 12.dp), Modifier.weight(1f))
            }
        }
    }
}

/** Dents this node's drawing where [finger] is, as far as [depth] says (0 flat, 1 pressed, below 0 springing back past flat). */
private fun Modifier.clothDent(finger: () -> Offset, depth: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) then(Api33Dent.modifier(finger, depth)) else this

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33Dent {
    private val shader by lazy { RuntimeShader(CLOTH_DENT_AGSL) }

    fun modifier(finger: () -> Offset, depth: () -> Float): Modifier = Modifier.graphicsLayer {
        val at = finger()
        val d = depth()
        if (at != Offset.Unspecified && kotlin.math.abs(d) > 0.004f) {
            shader.setFloatUniform("uTouch", at.x, at.y)
            shader.setFloatUniform("uDepth", d)
            shader.setFloatUniform("uRadius", 50.dp.toPx())
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uEdge", 28.dp.toPx())
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else {
            renderEffect = null
        }
    }
}

/**
 * A mouse button at the pad's foot: a solid shape a shade darker than the pad, a small mouse
 * with this button's side lit, and its name in the display face. Outside the dent, so the
 * cloth never warps it. Held, it fills with the hands' green and the mouse's side lights up.
 */
@Composable
private fun PadButton(session: LinkSession, button: String, label: String, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var down by remember { mutableStateOf(false) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(down) { press.animateTo(if (down) 1f else 0f, MacroMotion.Link.press()) }
    val face = lerp(roomColor(LinkHandsTone), Color.Black, 0.3f)
    Box(
        modifier
            .height(52.dp)
            .graphicsLayer {
                val s = 1f - 0.03f * press.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(face)
            .mouseButton(session, button, onTick = haptics::tick, onPressed = { down = it }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = press.value }.background(LinkHandsTone.copy(alpha = 0.22f)))
        // the mouse sits on the outer side, so the pair mirror each other
        val left = button == "left"
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (left) {
                MouseGlyph(left = true, lit = { 0.45f + 0.55f * press.value })
                Spacer(Modifier.width(9.dp))
            }
            Text(label, style = HealthDisplay, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (!left) {
                Spacer(Modifier.width(9.dp))
                MouseGlyph(left = false, lit = { 0.45f + 0.55f * press.value })
            }
        }
    }
}

/** A small mouse, outlined, with the side of the button it stands for filled in the hands' green. */
@Composable
private fun MouseGlyph(left: Boolean, lit: () -> Float) {
    Canvas(Modifier.size(width = 13.dp, height = 19.dp)) {
        val stroke = 1.6.dp.toPx()
        val body = Path().apply {
            addRoundRect(RoundRect(stroke / 2, stroke / 2, size.width - stroke / 2, size.height - stroke / 2, CornerRadius(size.width / 2)))
        }
        val split = size.height * 0.44f
        clipPath(body) {
            drawRect(
                LinkHandsTone.copy(alpha = lit()),
                topLeft = Offset(if (left) 0f else size.width / 2, 0f),
                size = Size(size.width / 2, split),
            )
        }
        drawPath(body, LinkHandsTone, style = Stroke(stroke))
        drawLine(LinkHandsTone, Offset(size.width / 2, 0f), Offset(size.width / 2, split), stroke)
        drawLine(LinkHandsTone, Offset(0f, split), Offset(size.width, split), stroke)
    }
}

/** One part of the trackpad's hint: an icon, what the fingers do, and what that does. */
@Composable
private fun Gesture(icon: ImageVector, what: String, does: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = LinkHandsTone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(what, style = HealthDisplay, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(does, color = TextSecondary, fontSize = 12.sp)
    }
}
