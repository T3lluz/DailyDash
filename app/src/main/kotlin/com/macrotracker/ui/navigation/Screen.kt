package com.macrotracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Health : Screen("health", "Health", Icons.Default.MonitorHeart)
    object AI : Screen("ai", "AI", Icons.Default.AutoAwesome) {
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
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
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
    const val HELP = "help"
    const val WIDGETS = "widgets"
    const val CAMERA_SCAN = "camera_scan"
}
