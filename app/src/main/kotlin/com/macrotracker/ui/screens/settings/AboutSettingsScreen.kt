package com.macrotracker.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.update.AppReleaseNotes
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.theme.AppIcons

@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val updateState by appUpdateViewModel.state.collectAsState()
    val releaseNotes by appUpdateViewModel.releaseNotes.collectAsState()
    val releaseNotesLoading by appUpdateViewModel.releaseNotesLoading.collectAsState()
    var showAllReleaseNotes by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        appUpdateViewModel.loadReleaseNotes()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .subScreenBottomPadding(),
    ) {
        SubScreenHeader(
            title = "About",
            subtitle = "Version, updates, and release notes",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        MacroCard(delayMs = 50) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Icon(
                    AppIcons.Download,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Update",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Installed ${appUpdateViewModel.currentVersionName} (build ${appUpdateViewModel.currentVersionCode})",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }

            when (val s = updateState) {
                is AppUpdateUiState.Idle -> {
                    Text(
                        text = "Checks GitHub Releases automatically, installs in-app, then reopens with What's new.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Checking -> {
                    Text(
                        text = "Checking for updates…",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.UpToDate -> {
                    Text(
                        text = "You're on the latest build.",
                        fontSize = 12.sp,
                        color = Success,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Available -> {
                    Text(
                        text = "Update available: ${s.info.versionName} (build ${s.info.versionCode})",
                        fontSize = 12.sp,
                        color = Primary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Downloading -> {
                    Text(
                        text = "Downloading ${s.info.versionName}… ${(s.progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        color = Primary,
                    )
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    Text(
                        text = "Ready to install ${s.info.versionName}",
                        fontSize = 12.sp,
                        color = Success,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Error -> {
                    Text(
                        text = s.message,
                        fontSize = 12.sp,
                        color = Error,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }

            when (val s = updateState) {
                is AppUpdateUiState.Available -> {
                    MacroButton(
                        text = "Update to ${s.info.versionName}",
                        onClick = {
                            haptics.confirm()
                            if (appUpdateViewModel.canInstallPackages()) {
                                appUpdateViewModel.startDownload(s.info)
                            } else {
                                context.startActivity(appUpdateViewModel.installPermissionSettingsIntent())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    MacroButton(
                        text = "Install update",
                        onClick = {
                            haptics.confirm()
                            appUpdateViewModel.installDownloaded()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is AppUpdateUiState.Downloading -> {
                    MacroButton(
                        text = "Downloading…",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    MacroButton(
                        text = if (updateState is AppUpdateUiState.Checking) "Checking…" else "Check for updates",
                        onClick = {
                            haptics.click()
                            appUpdateViewModel.checkFromSettings()
                        },
                        enabled = updateState !is AppUpdateUiState.Checking,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Release notes",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when {
                releaseNotesLoading && releaseNotes.isEmpty() -> {
                    Text(
                        text = "Loading release history…",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                releaseNotes.isEmpty() -> {
                    Text(
                        text = "No published releases found yet.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                else -> {
                    val visibleNotes = if (showAllReleaseNotes) {
                        releaseNotes
                    } else {
                        releaseNotes.take(3)
                    }
                    visibleNotes.forEach { release ->
                        ReleaseNotesDropdown(
                            release = release,
                            isCurrent = release.versionCode == appUpdateViewModel.currentVersionCode,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (releaseNotes.size > 3) {
                        MacroButton(
                            text = if (showAllReleaseNotes) {
                                "Show fewer updates"
                            } else {
                                "Show ${releaseNotes.size - 3} more updates"
                            },
                            onClick = {
                                haptics.click()
                                showAllReleaseNotes = !showAllReleaseNotes
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesDropdown(
    release: AppReleaseNotes,
    isCurrent: Boolean,
) {
    var expanded by remember(release.tagName) { mutableStateOf(isCurrent) }
    val haptics = rememberHaptics()
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(14.dp)
    val dateLabel = release.publishedAt?.take(10).orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Background)
            .border(1.dp, Border.copy(alpha = 0.55f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.click()
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.versionName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (release.isNewerThanInstalled) Primary else TextPrimary,
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Installed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Success,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Success.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else if (release.isNewerThanInstalled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("Build ${release.versionCode}")
                        if (dateLabel.isNotEmpty()) append(" · $dateLabel")
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            ) {
                HorizontalDivider(
                    color = Border.copy(alpha = 0.45f),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                MarkdownText(
                    markdown = release.releaseNotes.ifBlank { "No release notes." },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                if (release.htmlUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.click()
                                runCatching { uriHandler.openUri(release.htmlUrl) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = AppIcons.Link,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open on GitHub",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                        )
                    }
                }
            }
        }
    }
}
