package com.macrotracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
fun DailyDashNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
    onboardingCompleted: Boolean = false,
    onOnboardingComplete: () -> Unit = {},
) {
    val tabOrder = listOf(
        Screen.Home.route,
        Screen.Health.route,
        Screen.AI.route,
        Screen.Settings.route,
    )
    val subScreenRoutes = setOf(
        "stats",
        "help",
        "widgets",
        "camera_scan",
        SettingsRoutes.CONNECTIONS,
        SettingsRoutes.AI,
        SettingsRoutes.NUTRITION,
        SettingsRoutes.SERVERS,
        SettingsRoutes.SERVER_DASHBOARD,
        SettingsRoutes.ABOUT,
    )

    fun getTabDirection(initial: String?, target: String?): Boolean {
        // The AI destination is declared with query args — compare the base route.
        val initialIdx = tabOrder.indexOf(initial?.substringBefore('?')).takeIf { it != -1 } ?: 0
        val targetIdx = tabOrder.indexOf(target?.substringBefore('?')).takeIf { it != -1 } ?: 0
        return targetIdx > initialIdx
    }

    fun isSubScreen(route: String?): Boolean = route != null && route in subScreenRoutes

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier.background(Background),
        enterTransition  = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                isSubScreen(to) -> MacroMotion.subScreenEnter
                isSubScreen(from) -> MacroMotion.subScreenPopEnter
                else -> MacroMotion.tabEnter(getTabDirection(from, to))
            }
        },
        exitTransition   = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                isSubScreen(to) -> MacroMotion.subScreenExit
                isSubScreen(from) -> MacroMotion.subScreenPopExit
                else -> MacroMotion.tabExit(getTabDirection(from, to))
            }
        },
        // Predictive back + system back: always slide horizontally (never fade).
        popEnterTransition = { MacroMotion.subScreenPopEnter },
        popExitTransition  = { MacroMotion.subScreenPopExit },
    ) {
        // ── Onboarding flow ──────────────────────────────────────────────
        composable(
            route          = OnboardingRoutes.WELCOME,
            enterTransition = { EnterTransition.None },
            exitTransition  = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition  = { MacroMotion.subScreenPopExit },
        ) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(OnboardingRoutes.PERMISSIONS) }
            )
        }

        composable(
            route = OnboardingRoutes.PERMISSIONS,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            PermissionsScreen(
                onContinue = {
                    navController.navigate(OnboardingRoutes.TUTORIAL)
                }
            )
        }

        composable(
            route = OnboardingRoutes.TUTORIAL,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            TutorialScreen(
                onFinish = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        // ── Main screens ─────────────────────────────────────────────────
        composable(Screen.Home.route) {
            val onNavigateToHealth = remember(navController) {
                { navController.navigate(Screen.Health.route) }
            }
            val onNavigateToServers = remember(navController) {
                { navController.navigate(SettingsRoutes.SERVER_DASHBOARD) }
            }
            HomeScreen(
                onNavigateToHealth = onNavigateToHealth,
                onNavigateToServers = onNavigateToServers,
            )
        }

        composable(Screen.Health.route) {
            val onNavigateToCameraScan = remember(navController) {
                { navController.navigate("camera_scan") }
            }
            HealthScreen(onNavigateToCameraScan = onNavigateToCameraScan)
        }

        composable(
            route = Screen.AI.PATTERN,
            arguments = listOf(
                navArgument(Screen.AI.ARG_TAB) { nullable = true; defaultValue = null },
                navArgument(Screen.AI.ARG_SEED) { nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val onNavigateToCameraScan = remember(navController) {
                { navController.navigate("camera_scan") }
            }
            val onNavigateToAiSettings = remember(navController) {
                { navController.navigate(SettingsRoutes.AI) }
            }
            AIScreen(
                onNavigateToCameraScan = onNavigateToCameraScan,
                onNavigateToAiSettings = onNavigateToAiSettings,
                initialTab = backStackEntry.arguments?.getString(Screen.AI.ARG_TAB),
                serverHandoffId = backStackEntry.arguments?.getString(Screen.AI.ARG_SEED),
            )
        }

        composable(Screen.Settings.route) {
            val onNavigateToConnections = remember(navController) {
                { navController.navigate(SettingsRoutes.CONNECTIONS) }
            }
            val onNavigateToAi = remember(navController) {
                { navController.navigate(SettingsRoutes.AI) }
            }
            val onNavigateToNutrition = remember(navController) {
                { navController.navigate(SettingsRoutes.NUTRITION) }
            }
            val onNavigateToAbout = remember(navController) {
                { navController.navigate(SettingsRoutes.ABOUT) }
            }
            val onNavigateToHelp = remember(navController) { { navController.navigate("help") } }
            val onNavigateToStats = remember(navController) { { navController.navigate("stats") } }
            val onNavigateToWidgets = remember(navController) { { navController.navigate("widgets") } }
            val onReplayTutorial = remember(navController) {
                {
                    navController.navigate(OnboardingRoutes.WELCOME) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            }
            SettingsScreen(
                onNavigateToConnections = onNavigateToConnections,
                onNavigateToAi = onNavigateToAi,
                onNavigateToNutrition = onNavigateToNutrition,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToHelp = onNavigateToHelp,
                onNavigateToStats = onNavigateToStats,
                onNavigateToWidgets = onNavigateToWidgets,
                onReplayTutorial = onReplayTutorial,
            )
        }

        // Sub-screens
        composable(
            route = SettingsRoutes.CONNECTIONS,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            val onNavigateToServers = remember(navController) {
                { navController.navigate(SettingsRoutes.SERVERS) }
            }
            ConnectionsSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToServers = onNavigateToServers,
            )
        }

        composable(
            route = SettingsRoutes.SERVERS,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            val onOpenDashboard = remember(navController) {
                { navController.navigate(SettingsRoutes.SERVER_DASHBOARD) }
            }
            ServersSettingsScreen(
                onNavigateBack = onNavigateBack,
                onOpenDashboard = onOpenDashboard,
            )
        }

        composable(
            route = SettingsRoutes.SERVER_DASHBOARD,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            val onNavigateToServerSettings = remember(navController) {
                { navController.navigate(SettingsRoutes.SERVERS) }
            }
            val onAskAi: (String) -> Unit = remember(navController) {
                { seedId -> navController.navigate(Screen.AI.withSeed(seedId)) }
            }
            ServerScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToServerSettings,
                onAskAi = onAskAi,
            )
        }

        composable(
            route = SettingsRoutes.AI,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            AiSettingsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = SettingsRoutes.NUTRITION,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            NutritionSettingsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = SettingsRoutes.ABOUT,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            AboutSettingsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "stats",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            StatsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "help",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            HelpScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "widgets",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            WidgetsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "camera_scan",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            val onNavigateToAiSettings = remember(navController) {
                { navController.navigate(SettingsRoutes.AI) }
            }
            CameraScanScreen(
                onNavigateBack = onNavigateBack,
                onLogged = onNavigateBack,
                onNavigateToAiSettings = onNavigateToAiSettings,
            )
        }
    }
}
