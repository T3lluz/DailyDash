package com.macrotracker.ui.screens

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.BuildConfig
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.data.update.UpdateInstallActivity
import com.macrotracker.ui.components.AppUpdateSheet
import com.macrotracker.data.update.AppUpdateNotifier
import com.macrotracker.data.update.updateAvailable
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.components.WhatsNewDialog
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.navigation.SettingsRoutes
import com.macrotracker.ui.navigation.navigateToTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.macrotracker.ui.screens.onboarding.SplashOverlay
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import com.macrotracker.ui.viewmodel.ServerViewModel
import com.macrotracker.data.server.ServerIntentRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    serverRequest: StateFlow<ServerIntentRequest?>? = null,
    onServerRequestHandled: () -> Unit = {},
) {
    val activity = LocalContext.current as ComponentActivity
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val splashShown by onboardingViewModel.splashShown.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val openAiApiKey by settingsViewModel.openAiApiKey.collectAsState()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsState()
    val anthropicApiKey by settingsViewModel.anthropicApiKey.collectAsState()
    val claudeConnected by settingsViewModel.claudeConnected.collectAsState()
    // Align with AiCredentialResolver: Claude subscription, then Settings key, then BuildConfig.
    val hasAiApiKey = when (aiProvider) {
        AiProvider.GEMINI ->
            geminiApiKey.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank()
        AiProvider.OPENAI ->
            openAiApiKey.isNotBlank() || BuildConfig.OPENAI_API_KEY.isNotBlank()
        AiProvider.OPENROUTER ->
            openRouterApiKey.isNotBlank() || BuildConfig.OPENROUTER_API_KEY.isNotBlank()
        AiProvider.ANTHROPIC ->
            claudeConnected ||
                anthropicApiKey.isNotBlank() ||
                BuildConfig.ANTHROPIC_API_KEY.isNotBlank()
    }

    val updateState by appUpdateViewModel.state.collectAsState()
    val showUpdateDialog by appUpdateViewModel.showDialog.collectAsState()
    val updateAvailable by appUpdateViewModel.updateAvailable.collectAsState()
    val whatsNew by appUpdateViewModel.whatsNew.collectAsState()
    val context = LocalContext.current

    fun consumePostUpdateIntent(): Boolean {
        val intent = activity.intent ?: return false
        val force =
            intent.getBooleanExtra(UpdateInstallActivity.EXTRA_RELAUNCHED_AFTER_UPDATE, false) ||
                intent.getBooleanExtra(UpdateInstallActivity.EXTRA_SHOW_WHATS_NEW, false)
        if (!force) return false
        intent.removeExtra(UpdateInstallActivity.EXTRA_RELAUNCHED_AFTER_UPDATE)
        intent.removeExtra(UpdateInstallActivity.EXTRA_SHOW_WHATS_NEW)
        return true
    }

    /** The update notification: its tap opens the sheet, its Update action starts too. */
    fun consumeUpdateIntent() {
        val intent = activity.intent ?: return
        val start = intent.getBooleanExtra(AppUpdateNotifier.EXTRA_START_UPDATE, false)
        val show = start || intent.getBooleanExtra(AppUpdateNotifier.EXTRA_SHOW_UPDATE, false)
        if (!show) return
        intent.removeExtra(AppUpdateNotifier.EXTRA_START_UPDATE)
        intent.removeExtra(AppUpdateNotifier.EXTRA_SHOW_UPDATE)
        appUpdateViewModel.openFromNotification(start)
    }

    // Skip splash after an in-app update / version bump so What's New feels instant.
    LaunchedEffect(Unit) {
        consumeUpdateIntent()
        val force = consumePostUpdateIntent()
        val showWhatsNew = appUpdateViewModel.willShowWhatsNew(force)
        if (showWhatsNew && !splashShown) {
            onboardingViewModel.markSplashShown()
        }
        appUpdateViewModel.handlePostUpdateLaunch(forceFromIntent = force)
    }

    val startDestination = remember(onboardingCompleted) {
        if (onboardingCompleted) Screen.Home.route else OnboardingRoutes.WELCOME
    }

    val navController = rememberNavController()
    val items = remember(hasAiApiKey) {
        if (hasAiApiKey) {
            listOf(Screen.Home, Screen.Health, Screen.AI, Screen.Settings)
        } else {
            listOf(Screen.Home, Screen.Health, Screen.Settings)
        }
    }

    // Tapping a server notification lands on that server's dashboard; its Ask action
    // opens Tech support on the problem instead, when there is an AI to ask.
    val serverViewModel: ServerViewModel = hiltViewModel()
    val pendingServerRequest by (serverRequest ?: remember { MutableStateFlow(null) }).collectAsState()
    LaunchedEffect(pendingServerRequest, onboardingCompleted) {
        val request = pendingServerRequest ?: return@LaunchedEffect
        if (!onboardingCompleted) return@LaunchedEffect
        val seed = request.askAbout?.takeIf { hasAiApiKey }?.let { about ->
            serverViewModel.askAiFromNotification(request.serverId, about)
        }
        if (seed != null) {
            navController.navigateToTab(Screen.AI.withSeed(seed), restoreState = false)
        } else {
            serverViewModel.focusServer(request.serverId)
            navController.navigate(SettingsRoutes.SERVER_DASHBOARD) {
                launchSingleTop = true
            }
        }
        onServerRequestHandled()
    }

    val onOnboardingComplete = remember(onboardingViewModel) {
        { onboardingViewModel.completeOnboarding() }
    }
    val onSplashFinished = remember(onboardingViewModel) {
        { onboardingViewModel.markSplashShown() }
    }

    // After splash (and only once onboarding is done), start listening for GitHub Releases.
    LaunchedEffect(splashShown, onboardingCompleted) {
        if (splashShown && onboardingCompleted) {
            appUpdateViewModel.startListening()
        }
    }

    // Re-check when returning to the foreground.
    DisposableEffect(activity, splashShown, onboardingCompleted) {
        if (!splashShown || !onboardingCompleted) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Notification tap while the process is already alive (singleTask).
                if (consumePostUpdateIntent()) {
                    onboardingViewModel.markSplashShown()
                    appUpdateViewModel.handlePostUpdateLaunch(forceFromIntent = true)
                }
                consumeUpdateIntent()
                appUpdateViewModel.checkOnResume()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenScaffold(
            navController = navController,
            items = items,
            startDestination = startDestination,
            onOnboardingComplete = onOnboardingComplete,
            showSettingsUpdateBadge = updateAvailable,
            hasAiApiKey = hasAiApiKey,
            onSettingsUpdateBadgeClick = {
                appUpdateViewModel.openDialog()
                navController.navigateToTab(Screen.Settings.route)
                navController.navigate(SettingsRoutes.ABOUT) { launchSingleTop = true }
            },
        )

        if (!splashShown) {
            SplashOverlay(onFinished = onSplashFinished)
        }

        val showWhatsNew = whatsNew
        if (showWhatsNew != null && !showUpdateDialog) {
            WhatsNewDialog(
                info = showWhatsNew,
                onDismiss = { appUpdateViewModel.dismissWhatsNew() },
            )
        }

        if (showUpdateDialog && updateState.updateAvailable) {
            AppUpdateSheet(
                state = updateState,
                currentVersionName = appUpdateViewModel.currentVersionName,
                onDismiss = { appUpdateViewModel.dismissDialog(snooze = true) },
                onUpdate = { info ->
                    appUpdateViewModel.update(info)?.let { permission ->
                        runCatching { context.startActivity(permission) }
                    }
                },
                onCancelDownload = { appUpdateViewModel.cancelDownload() },
                onRetryInstall = { appUpdateViewModel.retryInstall() },
                onReopenPrompt = { appUpdateViewModel.reopenInstallPrompt() },
            )
        }
    }
}

@Composable
private fun MainScreenScaffold(
    navController: NavHostController,
    items: List<Screen>,
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    showSettingsUpdateBadge: Boolean,
    hasAiApiKey: Boolean,
    onSettingsUpdateBadgeClick: () -> Unit,
) {
    val navHostModifier = remember { Modifier.statusBarsPadding() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // Strip query args: the AI destination is declared as "ai?tab=…&seed=…".
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore('?')
    val isMainTabRoute = items.any { it.route == currentRoute }
    val context = LocalContext.current

    // If the AI key is cleared while parked on AI, leave that route so the
    // bottom bar does not vanish with no selected tab.
    LaunchedEffect(hasAiApiKey, currentRoute) {
        if (!hasAiApiKey && currentRoute == Screen.AI.route) {
            navController.navigateToTab(Screen.Home.route)
        }
    }

    // Predictive back on root tabs: collect progress so the system gesture
    // can animate, then finish only if the gesture completes.
    PredictiveBackHandler(enabled = isMainTabRoute) { progress ->
        try {
            progress.collect { /* system drives the predictive animation */ }
            (context as? Activity)?.finish()
        } catch (_: CancellationException) {
            // Gesture cancelled — stay on the current tab.
        }
    }

    val hazeState = rememberHazeState()

    // Overlay the frosted pill on top of content so scrolling content
    // can blur through the bar (hazeSource + hazeEffect).
    Box(modifier = Modifier.fillMaxSize()) {
        DailyDashNavHost(
            navController = navController,
            modifier = navHostModifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            startDestination = startDestination,
            onOnboardingComplete = onOnboardingComplete,
            aiAvailable = hasAiApiKey,
        )
        MainBottomBar(
            navController = navController,
            items = items,
            showSettingsUpdateBadge = showSettingsUpdateBadge,
            onSettingsUpdateBadgeClick = onSettingsUpdateBadgeClick,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MainBottomBar(
    navController: NavHostController,
    items: List<Screen>,
    showSettingsUpdateBadge: Boolean,
    onSettingsUpdateBadgeClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore('?')
    if (!items.any { it.route == currentRoute }) return

    val onItemClick = remember(navController, showSettingsUpdateBadge, onSettingsUpdateBadgeClick) {
        { screen: Screen ->
            if (screen is Screen.Settings && showSettingsUpdateBadge) {
                onSettingsUpdateBadgeClick()
            } else {
                navController.navigateToTab(screen.route)
            }
        }
    }

    Box(modifier = modifier.navigationBarsPadding()) {
        PillNavigationBar(
            items = items,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            showSettingsUpdateBadge = showSettingsUpdateBadge,
            hazeState = hazeState,
        )
    }
}
