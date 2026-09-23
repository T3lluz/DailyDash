package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.update.AppUpdateInfo
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.data.update.info
import com.macrotracker.data.update.inProgress
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import java.util.Locale

/**
 * The update, as one sheet that follows it from offer to reinstall, after Essentials'
 * updater: the notes, then the one action that makes sense right now in the same place.
 * Update turns into the download's bar the moment it is tapped, the bar into "installing",
 * and that into "tap Install in Android's prompt" when Android asks. Nothing offers a
 * button that would do nothing. Closing the sheet mid-download leaves it running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSheet(
    state: AppUpdateUiState,
    currentVersionName: String,
    onDismiss: () -> Unit,
    onUpdate: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onRetryInstall: () -> Unit,
    onReopenPrompt: () -> Unit,
) {
    val info = state.info ?: return
    val haptics = rememberHaptics()
    val uriHandler = LocalUriHandler.current
    val busy = state.inProgress
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Download, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title(state),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("DailyDash ${info.versionName}")
                    formatApkSize(info.apkBytes)?.let { append(" · $it") }
                    append(" · you have $currentVersionName")
                },
                fontSize = 12.sp,
                color = TextSecondary,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "What's new",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceChrome)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                MarkdownText(
                    markdown = info.releaseNotes.ifBlank { "Bug fixes and improvements." },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { MacroMotion.widgetContentTransition },
                label = "updateStep",
            ) { step ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (step) {
                        is AppUpdateUiState.Available -> {
                            MacroButton(
                                text = "Update now",
                                onClick = { onUpdate(step.info) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MacroButton(
                                text = "Later",
                                onClick = { onDismiss() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY,
                            )
                        }
                        is AppUpdateUiState.NeedsPermission -> {
                            StepNote(
                                "Android needs to let DailyDash install updates first. Allow it on the next " +
                                    "screen and come back; the update starts by itself.",
                            )
                            MacroButton(
                                text = "Allow and update",
                                onClick = { onUpdate(step.info) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MacroButton(
                                text = "Later",
                                onClick = { onDismiss() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY,
                            )
                        }
                        is AppUpdateUiState.Downloading -> {
                            ProgressLine(
                                progress = step.progress,
                                label = buildString {
                                    append("Downloading")
                                    val total = step.totalBytes
                                    if (total != null && total > 0) {
                                        append(" · ${formatMb(step.downloadedBytes)} of ${formatMb(total)}")
                                    } else if (step.downloadedBytes > 0) {
                                        append(" · ${formatMb(step.downloadedBytes)}")
                                    }
                                },
                                trailing = step.progress?.let { "${(it * 100).toInt()}%" },
                            )
                            MacroButton(
                                text = "Cancel",
                                onClick = { onCancelDownload() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY,
                            )
                        }
                        is AppUpdateUiState.Installing -> {
                            if (step.awaitingConfirmation) {
                                ProgressLine(progress = null, label = "Waiting for you in Android's prompt")
                                StepNote("Tap Install there. DailyDash closes, updates and opens again with what's new.")
                                MacroButton(
                                    text = "Show Android's prompt",
                                    onClick = { onReopenPrompt() },
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = ButtonVariant.SECONDARY,
                                )
                            } else {
                                ProgressLine(progress = null, label = "Installing")
                                StepNote("DailyDash closes and reopens by itself. If Android asks first, tap Install.")
                            }
                        }
                        is AppUpdateUiState.ReadyToInstall -> {
                            step.note?.let { StepNote(it, color = Warning) }
                            MacroButton(
                                text = "Install update",
                                onClick = { onRetryInstall() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MacroButton(
                                text = "Later",
                                onClick = { onDismiss() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY,
                            )
                        }
                        is AppUpdateUiState.Error -> {
                            StepNote(step.message, color = Error)
                            step.info?.let { failed ->
                                MacroButton(
                                    text = "Try again",
                                    onClick = { onUpdate(failed) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MacroButton(
                                text = "Close",
                                onClick = { onDismiss() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY,
                            )
                        }
                        else -> Unit
                    }
                }
            }

            if (!busy && info.htmlUrl.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.click()
                            runCatching { uriHandler.openUri(info.htmlUrl) }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("View the release on GitHub", color = TextTertiary, fontSize = 12.sp)
                    Icon(AppIcons.ExternalLink, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

private fun title(state: AppUpdateUiState): String = when (state) {
    is AppUpdateUiState.Downloading -> "Downloading update"
    is AppUpdateUiState.Installing -> if (state.awaitingConfirmation) "Confirm the update" else "Installing update"
    is AppUpdateUiState.ReadyToInstall -> "Ready to install"
    is AppUpdateUiState.Error -> "Update didn't finish"
    else -> "Update available"
}

@Composable
private fun ProgressLine(progress: Float?, label: String, trailing: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Primary,
                trackColor = Border,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Primary,
                trackColor = Border,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun StepNote(text: String, color: androidx.compose.ui.graphics.Color = TextSecondary) {
    Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = color, modifier = Modifier.fillMaxWidth())
}

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

private fun formatApkSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.0f MB", mb)
}
