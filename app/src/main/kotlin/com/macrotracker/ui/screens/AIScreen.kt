package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.screens.ai.HermesChatPane
import com.macrotracker.ui.screens.ai.SysopChatPane
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.viewmodel.ChatViewModel
import com.macrotracker.ui.viewmodel.HermesViewModel

/**
 * The AI tab: Tech support, which is Hermes on the dashboard server when he answers and the
 * phone's own Sysop when he doesn't. One pane, its own one-row header, nothing above it.
 *
 * Estimating a meal used to share the tab behind a switcher; it lives with Food now
 * ([MealChatScreen]), where the log is.
 */
@Composable
fun AIScreen(
    onNavigateToAiSettings: () -> Unit,
    onOpenConsole: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
    serverHandoffId: String? = null,
    chatViewModel: ChatViewModel = hiltViewModel(),
    hermesViewModel: HermesViewModel = hiltViewModel(),
) {
    val hermesChosen by hermesViewModel.usesHermes.collectAsState()
    // A hand-off Hermes could not take goes to Sysop, and Sysop is then what is on screen.
    var sysopForHandoff by rememberSaveable { mutableStateOf(false) }
    val usesHermes = hermesChosen && !sysopForHandoff

    // A hand-off from a server card opens a thread on it. Hermes gets it when it can be
    // reached, since it can look at the server itself; otherwise Sysop.
    LaunchedEffect(serverHandoffId) {
        if (serverHandoffId != null) {
            if (hermesViewModel.usesHermes.value && hermesViewModel.awaitReady()) {
                sysopForHandoff = false
                hermesViewModel.openServerHandoff(serverHandoffId)
            } else {
                sysopForHandoff = hermesViewModel.usesHermes.value
                chatViewModel.openServerHandoff(serverHandoffId)
            }
        }
    }

    // The navbar's Hermes tab or a Hermes notification was tapped: that chat.
    val hermesOpenRequest by hermesViewModel.openRequest.collectAsState()
    LaunchedEffect(hermesOpenRequest) {
        val threadId = hermesOpenRequest ?: return@LaunchedEffect
        sysopForHandoff = false
        hermesViewModel.openRequested(threadId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(top = 8.dp),
    ) {
        if (usesHermes) {
            HermesChatPane(
                viewModel = hermesViewModel,
                onOpenUsage = onOpenUsage,
                onOpenConsole = onOpenConsole,
                onUsePhoneAi = { hermesViewModel.setUsesHermes(false) },
            )
        } else {
            SysopChatPane(
                viewModel = chatViewModel,
                onNavigateToAiSettings = onNavigateToAiSettings,
                onOpenConsole = onOpenConsole,
                onUseHermes = {
                    sysopForHandoff = false
                    hermesViewModel.setUsesHermes(true)
                },
            )
        }
    }
}
