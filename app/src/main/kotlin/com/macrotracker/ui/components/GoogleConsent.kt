package com.macrotracker.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.macrotracker.data.google.GoogleScope
import com.macrotracker.data.youtube.AuthorizeOutcome
import com.macrotracker.ui.util.findActivity
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.launch

/**
 * Asks Google for read access ([GoogleScope]s) from a screen: silently when it was allowed
 * before, through Google's own sheet when it needs the person. [onDone] gets null when it
 * worked, or what went wrong.
 */
@Composable
fun rememberGoogleConsent(onDone: (error: String?) -> Unit): (Set<GoogleScope>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val access = remember { context.widgetEntryPoint().googleAccess() }
    val done by rememberUpdatedState(onDone)
    var asking by remember { mutableStateOf<Set<GoogleScope>>(emptySet()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val activity = context.findActivity()
        scope.launch {
            // Parse even a cancelled result: setup errors come back as RESULT_CANCELED with the reason inside.
            when (val o = access.complete(activity, result.data, asking)) {
                is AuthorizeOutcome.Ready -> done(null)
                is AuthorizeOutcome.Failed -> done(o.message)
                is AuthorizeOutcome.NeedsConsent -> done("Google sign-in didn't finish. Try again.")
            }
        }
    }
    return { scopes ->
        asking = scopes
        scope.launch {
            when (val o = access.request(context.findActivity(), scopes)) {
                is AuthorizeOutcome.Ready -> done(null)
                is AuthorizeOutcome.NeedsConsent -> launcher.launch(IntentSenderRequest.Builder(o.pendingIntent.intentSender).build())
                is AuthorizeOutcome.Failed -> done(o.message)
            }
        }
    }
}
