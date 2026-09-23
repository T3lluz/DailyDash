package com.macrotracker.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import com.macrotracker.ui.theme.MacroMotion
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class TutorialPage(
    val icon: ImageVector,
    val accentColor: androidx.compose.ui.graphics.Color,
    val badge: String,
    val title: String,
    val body: String,
    val tips: List<String>,
)

private val PAGES = listOf(
    TutorialPage(
        icon = Icons.Outlined.Dashboard,
        accentColor = Primary,
        badge = "Home",
        title = "Your Personal Dashboard",
        body = "DailyDash is more than a macro tracker — it's a customisable home screen for your daily life. Weather, calendar, sport, news and nutrition all in one glanceable view.",
        tips = listOf(
            "Long-press and drag any home widget to reorder the layout",
            "Toggle widgets on or off in Widget-Settings",
            "Every section updates automatically throughout the day",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.Widgets,
        accentColor = androidx.compose.ui.graphics.Color(0xFFF59E0B),
        badge = "Widgets",
        title = "Live Info at a Glance",
        body = "The home screen pulls in live weather for your location, your Google Calendar events for the day, Formula 1 race schedules and a curated YouTube feed — refreshed automatically.",
        tips = listOf(
            "Weather requires location permission",
            "Calendar syncs with your Google account",
            "F1 widget shows the next race countdown",
            "YouTube card shows latest videos from your picks",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.Restaurant,
        accentColor = Secondary,
        badge = "Nutrition",
        title = "Macro Tracking Made Easy",
        body = "Log food by typing, scanning a nutrition label with your camera, or describing your meal to Clanker in plain English. DailyDash tracks calories and protein.",
        tips = listOf(
            "Quick Add on Home for fast manual logs",
            "Scan any nutrition facts label with the camera",
            "Progress bars turn red when you exceed a goal",
            "Delete entries from Recent Logs on Health",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.AutoAwesome,
        accentColor = androidx.compose.ui.graphics.Color(0xFFA855F7),
        badge = "AI",
        title = "Chat With Clanker",
        body = "On the AI tab, describe a meal like \"large bowl of porridge with banana\" — Clanker returns calories and protein you can portion and log.",
        tips = listOf(
            "Type a dish name to get smart add-on suggestions",
            "Add a Gemini, OpenAI, or OpenRouter API key in Settings → AI",
            "Confidence shows how sure the estimate is",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.FavoriteBorder,
        accentColor = androidx.compose.ui.graphics.Color(0xFFEC4899),
        badge = "Health",
        title = "Optional Health Metrics",
        body = "Connect Health Connect to layer in steps, heart rate, sleep, workouts, floors climbed and active calories alongside your nutrition data. All stored locally — nothing uploaded.",
        tips = listOf(
            "Enable Health Connect in Settings → Connections",
            "Sync Garmin Connect (or another fitness app) to Health Connect to see walks and rides",
            "Health Connect must be installed on your device",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.BarChart,
        accentColor = Secondary,
        badge = "Trends",
        title = "See Your Trends",
        body = "The Health tab charts your last 7, 14 or 30 days of nutrition. Tap any bar to drill into the individual food logs for that day.",
        tips = listOf(
            "Set daily goals in Settings → Daily Goals",
            "Green bars = under goal, red = over goal",
            "Stats screen shows your last 7 days",
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.CheckCircle,
        accentColor = Secondary,
        badge = "All set!",
        title = "You're Ready to Go 🎉",
        body = "DailyDash is your one-stop daily companion. Everything runs offline, nothing leaves your device, and you control exactly what you see.",
        tips = listOf(
           "Add the home-screen widget for instant macro stats",
            "Tap Help in Settings any time you need a refresher"
        ),
    ),
)

@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()

    val isLastPage = pagerState.currentPage == PAGES.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Skip button top-right
        if (!isLastPage) {
            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(PAGES.lastIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
            ) {
                Text("Skip", color = TextSecondary, fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
            ) {
                PAGES.indices.forEach { index ->
                    val isSelected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = MacroMotion.fadeTween(250),
                        label = "dotWidth",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(if (isSelected) Primary else Border),
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { pageIndex ->
                TutorialPageContent(page = PAGES[pageIndex])
            }

            // Bottom nav
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
            ) {
                if (isLastPage) {
                    MacroButton(
                        text = "Start Dashing 🚀",
                        onClick = onFinish,
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MacroButton(
                        text = "Next →",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialPageContent(page: TutorialPage) {
    // Fade-only page reveal — avoid stacking translateY on top of nav slide / pager.
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(page.title) {
        alpha.snapTo(0f)
        alpha.animateTo(1f, MacroMotion.revealTween(350))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .graphicsLayer {
                this.alpha = alpha.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Big icon circle with gradient glow
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            page.accentColor.copy(alpha = 0.25f),
                            page.accentColor.copy(alpha = 0.05f),
                        )
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = page.accentColor.copy(alpha = 0.18f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = page.accentColor,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Badge pill
        Box(
            modifier = Modifier
                .background(
                    color = page.accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50.dp),
                )
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = page.badge,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = page.accentColor,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = page.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = page.body,
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tips card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            page.tips.forEach { tip ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(6.dp)
                            .background(page.accentColor, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tip,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


