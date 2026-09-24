package com.macrotracker.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberReducedMotion
import org.intellij.lang.annotations.Language

/*
 * The liquid ripple from Essentials (github.com/sameerasw/essentials, MIT,
 * ui/modifiers/LiquidRippleModifier.kt): an AGSL shader that pushes every pixel out
 * along the line from an origin by a damped sine wave travelling outward, plus a
 * smaller, later second wave, with a faint highlight on the crests. Android 13+
 * only (RuntimeShader); older phones just don't ripple.
 */
@Language("AGSL")
private const val LIQUID_RIPPLE_AGSL = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float2 uOrigin;
    uniform float uTime;
    uniform float uAmplitude;
    uniform float uFrequency;
    uniform float uDecay;
    uniform float uSpeed;

    half4 main(float2 fragCoord) {
        float2 pos = fragCoord;
        float distance = length(pos - uOrigin);
        float delay = distance / uSpeed;
        float time = max(0.0, uTime - delay);

        float wave1 = uAmplitude * sin(uFrequency * time) * exp(-uDecay * time);

        float subTime = max(0.0, time - 0.22);
        float wave2 = (uAmplitude * 0.55) * sin(uFrequency * 1.15 * subTime) * exp(-(uDecay * 0.8) * subTime);

        float totalWave = wave1 + wave2;
        float2 n = normalize(pos - uOrigin);
        float2 newPos = pos + totalWave * n;

        float highlight = 0.16 * (totalWave / max(1.0, uAmplitude));

        return inputShader.eval(newPos) + half4(highlight, highlight, highlight, 0.0);
    }
"""

/**
 * Where a [liquidRipple] starts: the centre of the node tagged with [anchor], in the
 * rippling layer's own space. Plain fields, not state — it is only read when a ripple
 * draws, so a scroll moving the anchor never invalidates anything.
 */
class RippleOrigin {
    internal var layer: LayoutCoordinates? = null
    internal var anchor: LayoutCoordinates? = null

    internal fun resolve(): Offset {
        val layer = layer?.takeIf { it.isAttached } ?: return Offset.Unspecified
        val anchor = anchor?.takeIf { it.isAttached } ?: return Offset.Unspecified
        return layer.localPositionOf(anchor, anchor.size.toSize().center)
    }
}

@Composable
fun rememberRippleOrigin(): RippleOrigin = remember { RippleOrigin() }

/** Marks the node a [RippleOrigin]'s ripple starts from. */
fun Modifier.rippleAnchor(origin: RippleOrigin): Modifier =
    onGloballyPositioned { origin.anchor = it }

/**
 * Ripples this node's whole drawing once each time [trigger] goes up. The clock is read
 * only in the layer block, so a ripple redraws the layer each frame without recomposing.
 * With no anchor placed it starts from the centre. Off when animations are.
 */
fun Modifier.liquidRipple(
    trigger: Int,
    origin: RippleOrigin? = null,
    enabled: Boolean = true,
    durationMillis: Int = MacroMotion.LiquidRipple.DURATION_MS,
    amplitudeDp: Float = MacroMotion.LiquidRipple.AMPLITUDE_DP,
    frequency: Float = MacroMotion.LiquidRipple.FREQUENCY,
    decay: Float = MacroMotion.LiquidRipple.DECAY,
    speedDp: Float = MacroMotion.LiquidRipple.SPEED_DP,
): Modifier = composed {
    if (!enabled || rememberReducedMotion()) return@composed Modifier

    val animTime = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            animTime.snapTo(0f)
            animTime.animateTo(
                targetValue = durationMillis / 1000f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
            )
            animTime.snapTo(0f)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tracked = if (origin != null) Modifier.onGloballyPositioned { origin.layer = it } else Modifier
        tracked.then(
            Api33LiquidRipple.layer(animTime, origin, durationMillis, amplitudeDp, frequency, decay, speedDp),
        )
    } else {
        Modifier
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33LiquidRipple {
    @Composable
    fun layer(
        animTime: Animatable<Float, *>,
        origin: RippleOrigin?,
        durationMillis: Int,
        amplitudeDp: Float,
        frequency: Float,
        decay: Float,
        speedDp: Float,
    ): Modifier {
        val shader = remember { RuntimeShader(LIQUID_RIPPLE_AGSL) }

        return Modifier.graphicsLayer {
            val currentTime = animTime.value
            val maxTime = durationMillis / 1000f
            if (currentTime > 0f && currentTime < maxTime) {
                val start = origin?.resolve()?.takeIf { it.isSpecified } ?: size.center

                shader.setFloatUniform("uResolution", size.width, size.height)
                shader.setFloatUniform("uOrigin", start.x, start.y)
                shader.setFloatUniform("uTime", currentTime)
                shader.setFloatUniform("uAmplitude", amplitudeDp * density)
                shader.setFloatUniform("uFrequency", frequency)
                shader.setFloatUniform("uDecay", decay)
                shader.setFloatUniform("uSpeed", speedDp * density)

                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "inputShader").asComposeRenderEffect()
            } else {
                renderEffect = null
            }
        }
    }
}
