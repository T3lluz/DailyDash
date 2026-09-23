package com.macrotracker.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.macrotracker.MainActivity

/**
 * Trampoline for [PackageInstaller] commit results.
 *
 * An Activity PendingIntent stays eligible to launch
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] confirmations on OEMs that block
 * background BroadcastReceivers. On success, relaunches DailyDash so the update
 * opens immediately after install and can show What's New.
 */
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleInstallStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallStatus(intent)
    }

    private fun handleInstallStatus(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        Log.i(TAG, "Install status=$status message=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = intent.confirmationIntent()
                // The sheet in the app says "tap Install in Android's prompt" from here on,
                // so no toast: the prompt and the sheet already say it.
                UpdateInstallEvents.post(UpdateInstallEvents.Event.AwaitingUser(confirmIntent))
                if (confirmIntent != null) {
                    // Android's silent-update throttle (often ~1h) can force this path
                    // even for same-package self-updates.
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(confirmIntent) }
                        .onSuccess { Log.i(TAG, "Opened install confirmation UI") }
                        .onFailure {
                            Log.e(TAG, "Failed to open install confirmation", it)
                            UpdateInstallEvents.post(
                                UpdateInstallEvents.Event.Failed(status, "Android's install prompt would not open"),
                            )
                        }
                } else {
                    Log.e(TAG, "PENDING_USER_ACTION without confirmation intent")
                    UpdateInstallEvents.post(
                        UpdateInstallEvents.Event.Failed(status, "Android asked to confirm but sent no prompt"),
                    )
                }
                finish()
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update installed successfully — relaunching DailyDash")
                UpdateInstallEvents.post(UpdateInstallEvents.Event.Success)
                relaunchApp()
                // Delay finish so the launch intent is delivered before we tear down.
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 250L)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i(TAG, "Update install cancelled from Android's prompt")
                UpdateInstallEvents.post(UpdateInstallEvents.Event.Aborted)
                finish()
            }
            else -> {
                Log.e(TAG, "Update install failed status=$status message=$message")
                UpdateInstallEvents.post(UpdateInstallEvents.Event.Failed(status, failureText(status, message)))
                finish()
            }
        }
    }

    private fun relaunchApp() {
        // Explicit activity class — more reliable than getLaunchIntentForPackage for
        // carrying extras through OEM launchers after a package replace.
        val launch = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            putExtra(EXTRA_RELAUNCHED_AFTER_UPDATE, true)
            putExtra(EXTRA_SHOW_WHATS_NEW, true)
        }
        runCatching { startActivity(launch) }
            .onFailure { Log.e(TAG, "Failed to relaunch after update", it) }
    }

    /** PackageInstaller's messages are for developers; say what happened instead. */
    private fun failureText(status: Int, message: String): String = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "It clashes with the installed app"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This phone cannot run that build"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The download was not a valid app"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install it"
        else -> message.ifBlank { "Android reported status $status" }
    }

    private fun Intent.confirmationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }
    }

    companion object {
        const val ACTION_INSTALL_COMPLETE = "com.macrotracker.action.UPDATE_INSTALL_COMPLETE"
        const val EXTRA_RELAUNCHED_AFTER_UPDATE = "relaunched_after_update"
        const val EXTRA_SHOW_WHATS_NEW = "show_whats_new"
        private const val TAG = "UpdateInstall"
    }
}
