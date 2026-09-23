package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.AppIcons

private data class HelpStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val STEPS = listOf(
    HelpStep(
        icon = AppIcons.Home,
        title = "Home Screen — Quick Add",
        body = "The Home screen shows a live greeting with today's date and all your widgets. Use the Quick Add widget to enter a food name (optional), calories and protein, then tap \"Add\". Tap \"View all logs\" to jump to the full Health tab.",
    ),
    HelpStep(
        icon = AppIcons.Rocket,
        title = "Customise Your Home Screen",
        body = "Tap the pencil icon (top-right of Home or Health) to enter edit mode. Toggle widgets on or off, then drag ☰ to reorder rows. Outside edit mode, long-press and drag any visible widget to reorder them.",
    ),
    HelpStep(
        icon = AppIcons.Camera,
        title = "Scan a Nutrition Label",
        body = "Tap \"Scan label\" on the AI tab or in Add Entry on Health. Point the camera at any nutrition facts label (or pick a photo from your gallery) and Clanker will read calories and protein for you.",
    ),
    HelpStep(
        icon = AppIcons.Sparkles,
        title = "AI Food Estimates",
        body = "On the AI tab, chat with Clanker — type something like \"1 medium avocado\" or \"burger\" (then tap add-ons like bacon), or tap + to send a meal photo. Adjust portion if needed, then log the estimate.",
    ),
    HelpStep(
        icon = AppIcons.ChartBar,
        title = "Macro Trends on Health",
        body = "On the Health tab, Macro Trends charts your last 7, 14 or 30 days. Use the range chips (7d / 14d / 30d) and the Calories / Protein toggle to switch views. Tap any bar to see and manage the individual food logs for that day.",
    ),
    HelpStep(
        icon = AppIcons.Server,
        title = "Monitor Your Servers",
        body = "Add an SSH host in Settings → Connections → Servers. The Servers card on Home opens a live dashboard; tap the sparkle on any section to ask Sysop, the Tech support bot on the AI tab, about it.",
    ),
    HelpStep(
        icon = AppIcons.Flag,
        title = "Set Daily Goals",
        body = "Go to Settings → Nutrition. Enter your calorie and protein targets and tap \"Save goals\". Progress bars turn red when you exceed a goal.",
    ),
    HelpStep(
        icon = AppIcons.Delete,
        title = "Delete a Log Entry",
        body = "On the Health tab, tap the X on any food entry in the Recent Logs list to delete it. You can also navigate back to a past date in the Health tab and delete entries from there.",
    ),
)

private data class FaqItem(val question: String, val answer: String)

private val FAQ = listOf(
    FaqItem(
        question = "Where is my data stored?",
        answer = "All data is stored locally on your device using a local database. Nothing is uploaded to a server. AI requests are sent to your selected provider (Gemini, OpenAI, OpenRouter, or Claude) but your food logs are never included.",
    ),
    FaqItem(
        question = "Why is my progress bar red?",
        answer = "The calorie progress bar turns red when your total calories for the day exceed your calorie goal. Set or adjust your goal in Settings → Nutrition.",
    ),
    FaqItem(
        question = "How do I connect AI?",
        answer = "Go to Settings → AI and pick a provider. For Claude, tap Connect Claude and sign in with a Pro, Max, Team, or Enterprise account — that uses your subscription instead of buying console API credits. Gemini, OpenAI, and OpenRouter still need a pasted key: Gemini starts with \"AIza\" (aistudio.google.com), OpenAI with \"sk-\" (platform.openai.com), OpenRouter with \"sk-or-\" (openrouter.ai/keys). A Claude API key (sk-ant-…) is optional if you prefer to pay per token.",
    ),
    FaqItem(
        question = "How do I connect Health Connect?",
        answer = "Go to Settings → Connections and toggle on Health Connect. Grant the permissions when prompted, including Exercise and exercise routes so Garmin (and other apps) can share workouts and GPS maps. You can then enable individual metrics independently.",
    ),
    FaqItem(
        question = "How do I see Garmin walks and rides?",
        answer = "In Garmin Connect, turn on Health Connect sync (including activities). In DailyDash, allow Health Connect exercise and route access. Workouts then appear on the Health tab in Activities. If a walk or ride has GPS, tap Show GPS map once to reveal the route.",
    ),
    FaqItem(
        question = "How do I connect Weather or Calendar?",
        answer = "Go to Settings → Connections and toggle on Weather Data (requires location permission) or Google Calendar (requires calendar permission). Weather uses your device location via Yr.no. Calendar shows today's events from any calendars you select.",
    ),
    FaqItem(
        question = "How accurate are AI estimates?",
        answer = "AI estimates are approximations. The confidence level (high / medium / low) shown in the result tells you how certain the AI is. For precise tracking, use scanned nutrition labels or manually entered values when available.",
    ),
)

@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .subScreenBottomPadding(),
    ) {
        SubScreenHeader(
            title = "Help & How-To",
            subtitle = "Get started in minutes",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Quick Start card
        MacroCard(delayMs = 60) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(AppIcons.Rocket, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quick Start", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            STEPS.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Background, RoundedCornerShape(10.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(step.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 3.dp))
                        Text(step.body, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    }
                }
                if (index < STEPS.lastIndex) {
                    HorizontalDivider(color = Border, thickness = 1.dp)
                }
            }
        }

        // FAQ card
        MacroCard(delayMs = 120) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(AppIcons.Help, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("FAQ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            FAQ.forEachIndexed { index, item ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(item.question, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
                    Text(item.answer, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                }
                if (index < FAQ.lastIndex) {
                    HorizontalDivider(color = Border, thickness = 1.dp)
                }
            }
        }
    }
}
