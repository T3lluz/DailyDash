package com.macrotracker.ui.screens.settings

import com.macrotracker.ui.screens.home.pageRise
import com.macrotracker.ui.screens.home.RoomNeutral
import com.macrotracker.ui.components.JoinedColumn
import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.screens.home.SubPage
import com.macrotracker.ui.util.LoadWhenSeen
import com.macrotracker.ui.util.findActivity
import com.macrotracker.data.update.prettyVersion
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
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.macrotracker.data.update.info
import com.macrotracker.data.update.updateAvailable
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.UpdateProgressBar
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
    onReplayIntro: () -> Unit = {},
) {
    val activity = LocalContext.current.findActivity()
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val updateState by appUpdateViewModel.state.collectAsState()
    val releaseNotes by appUpdateViewModel.releaseNotes.collectAsState()
    val releaseNotesLoading by appUpdateViewModel.releaseNotesLoading.collectAsState()
    val notifyEnabled by appUpdateViewModel.notifyEnabled.collectAsState()
    var showAllReleaseNotes by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        appUpdateViewModel.loadReleaseNotes()
    }

    SubPage(title = "About", lede = "Version, updates, release notes and the intro", onBack = onNavigateBack) {

        MacroCard(delayMs = 50) {
            CardHeader(
                title = "App update",
                icon = AppIcons.Download,
                accent = Primary,
                subtitle = "Installed ${appUpdateViewModel.currentVersionName} (build ${appUpdateViewModel.currentVersionCode})",
                modifier = Modifier.padding(bottom = 10.dp),
            )

            // One line for where the update is; the sheet has the rest and opens from the button.
            val (line, lineColor) = when (val s = updateState) {
                is AppUpdateUiState.Idle ->
                    "Checks GitHub Releases, installs in-app, then reopens with What's new." to TextSecondary
                is AppUpdateUiState.Checking -> "Checking for updates…" to TextSecondary
                is AppUpdateUiState.UpToDate -> "You're on the latest build." to Success
                is AppUpdateUiState.Available ->
                    "Update available: ${prettyVersion(s.info.versionName)} (build ${s.info.versionCode})" to Primary
                is AppUpdateUiState.NeedsPermission ->
                    "${prettyVersion(s.info.versionName)} is ready. Android needs to allow installs from DailyDash first." to Primary
                is AppUpdateUiState.Downloading ->
                    "Downloading ${prettyVersion(s.info.versionName)}…${s.progress?.let { " ${(it * 100).toInt()}%" } ?: ""}" to TextSecondary
                is AppUpdateUiState.Installing ->
                    (if (s.awaitingConfirmation) "Tap Install in Android's prompt" else "Installing ${prettyVersion(s.info.versionName)}…") to TextSecondary
                is AppUpdateUiState.ReadyToInstall ->
                    (s.note ?: "${prettyVersion(s.info.versionName)} is downloaded and ready to install") to Success
                is AppUpdateUiState.Error -> s.message to Error
            }
            Text(
                text = line,
                fontSize = 12.sp,
                color = lineColor,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when (val s = updateState) {
                // The same bar as the update sheet; an unknown size sweeps instead of sitting at zero.
                is AppUpdateUiState.Downloading -> UpdateProgressBar(s.progress, Modifier.padding(bottom = 10.dp))
                is AppUpdateUiState.Installing -> UpdateProgressBar(null, Modifier.padding(bottom = 10.dp))
                else -> Unit
            }

            if (updateState.updateAvailable) {
                MacroButton(
                    text = when (val s = updateState) {
                        is AppUpdateUiState.Downloading, is AppUpdateUiState.Installing -> "Show progress"
                        is AppUpdateUiState.ReadyToInstall -> "Install update"
                        is AppUpdateUiState.Error -> "Try again"
                        else -> "Update to ${prettyVersion(s.info?.versionName.orEmpty())}"
                    },
                    onClick = {
                        when (val s = updateState) {
                            is AppUpdateUiState.ReadyToInstall -> appUpdateViewModel.retryInstall()
                            is AppUpdateUiState.Available, is AppUpdateUiState.NeedsPermission, is AppUpdateUiState.Error ->
                                s.info?.let { info ->
                                    appUpdateViewModel.update(info)?.let { runCatching { context.startActivity(it) } }
                                }
                            else -> Unit
                        }
                        appUpdateViewModel.openDialog()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MacroButton(
                    text = if (updateState is AppUpdateUiState.Checking) "Checking…" else "Check for updates",
                    onClick = { appUpdateViewModel.checkFromSettings() },
                    enabled = updateState !is AppUpdateUiState.Checking,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.SECONDARY,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            JoinedColumn(outer = 18.dp) {
                MetricToggleRow(
                    name = "Tell me when a build is out",
                    enabled = notifyEnabled,
                    icon = AppIcons.Bell,
                    onCheckedChange = { enabled ->
                        haptics.tick()
                        appUpdateViewModel.setNotifyEnabled(enabled)
                    },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Release notes",
                style = HealthDisplay,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
            )
            when {
                releaseNotesLoading && releaseNotes.isEmpty() -> {
                    // First load: a placeholder shaped like the list, as the cards do.
                    ContentSkeleton(lines = 3, accent = Border)
                }
                releaseNotes.isEmpty() -> {
                    // A failed fetch lands here too, so offer another go.
                    Text(
                        text = "No release notes to show. They come from GitHub Releases.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    MacroButton(
                        text = "Try again",
                        onClick = { appUpdateViewModel.loadReleaseNotes(force = true) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
                else -> {
                    // every release is a tile of one group; the older ones join it when asked for
                    val shown = if (showAllReleaseNotes) releaseNotes else releaseNotes.take(3)
                    JoinedColumn(outer = 18.dp) {
                        shown.forEach { release ->
                            androidx.compose.runtime.key(release.tagName) {
                                ReleaseNotesDropdown(
                                    release = release,
                                    isCurrent = release.versionCode == appUpdateViewModel.currentVersionCode,
                                )
                            }
                        }
                    }
                    if (releaseNotes.size > 3) {
                        Spacer(modifier = Modifier.height(10.dp))
                        MacroButton(
                            text = if (showAllReleaseNotes) {
                                "Show fewer updates"
                            } else {
                                "Show ${releaseNotes.size - 3} more updates"
                            },
                            onClick = { showAllReleaseNotes = !showAllReleaseNotes },
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        }

        JoinedColumn(modifier = Modifier.pageRise().padding(vertical = 7.dp), fill = RoomNeutral, outer = Joined.Page) {
            SettingsNavRow(
                icon = AppIcons.GraduationCap,
                tint = Primary,
                title = "Replay the intro",
                summary = "The welcome, permissions and a quick tour of the tabs",
                onClick = onReplayIntro,
            )
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

    // a tile of the release list: the group gives it its fill and corners
    Column(modifier = Modifier.fillMaxWidth()) {
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
                        text = prettyVersion(release.versionName), style = HealthDisplay,
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
                                .clip(RoundedCornerShape(10.dp))
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
                                .clip(RoundedCornerShape(10.dp))
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
                Spacer(Modifier.height(8.dp))
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
                            .clip(RoundedCornerShape(14.dp))
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
