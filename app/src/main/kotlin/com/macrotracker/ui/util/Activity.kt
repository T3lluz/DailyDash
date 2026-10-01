package com.macrotracker.ui.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit

/** The activity behind a Compose context, through any wrappers a dialog or theme put round it. */
tailrec fun Context.findActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity behind $this")
}

/**
 * Asks for [permission] through [launch]; once Android stops showing its prompt ("Don't ask
 * again", or refused twice) the button would do nothing, so it opens the app's settings page,
 * where the permission can still be allowed.
 */
fun Context.requestOrOpenSettings(permission: String, launch: () -> Unit) {
    val asked = getSharedPreferences(PERMISSION_ASKS, Context.MODE_PRIVATE)
    val blocked = asked.getBoolean(permission, false) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(findActivity(), permission)
    if (blocked) {
        openAppSettings()
        return
    }
    asked.edit { putBoolean(permission, true) }
    launch()
}

/** The app's page in Android's settings: permissions, notifications, storage. */
fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private const val PERMISSION_ASKS = "permission_asks"

/**
 * Opens [url] in whatever app handles it. Quietly does nothing for a blank link or when no
 * app can open it, where startActivity would throw and take the screen down.
 */
fun Context.openUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }.isSuccess
}
