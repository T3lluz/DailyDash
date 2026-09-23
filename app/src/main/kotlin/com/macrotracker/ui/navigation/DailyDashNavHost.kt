package com.macrotracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.macrotracker.ui.screens.AIScreen
import com.macrotracker.ui.screens.CameraScanScreen
import com.macrotracker.ui.screens.HealthScreen
import com.macrotracker.ui.screens.HelpScreen
import com.macrotracker.ui.screens.HomeScreen
import com.macrotracker.ui.screens.ServerScreen
import com.macrotracker.ui.screens.SettingsScreen
import com.macrotracker.ui.screens.StatsScreen
import com.macrotracker.ui.screens.WidgetsScreen
import com.macrotracker.ui.screens.onboarding.PermissionsScreen
import com.macrotracker.ui.screens.onboarding.TutorialScreen
import com.macrotracker.ui.screens.onboarding.WelcomeScreen
import com.macrotracker.ui.screens.settings.AboutSettingsScreen
import com.macrotracker.ui.screens.settings.AiSettingsScreen
import com.macrotracker.ui.screens.settings.ConnectionsSettingsScreen
import com.macrotracker.ui.screens.settings.NutritionSettingsScreen
import com.macrotracker.ui.screens.settings.ServersSettingsScreen
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion

private val TabOrder = listOf(
    Screen.Home.route,
    Screen.Health.route,
    Screen.AI.route,
    Screen.Settings.route,
)

private val SubScreens = setOf(
    SubScreenRoutes.STATS,
    SubScreenRoutes.HELP,
    SubScreenRoutes.WIDGETS,
    SubScreenRoutes.CAMERA_SCAN,
    SettingsRoutes.CONNECTIONS,
    SettingsRoutes.AI,
    SettingsRoutes.NUTRITION,
    SettingsRoutes.SERVERS,
    SettingsRoutes.SERVER_DASHBOARD,
    SettingsRoutes.ABOUT,
)

private fun isSubScreen(route: String?): Boolean = route != null && route in SubScreens

/** The AI destination is declared with query args — compare base routes. */
private fun tabMovesRight(from: String?, to: String?): Boolean {
    val fromIdx = TabOrder.indexOf(from?.substringBefore('?')).coerceAtLeast(0)
    val toIdx = TabOrder.indexOf(to?.substringBefore('?')).coerceAtLeast(0)
    return toIdx > fromIdx
}

@Composable
fun DailyDashNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
    onOnboardingComplete: () -> Unit = {},
    /** False when no AI provider is configured — the AI tab is hidden, so nothing may link to it. */
    aiAvailable: Boolean = true,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.background(Background),
        enterTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                isSubScreen(to) -> MacroMotion.subScreenEnter
                isSubScreen(from) -> MacroMotion.subScreenPopEnter
                else -> MacroMotion.tabEnter(tabMovesRight(from, to))
            }
        },
        exitTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                isSubScreen(to) -> MacroMotion.subScreenExit
                isSubScreen(from) -> MacroMotion.subScreenPopExit
                else -> MacroMotion.tabExit(tabMovesRight(from, to))
            }
        },
        // Predictive back + system back: always slide horizontally (never fade).
        popEnterTransition = { MacroMotion.subScreenPopEnter },
        popExitTransition = { MacroMotion.subScreenPopExit },
    ) {
        // ── Onboarding flow ──────────────────────────────────────────────
        composable(
            route = OnboardingRoutes.WELCOME,
            enterTransition = { EnterTransition.None },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            WelcomeScreen(onGetStarted = { navController.navigateToSubScreen(OnboardingRoutes.PERMISSIONS) })
        }

        subScreen(OnboardingRoutes.PERMISSIONS) {
            PermissionsScreen(onContinue = { navController.navigateToSubScreen(OnboardingRoutes.TUTORIAL) })
        }

        subScreen(OnboardingRoutes.TUTORIAL) {
            TutorialScreen(
                onFinish = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
                        // Replaying the tutorial from Settings leaves Home underneath.
                        launchSingleTop = true
                    }
                },
            )
        }

        // ── Tabs ─────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToHealth = { navController.navigateToTab(Screen.Health.route) },
                onNavigateToServers = { navController.navigateToSubScreen(SettingsRoutes.SERVER_DASHBOARD) },
            )
        }

        composable(Screen.Health.route) {
            HealthScreen(onNavigateToCameraScan = { navController.navigateToSubScreen(SubScreenRoutes.CAMERA_SCAN) })
        }

        composable(
            route = Screen.AI.PATTERN,
            arguments = listOf(
                navArgument(Screen.AI.ARG_TAB) { nullable = true; defaultValue = null },
                navArgument(Screen.AI.ARG_SEED) { nullable = true; defaultValue = null },
            ),
        ) { entry ->
            AIScreen(
                onNavigateToCameraScan = { navController.navigateToSubScreen(SubScreenRoutes.CAMERA_SCAN) },
                onNavigateToAiSettings = { navController.navigateToSubScreen(SettingsRoutes.AI) },
                initialTab = entry.arguments?.getString(Screen.AI.ARG_TAB),
                serverHandoffId = entry.arguments?.getString(Screen.AI.ARG_SEED),
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToConnections = { navController.navigateToSubScreen(SettingsRoutes.CONNECTIONS) },
                onNavigateToAi = { navController.navigateToSubScreen(SettingsRoutes.AI) },
                onNavigateToNutrition = { navController.navigateToSubScreen(SettingsRoutes.NUTRITION) },
                onNavigateToAbout = { navController.navigateToSubScreen(SettingsRoutes.ABOUT) },
                onNavigateToHelp = { navController.navigateToSubScreen(SubScreenRoutes.HELP) },
                onNavigateToStats = { navController.navigateToSubScreen(SubScreenRoutes.STATS) },
                onNavigateToWidgets = { navController.navigateToSubScreen(SubScreenRoutes.WIDGETS) },
                onReplayTutorial = {
                    navController.navigate(OnboardingRoutes.WELCOME) {
                        popUpTo(Screen.Home.route)
                    }
                },
            )
        }

        // ── Sub-screens ──────────────────────────────────────────────────
        subScreen(SettingsRoutes.CONNECTIONS) { entry ->
            ConnectionsSettingsScreen(
                onNavigateBack = { navController.popSubScreen(entry) },
                onNavigateToServers = { navController.navigateToSubScreen(SettingsRoutes.SERVERS) },
            )
        }

        subScreen(SettingsRoutes.SERVERS) { entry ->
            ServersSettingsScreen(
                onNavigateBack = { navController.popSubScreen(entry) },
                onOpenDashboard = {
                    navController.navigate(SettingsRoutes.SERVER_DASHBOARD) { launchSingleTop = true }
                },
            )
        }

        subScreen(SettingsRoutes.SERVER_DASHBOARD) { entry ->
            ServerScreen(
                onNavigateBack = { navController.popSubScreen(entry) },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoutes.SERVERS) { launchSingleTop = true }
                },
                // The AI tab is a tab: switch to it rather than stacking it on this screen.
                onAskAi = if (aiAvailable) {
                    { seedId -> navController.navigateToTab(Screen.AI.withSeed(seedId), restoreState = false) }
                } else {
                    null
                },
            )
        }

        subScreen(SettingsRoutes.AI) { entry ->
            AiSettingsScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SettingsRoutes.NUTRITION) { entry ->
            NutritionSettingsScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SettingsRoutes.ABOUT) { entry ->
            AboutSettingsScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SubScreenRoutes.STATS) { entry ->
            StatsScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SubScreenRoutes.HELP) { entry ->
            HelpScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SubScreenRoutes.WIDGETS) { entry ->
            WidgetsScreen(onNavigateBack = { navController.popSubScreen(entry) })
        }

        subScreen(SubScreenRoutes.CAMERA_SCAN) { entry ->
            CameraScanScreen(
                onNavigateBack = { navController.popSubScreen(entry) },
                onLogged = { navController.popSubScreen(entry) },
                onNavigateToAiSettings = { navController.navigateToSubScreen(SettingsRoutes.AI) },
            )
        }
    }
}
