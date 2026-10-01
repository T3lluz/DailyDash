package com.macrotracker.data

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Whether the app may post notifications: always below Android 13, only once granted from 13 on. */
fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Posts [notification] when the app may; a refused permission or a system error drops it quietly. */
fun NotificationManagerCompat.notifyIfAllowed(context: Context, id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    runCatching { notify(id, notification) }
}
