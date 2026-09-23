package com.macrotracker.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import com.macrotracker.ui.theme.MacroMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.macrotracker.ui.theme.AppIcons

private data class Feature(val icon: ImageVector, val title: String, val subtitle: String)

private val features = listOf(
    Feature(
        AppIcons.Dashboard,
        "Your personal dashboard",
        "Weather, calendar, Formula 1, YouTube, Twitch, GitHub and health stats — all on one home screen, laid out your way.",
    ),
    Feature(
        AppIcons.Blocks,
        "Fully customisable widgets",
        "Reorder, show or hide any widget. Build a home screen that shows exactly what matters to you, nothing more.",
    ),
    Feature(
        AppIcons.Restaurant,
        "Macro & nutrition tracking",
        "Log food manually, scan a label with your camera, or describe your meal and let AI fill in the numbers.",
    ),
    Feature(
        AppIcons.Sparkles,
        "AI-powered throughout",
        "Chat with Clanker to estimate meal macros, or ask Sysop about your servers. Connect Claude or add a Gemini, OpenAI or OpenRouter key in Settings → AI.",
    ),
)

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    // Staggered entrance animatables — all run in parallel with increasing delays
    val logoAlpha   = remember { Animatable(0f) }
    val logoY       = remember { Animatable(24f) }
    val titleAlpha  = remember { Animatable(0f) }
    val titleY      = remember { Animatable(16f) }
    val featuresAlpha = remember { Animatable(0f) }
    val featuresY   = remember { Animatable(16f) }
    val btnAlpha    = remember { Animatable(0f) }
    val btnY        = remember { Animatable(12f) }

    LaunchedEffect(Unit) {
        // Welcome uses EnterTransition.None — child stagger (incl. Y) is intentional.
        // Each element fades and rises together: its own launch per property.
        launch { logoAlpha.animateTo(1f, MacroMotion.revealTween(450)) }
        launch { logoY.animateTo(0f, MacroMotion.revealTween(450)) }
        delay(120)
        launch { titleAlpha.animateTo(1f, MacroMotion.revealTween()) }
        launch { titleY.animateTo(0f, MacroMotion.revealTween()) }
        delay(120)
        launch { featuresAlpha.animateTo(1f, MacroMotion.revealTween()) }
        launch { featuresY.animateTo(0f, MacroMotion.revealTween()) }
        delay(120)
        launch { btnAlpha.animateTo(1f, MacroMotion.revealTween(350)) }
        launch { btnY.animateTo(0f, MacroMotion.revealTween(350)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // ── Logo ──────────────────────────────────────────────────────
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "DailyDash",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    translationY = logoY.value
                }
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Title & tagline ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleY.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Welcome to DailyDash",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your personal dashboard — weather, sport, nutrition, health and more, all in one place.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Feature rows ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = featuresAlpha.value
                    translationY = featuresY.value
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            features.forEach { feature ->
                FeatureRow(feature)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── CTA ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = btnAlpha.value
                    translationY = btnY.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MacroButton(
                text = "Get started →",
                onClick = onGetStarted,
                variant = ButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Free · No account needed · Your data stays on your device",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = feature.subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
            )
        }
    }
}
