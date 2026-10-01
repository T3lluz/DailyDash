package com.macrotracker.ui.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/** The activity behind a Compose context, through any wrappers a dialog or theme put round it. */
tailrec fun Context.findActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity behind $this")
}
