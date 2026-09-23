package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.update.AppUpdateInfo
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface as AppSurface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import java.util.Locale
import com.macrotracker.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    currentVersionName: String,
    onDismiss: () -> Unit,
    onUpdate: (AppUpdateInfo) -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    needsInstallPermission: Boolean,
) {
    val info = when (state) {
        is AppUpdateUiState.Available -> state.info
        is AppUpdateUiState.Downloading -> state.info
        is AppUpdateUiState.ReadyToInstall -> state.info
        else -> return
    }
    val haptics = rememberHaptics()
    val downloading = state is AppUpdateUiState.Downloading
    val ready = state is AppUpdateUiState.ReadyToInstall
    val progress = (state as? AppUpdateUiState.Downloading)?.progress ?: 0f
    val sizeLabel = formatApkSize(info.apkBytes)

    BasicAlertDialog(
        onDismissRequest = {
            if (!downloading) onDismiss()
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = AppSurface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Download,
                            contentDescription = null,
                            tint = Primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Update available",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    if (!downloading) {
                        IconButton(onClick = {
                            haptics.click()
                            onDismiss()
                        }) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("DailyDash ${info.versionName} · build ${info.versionCode}")
                        if (sizeLabel != null) append(" · $sizeLabel")
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                )
                Text(
                    text = "Installed $currentVersionName · opens automatically after install",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(14.dp))
                MarkdownText(
                    markdown = info.releaseNotes.ifBlank { "Bug fixes and improvements." },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )

                if (downloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloading… ${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }

                if (ready) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Installing… DailyDash will reopen when it's done. " +
                            "If Android asks once, tap Install (silent updates are limited about once an hour).",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }

                if (needsInstallPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Allow DailyDash to install updates, then return here — download resumes automatically.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    MacroButton(
                        text = "Allow installs",
                        onClick = {
                            haptics.click()
                            onOpenInstallPermission()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                when {
                    ready -> MacroButton(
                        text = "Install update",
                        onClick = {
                            haptics.confirm()
                            onInstall()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !needsInstallPermission,
                    )
                    downloading -> MacroButton(
                        text = "Downloading…",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    )
                    else -> MacroButton(
                        text = if (needsInstallPermission) {
                            "Update (after allowing installs)"
                        } else {
                            "Update now"
                        },
                        onClick = {
                            haptics.confirm()
                            onUpdate(info)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !needsInstallPermission,
                    )
                }

                if (!downloading) {
                    MacroButton(
                        text = "Remind me later",
                        onClick = {
                            haptics.click()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }
        }
    }
}

private fun formatApkSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.0f MB", mb)
}
