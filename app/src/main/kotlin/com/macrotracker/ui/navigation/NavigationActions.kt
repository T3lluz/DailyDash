package com.macrotracker.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.macrotracker.ui.theme.MacroMotion

/**
 * Switches bottom-nav tabs. Each tab keeps its own saved stack, so a tab must never
 * be pushed on top of another one — otherwise the other tab's saved stack ends with
 * this one and tapping that tab just reopens it.
 *
 * Pass `restoreState = false` when the destination carries arguments that must win
 * over whatever the tab had saved (e.g. a server hand-off into the AI tab).
 */
fun NavHostController.navigateToTab(route: String, restoreState: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        this.restoreState = restoreState
    }
}

/** Pops [entry] only while it is still on top, so a double-tapped back arrow can't also pop the tab below. */
fun NavHostController.popSubScreen(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/** A pushed (non-tab) destination: slides in from the side and back out on pop. */
fun NavGraphBuilder.subScreen(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        enterTransition = { MacroMotion.subScreenEnter },
        exitTransition = { MacroMotion.subScreenExit },
        popEnterTransition = { MacroMotion.subScreenPopEnter },
        popExitTransition = { MacroMotion.subScreenPopExit },
        content = content,
    )
}
