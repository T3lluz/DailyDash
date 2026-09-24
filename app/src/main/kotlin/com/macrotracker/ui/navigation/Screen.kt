package com.macrotracker.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.macrotracker.ui.theme.AppIcons

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", AppIcons.Home)
    object Health : Screen("health", "Health", AppIcons.HeartPulse)
    object AI : Screen("ai", "AI", AppIcons.Sparkles) {
        /**
         * Optional args, so plain `navigate("ai")` still matches. Only the hand-off
         * *id* travels — the server context itself is far too big for a nav argument
         * and is picked up from [com.macrotracker.data.chat.ServerAiHandoff].
         */
        const val PATTERN = "ai?tab={tab}&seed={seed}"
        const val ARG_TAB = "tab"
        const val ARG_SEED = "seed"

        fun withSeed(seedId: String): String = "ai?tab=sysop&seed=$seedId"
    }
    object Settings : Screen("settings", "Settings", AppIcons.Settings)
}

// Onboarding flow — not part of the bottom-nav bar
object OnboardingRoutes {
    const val WELCOME = "onboarding_welcome"
    const val PERMISSIONS = "onboarding_permissions"
    const val TUTORIAL = "onboarding_tutorial"
}

// Settings category sub-screens
object SettingsRoutes {
    /** The server dashboard is a sub-screen, not a bottom-nav tab. */
    const val SERVER_DASHBOARD = "servers"

    const val CONNECTIONS = "settings_connections"
    const val AI = "settings_ai"
    const val NUTRITION = "settings_nutrition"
    const val SERVERS = "settings_servers"
    const val ABOUT = "settings_about"
}

// Other pushed sub-screens
object SubScreenRoutes {
    const val STATS = "stats"
    const val WIDGETS = "widgets"
    const val CAMERA_SCAN = "camera_scan"
}
