package com.macrotracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.macrotracker.data.hermes.HermesNotifier
import com.macrotracker.data.server.ServerIntentRequest
import com.macrotracker.data.server.ServerNotifier
import com.macrotracker.ui.screens.MainScreen
import com.macrotracker.ui.theme.DailyDashTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Set when a server notification is tapped. The activity is `singleTask`,
     * so a tap while the app is already open arrives through [onNewIntent]
     * rather than a fresh [onCreate] — both paths funnel through here.
     */
    private val _serverRequest = MutableStateFlow<ServerIntentRequest?>(null)
    private val serverRequest: StateFlow<ServerIntentRequest?> = _serverRequest

    /** A tapped Hermes notification: the chat to open, or "" for Tech support as it was. */
    private val _hermesRequest = MutableStateFlow<String?>(null)
    private val hermesRequest: StateFlow<String?> = _hermesRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch away from the splash theme before Compose draws its first frame
        setTheme(R.style.Theme_DailyDash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleServerIntent(intent)
        handleHermesIntent(intent)
        setContent {
            DailyDashTheme {
                MainScreen(
                    serverRequest = serverRequest,
                    onServerRequestHandled = { _serverRequest.value = null },
                    hermesRequest = hermesRequest,
                    onHermesRequestHandled = { _hermesRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleServerIntent(intent)
        handleHermesIntent(intent)
    }

    private fun handleHermesIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(HermesNotifier.EXTRA_OPEN_HERMES, false) != true) return
        _hermesRequest.value = intent.getStringExtra(HermesNotifier.EXTRA_THREAD_ID).orEmpty()
        intent.removeExtra(HermesNotifier.EXTRA_OPEN_HERMES)
    }

    private fun handleServerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ServerNotifier.EXTRA_OPEN_SERVERS, false) != true) return
        _serverRequest.value = ServerIntentRequest(
            serverId = intent.getStringExtra(ServerNotifier.EXTRA_SERVER_ID),
            askAbout = intent.getStringExtra(ServerNotifier.EXTRA_ASK_ABOUT),
        )
        // Consumed: a configuration change must not replay the tap.
        intent.removeExtra(ServerNotifier.EXTRA_OPEN_SERVERS)
    }
}
