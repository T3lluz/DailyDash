package com.macrotracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.macrotracker.R
import com.macrotracker.data.github.GitHubActivity
import com.macrotracker.data.github.GitHubCommit
import com.macrotracker.data.github.GitHubIssue
import com.macrotracker.data.github.GitHubLabel
import com.macrotracker.data.github.GitHubNotification
import com.macrotracker.data.github.GitHubPullRequest
import com.macrotracker.data.github.GitHubRelease
import com.macrotracker.data.github.GitHubRepo
import com.macrotracker.data.github.GitHubRepoFocus
import com.macrotracker.data.github.GitHubSnapshot
import com.macrotracker.data.github.GitHubWorkflowRun
import com.macrotracker.data.github.compactCount
import com.macrotracker.data.github.isOpen
import com.macrotracker.data.github.lastTouchedAt
import com.macrotracker.data.github.openIssueCount
import com.macrotracker.data.github.openPrCount
import com.macrotracker.data.github.parseGitHubInstant
import com.macrotracker.data.github.repoNamed
import com.macrotracker.data.github.sortedByRecent
import com.macrotracker.data.github.statusKey
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberRelativeTime
import com.macrotracker.ui.viewmodel.GitHubAuthUiState
import com.macrotracker.ui.viewmodel.GitHubRepoFocusUiState
import com.macrotracker.ui.viewmodel.GitHubUiState
import com.macrotracker.ui.viewmodel.GitHubViewModel
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

private val GhAccent = Color(0xFF58A6FF)
private val GhOpen = Color(0xFF3FB950)
private val GhMerged = Color(0xFFA371F7)
private val GhClosed = Color(0xFFF85149)
private val GhDraft = Color(0xFF8B949E)
private val GhReview = Color(0xFFE3B341)
private val GhSurface = Surface
private val GhHairline = Border
private val GhChip = SurfaceElevated
private val Sharp = RoundedCornerShape(6.dp)
private val Pill = RoundedCornerShape(20.dp)

private enum class GhTab(val label: String) {
    ISSUES("Issues"),
    PRS("PRs"),
    INBOX("Inbox"),
    ACTIVITY("Activity"),
    REPOS("Repos"),
    ACCOUNT("Account"),
}

private data class GhHub(
    val login: String,
    val issues: List<GitHubIssue>,
    val pulls: List<GitHubPullRequest>,
    val activity: List<GitHubActivity>,
    val notifications: List<GitHubNotification>,
    val repos: List<GitHubRepo>,
    val selectedFullName: String,
    val selectedRepo: GitHubRepo?,
    val focus: GitHubRepoFocus?,
    val openIssues: Int,
    val openPrs: Int,
    val unread: Int,
    val reviews: Int,
    val notificationsNeedReconnect: Boolean,
)

private fun githubHub(
    data: GitHubSnapshot,
    focusRepo: String,
    focusState: GitHubRepoFocusUiState,
): GhHub {
    val repos = data.repos.sortedByRecent()
    val focus = (focusState as? GitHubRepoFocusUiState.Ready)?.focus
        ?.takeIf { it.repo.fullName.equals(focusRepo, ignoreCase = true) }
    val selected = focus?.repo ?: data.repoNamed(focusRepo)
    val focused = focusRepo.isNotBlank()
    val issues = if (focused) {
        focus?.issues ?: data.issues.filter { it.repoFullName.equals(focusRepo, ignoreCase = true) }
    } else {
        data.issues
    }
    val pulls = if (focused) {
        focus?.pullRequests
            ?: data.pullRequests.filter { it.repoFullName.equals(focusRepo, ignoreCase = true) }
    } else {
        data.pullRequests
    }
    val activity = if (focused) {
        focus?.activity
            ?: data.activity.filter { it.repoFullName.equals(focusRepo, ignoreCase = true) }
    } else {
        data.activity
    }
    val notifications = if (focused) {
        data.notifications.filter { it.repoFullName.equals(focusRepo, ignoreCase = true) }
    } else {
        data.notifications
    }
    return GhHub(
        login = data.user.login,
        issues = issues,
        pulls = pulls,
        activity = activity,
        notifications = notifications,
        repos = repos,
        selectedFullName = focusRepo,
        selectedRepo = selected,
        focus = focus,
        openIssues = if (focused) issues.count { it.isOpen } else data.openIssueCount(),
        openPrs = if (focused) pulls.count { it.isOpen || it.draft } else data.openPrCount(),
        unread = notifications.count { it.unread },
        reviews = if (focused) pulls.count { it.reviewRequested } else data.reviewRequestedCount,
        notificationsNeedReconnect = data.notificationsNeedReconnect,
    )
}

private enum class IssueFilter(val label: String) { ALL("All"), ASSIGNED("Assigned") }
private enum class PrFilter(val label: String) { ALL("All"), MINE("Mine"), REVIEW("Review") }

@Composable
fun GitHubCard(
    viewModel: GitHubViewModel = hiltViewModel(),
    isVisible: Boolean = true,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val focusRepo by viewModel.focusRepo.collectAsState()
    val repoFocusState by viewModel.repoFocus.collectAsState()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedTabName by rememberSaveable { mutableStateOf(GhTab.ISSUES.name) }
    val selectedTab = GhTab.entries.find { it.name == selectedTabName } ?: GhTab.ISSUES

    LaunchedEffect(Unit) {
        if (state is GitHubUiState.Idle) viewModel.loadDashboard()
    }

    val success = state as? GitHubUiState.Success
    val data = success?.data
    val hub = remember(data, focusRepo, repoFocusState) {
        data?.let { githubHub(it, focusRepo, repoFocusState) }
    }
    val headerSub = when {
        hub != null -> {
            buildString {
                if (hub.selectedRepo != null) {
                    append(hub.selectedRepo.name)
                } else {
                    append("@${hub.login}")
                }
                val issues = hub.openIssues
                val prs = hub.openPrs
                if (issues > 0 || prs > 0) {
                    append(" · ")
                    if (prs > 0) append("$prs PR${if (prs != 1) "s" else ""}")
                    if (issues > 0 && prs > 0) append(" · ")
                    if (issues > 0) append("$issues issue${if (issues != 1) "s" else ""}")
                }
            }
        }
        state is GitHubUiState.NeedsAuth -> "Connect your account"
        else -> "Your issues, PRs & activity"
    }

    MacroCard(borderColor = GhAccent.copy(alpha = 0.16f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (data?.user?.avatarUrl != null) {
                    GhAvatar(data.user.avatarUrl, data.user.login, size = 22.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_github_logo),
                        contentDescription = "GitHub",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GitHub",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.1.sp,
                    )
                    Text(
                        headerSub,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LastUpdatedText(
                    lastUpdatedAt = success?.lastUpdatedAt,
                    color = TextSecondary,
                )
                if (expanded && state !is GitHubUiState.NeedsAuth) {
                    IconButton(
                        onClick = { haptics.click(); viewModel.loadDashboard(forceRefresh = true) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(AppIcons.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tick()
                        val url = hub?.selectedRepo?.htmlUrl ?: data?.user?.htmlUrl ?: "https://github.com"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        AppIcons.ExternalLink,
                        contentDescription = "Open GitHub",
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                WidgetExpandChevron(
                    expanded = expanded,
                    onClick = {
                        expanded = !expanded
                        if (expanded) {
                            haptics.toggleOn()
                            if (state is GitHubUiState.NeedsAuth) {
                                selectedTabName = GhTab.ACCOUNT.name
                            }
                        } else {
                            haptics.toggleOff()
                        }
                    },
                    accentColor = GhAccent,
                )
            }

            if (isVisible) {
                if (!expanded) {
                    Spacer(Modifier.height(12.dp))
                    when (state) {
                        GitHubUiState.Loading, GitHubUiState.Idle -> GhLoading()
                        GitHubUiState.NeedsAuth -> {
                            if (authState.isAwaitingBrowser) {
                                GitHubDeviceCodePanel(
                                    userCode = authState.deviceLogin?.userCode,
                                    onOpenActivation = { viewModel.openActivation() },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                )
                            } else {
                                ConnectPrompt {
                                    expanded = true
                                    selectedTabName = GhTab.ACCOUNT.name
                                    haptics.toggleOn()
                                    viewModel.connectGitHub()
                                }
                            }
                        }
                        is GitHubUiState.Error -> GhError((state as GitHubUiState.Error).message)
                        is GitHubUiState.Success -> {
                            val currentHub = hub
                            if (currentHub == null) {
                                GhLoading()
                            } else {
                                GitHubCollapsedGlance(
                                    hub = currentHub,
                                    haptics = haptics,
                                    onSelectRepo = { viewModel.selectRepo(it) },
                                )
                            }
                        }
                    }
                    WidgetExpandFooter(
                        expanded = false,
                        onToggle = {
                            expanded = true
                            if (state is GitHubUiState.NeedsAuth) {
                                selectedTabName = GhTab.ACCOUNT.name
                            }
                        },
                        accentColor = GhAccent,
                        expandLabel = if (state is GitHubUiState.NeedsAuth) "Connect" else "Open hub",
                    )
                }

                WidgetExpandSection(visible = expanded && isVisible) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(14.dp))
                        val openIss = hub?.openIssues ?: 0
                        val openPrs = hub?.openPrs ?: 0
                        val unread = hub?.unread ?: 0

                        if (hub != null) {
                            RepoPicker(
                                repos = hub.repos,
                                selectedFullName = hub.selectedFullName,
                                onSelect = { haptics.tick(); viewModel.selectRepo(it) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            GhTab.entries.forEach { tab ->
                                val badge = when (tab) {
                                    GhTab.ISSUES -> openIss.takeIf { it > 0 }?.toString()
                                    GhTab.PRS -> openPrs.takeIf { it > 0 }?.toString()
                                    GhTab.INBOX -> unread.takeIf { it > 0 }?.toString()
                                    else -> null
                                }
                                val badgeColor = when (tab) {
                                    GhTab.ISSUES -> GhOpen
                                    GhTab.PRS -> GhMerged
                                    GhTab.INBOX -> GhReview
                                    GhTab.ACTIVITY -> GhReview
                                    else -> GhAccent
                                }
                                GhTabChip(
                                    label = tab.label,
                                    badge = badge,
                                    badgeColor = badgeColor,
                                    active = selectedTab == tab,
                                    onClick = {
                                        haptics.tick()
                                        selectedTabName = tab.name
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GhHairline, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        WidgetStateSwitch(
                            targetState = when (state) {
                                GitHubUiState.Idle, GitHubUiState.Loading -> 0
                                is GitHubUiState.Error -> 1
                                GitHubUiState.NeedsAuth -> 2
                                is GitHubUiState.Success -> 3
                            },
                            label = "ghState",
                        ) { phase ->
                            when (phase) {
                                0 -> GhLoading()
                                1 -> GhError(
                                    (state as? GitHubUiState.Error)?.message
                                        ?: "Couldn’t load GitHub data",
                                )
                                2 -> AccountTab(
                                    snapshot = null,
                                    authState = authState,
                                    onConnect = { viewModel.connectGitHub() },
                                    onDisconnect = { viewModel.disconnect() },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                    onOpenActivation = { viewModel.openActivation() },
                                    haptics = haptics,
                                )
                                else -> {
                                    val currentHub = hub
                                    if (currentHub == null) {
                                        GhLoading()
                                    } else {
                                        AnimatedContent(
                                            targetState = selectedTab,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clipToBounds(),
                                            transitionSpec = {
                                                MacroMotion.inCardTabSwitch(
                                                    toRight = targetState.ordinal > initialState.ordinal,
                                                )
                                            },
                                            label = "ghTab",
                                        ) { tab ->
                                            when (tab) {
                                                GhTab.ISSUES -> IssuesTab(
                                                    issues = currentHub.issues,
                                                    focusedRepo = currentHub.selectedRepo?.name,
                                                    haptics = haptics,
                                                )
                                                GhTab.PRS -> PullsTab(
                                                    pulls = currentHub.pulls,
                                                    focusedRepo = currentHub.selectedRepo?.name,
                                                    haptics = haptics,
                                                )
                                                GhTab.INBOX -> InboxTab(
                                                    notifications = currentHub.notifications,
                                                    needReconnect = currentHub.notificationsNeedReconnect,
                                                    haptics = haptics,
                                                )
                                                GhTab.ACTIVITY -> ActivityTab(currentHub.activity, haptics)
                                                GhTab.REPOS -> ReposTab(
                                                    repos = currentHub.repos,
                                                    selectedFullName = currentHub.selectedFullName,
                                                    focus = currentHub.focus,
                                                    focusLoading = repoFocusState is GitHubRepoFocusUiState.Loading,
                                                    onSelectRepo = { viewModel.selectRepo(it) },
                                                    haptics = haptics,
                                                )
                                                GhTab.ACCOUNT -> AccountTab(
                                                    snapshot = data,
                                                    authState = authState,
                                                    onConnect = { viewModel.connectGitHub() },
                                                    onDisconnect = { viewModel.disconnect() },
                                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                                    onOpenActivation = { viewModel.openActivation() },
                                                    haptics = haptics,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WidgetExpandFooter(
                            expanded = true,
                            onToggle = { expanded = false },
                            accentColor = GhAccent,
                            collapseLabel = "Show less",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GhTabChip(
    label: String,
    badge: String?,
    badgeColor: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        if (active) TextPrimary else TextTertiary,
        MacroMotion.colorTween(160),
        label = "ghTabFg",
    )
    val underline by animateColorAsState(
        if (active) GhAccent else Color.Transparent,
        MacroMotion.colorTween(160),
        label = "ghTabLine",
    )
    val underlineW by animateDpAsState(
        if (active) 18.dp else 0.dp,
        MacroMotion.pressSpring(),
        label = "ghTabW",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(Sharp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                color = fg,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(Pill)
                        .background(badgeColor.copy(alpha = if (active) 0.22f else 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(underlineW)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(underline),
        )
    }
}

@Composable
private fun GhLoading() {
    Box(
        Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContentSkeleton(lines = 4, accent = GhHairline, surface = GhSurface)
    }
}

@Composable
private fun GhError(message: String) {
    Text(
        message,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(Error.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun ConnectPrompt(onConnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onConnect)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github_logo),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Connect GitHub",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                "See issues, PRs, and activity across every repo",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GitHubCollapsedGlance(
    hub: GhHub,
    haptics: HapticHelper,
    onSelectRepo: (String) -> Unit,
) {
    val context = LocalContext.current
    val pulse = remember(hub) { collapsedPulse(hub) }
    val stats = remember(hub) { collapsedStats(hub) }
    val recentRepos = remember(hub.repos) { hub.repos.take(3) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RepoPicker(
            repos = hub.repos,
            selectedFullName = hub.selectedFullName,
            onSelect = onSelectRepo,
            compact = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stats.forEach { stat ->
                GlanceStat(
                    value = stat.value,
                    label = stat.label,
                    color = stat.color,
                    icon = stat.icon,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (pulse.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Sharp)
                    .background(GhSurface),
            ) {
                pulse.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = GhHairline.copy(alpha = 0.55f), thickness = 0.5.dp)
                    }
                    PulseLine(item) {
                        haptics.tick()
                        item.url?.let {
                            context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                        } ?: item.selectRepo?.let(onSelectRepo)
                    }
                }
            }
        }

        if (recentRepos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recentRepos.forEach { repo ->
                    RecentRepoChip(
                        repo = repo,
                        selected = repo.fullName.equals(hub.selectedFullName, ignoreCase = true),
                        onClick = {
                            haptics.tick()
                            onSelectRepo(
                                if (repo.fullName.equals(hub.selectedFullName, ignoreCase = true)) ""
                                else repo.fullName,
                            )
                        },
                    )
                }
            }
        }
    }
}

private data class GhStat(
    val value: String,
    val label: String,
    val color: Color,
    val icon: Int,
)

private data class GhPulse(
    val key: String,
    val title: String,
    val meta: String,
    val at: String?,
    val url: String? = null,
    val selectRepo: String? = null,
    val avatarUrl: String? = null,
    val tag: String? = null,
    val tagColor: Color? = null,
)

private fun collapsedStats(hub: GhHub): List<GhStat> {
    val mine = hub.pulls.count { it.authoredByMe }
    val ci = hub.focus?.workflowRuns?.firstOrNull()
    val release = hub.focus?.latestRelease
    val selected = hub.selectedRepo
    val third = when {
        hub.reviews > 0 -> GhStat(
            "${hub.reviews}",
            if (hub.reviews == 1) "Review" else "Reviews",
            GhReview,
            R.drawable.ic_gh_review,
        )
        ci != null -> {
            val pending = ci.status.equals("in_progress", true) || ci.status.equals("queued", true)
            val failed = ci.conclusion.equals("failure", true)
            val ok = ci.conclusion.equals("success", true)
            GhStat(
                when {
                    pending -> "…"
                    ok -> "✓"
                    failed -> "✕"
                    else -> ci.conclusion?.take(3)?.uppercase() ?: "CI"
                },
                ci.name.take(10),
                statusColor(ci.statusKey()),
                when {
                    pending -> R.drawable.ic_gh_dot
                    failed -> R.drawable.ic_gh_x
                    else -> R.drawable.ic_gh_check
                },
            )
        }
        mine > 0 -> GhStat("$mine", if (mine == 1) "Mine" else "Mine", GhOpen, R.drawable.ic_gh_pr)
        else -> GhStat("${hub.repos.size}", "Repos", GhAccent, R.drawable.ic_gh_repo)
    }
    val fourth = when {
        hub.unread > 0 -> GhStat("${hub.unread}", "Inbox", GhReview, R.drawable.ic_gh_inbox)
        release != null -> GhStat(
            release.tagName.removePrefix("v").take(6),
            "Release",
            GhAccent,
            R.drawable.ic_gh_tag,
        )
        selected != null -> GhStat(
            compactCount(selected.stars),
            "Stars",
            GhReview,
            R.drawable.ic_gh_star,
        )
        else -> GhStat("${hub.activity.size}", "Events", TextSecondary, R.drawable.ic_gh_commit)
    }
    return listOf(
        GhStat("${hub.openPrs}", if (hub.openPrs == 1) "PR" else "PRs", GhMerged, R.drawable.ic_gh_pr),
        GhStat("${hub.openIssues}", if (hub.openIssues == 1) "Issue" else "Issues", GhOpen, R.drawable.ic_gh_issue),
        third,
        fourth,
    )
}

private fun collapsedPulse(hub: GhHub): List<GhPulse> {
    val out = ArrayList<GhPulse>(3)
    val seen = HashSet<String>()

    fun add(item: GhPulse) {
        if (out.size >= 3) return
        val id = item.url ?: "${item.title}|${item.meta}|${item.at}"
        if (!seen.add(id)) return
        out += item
    }

    hub.pulls.firstOrNull { it.reviewRequested }?.let { pr ->
        add(
            GhPulse(
                key = "review",
                title = "#${pr.number}  ${pr.title}",
                meta = pr.repoFullName,
                at = pr.updatedAt,
                url = pr.htmlUrl,
                avatarUrl = pr.userAvatarUrl,
                tag = "REVIEW",
                tagColor = GhReview,
            ),
        )
    }
    hub.notifications.firstOrNull { it.unread }?.let { n ->
        add(
            GhPulse(
                key = "review",
                title = n.title,
                meta = "${n.repoFullName} · ${notificationReason(n.reason)}",
                at = n.updatedAt,
                url = n.htmlUrl,
                tag = n.type.take(8).uppercase(),
                tagColor = GhAccent,
            ),
        )
    }
    hub.issues.firstOrNull { it.assignedToMe }?.let { issue ->
        add(
            GhPulse(
                key = if (issue.isOpen) "open" else "closed",
                title = "#${issue.number}  ${issue.title}",
                meta = issue.repoFullName,
                at = issue.updatedAt,
                url = issue.htmlUrl,
                avatarUrl = issue.userAvatarUrl,
                tag = if (issue.assignedToMe) "ASSIGNED" else null,
                tagColor = GhAccent,
            ),
        )
    }

    val hotRun = hub.focus?.workflowRuns?.firstOrNull { run ->
        run.conclusion.equals("failure", true) ||
            run.status.equals("in_progress", true) ||
            run.status.equals("queued", true)
    }
    hotRun?.let { run ->
        val key = run.statusKey()
        add(
            GhPulse(
                key = key,
                title = run.displayTitle.ifBlank { run.name },
                meta = listOfNotNull(run.name, run.headBranch).joinToString(" · "),
                at = run.updatedAt,
                url = run.htmlUrl,
                tag = key.uppercase().take(8),
                tagColor = statusColor(key),
            ),
        )
    }
    hub.focus?.commits?.firstOrNull()?.let { commit ->
        add(
            GhPulse(
                key = "open",
                title = commit.message,
                meta = listOfNotNull(commit.sha.take(7), commit.authorLogin).joinToString(" · "),
                at = commit.committedAt,
                url = commit.htmlUrl,
                avatarUrl = commit.authorAvatarUrl,
                tag = "COMMIT",
                tagColor = GhAccent,
            ),
        )
    }

    hub.activity.forEach { act ->
        add(
            GhPulse(
                key = if (act.type == "PushEvent") "open" else "review",
                title = act.title,
                meta = act.repoFullName,
                at = act.createdAt,
                url = act.htmlUrl,
                avatarUrl = act.actorAvatarUrl,
                tag = act.type.removeSuffix("Event").uppercase().take(8),
                tagColor = GhAccent,
            ),
        )
    }

    if (out.size < 3) {
        hub.pulls.firstOrNull { !it.reviewRequested }?.let { pr ->
            add(
                GhPulse(
                    key = pr.statusKey(),
                    title = "#${pr.number}  ${pr.title}",
                    meta = pr.repoFullName,
                    at = pr.updatedAt,
                    url = pr.htmlUrl,
                    avatarUrl = pr.userAvatarUrl,
                    tag = pr.statusKey().uppercase(),
                    tagColor = statusColor(pr.statusKey()),
                ),
            )
        }
    }
    if (out.size < 3) {
        hub.issues.firstOrNull { !it.assignedToMe }?.let { issue ->
            add(
                GhPulse(
                    key = if (issue.isOpen) "open" else "closed",
                    title = "#${issue.number}  ${issue.title}",
                    meta = issue.repoFullName,
                    at = issue.updatedAt,
                    url = issue.htmlUrl,
                    avatarUrl = issue.userAvatarUrl,
                ),
            )
        }
    }

    return out.take(3)
}

@Composable
private fun GlanceStat(
    value: String,
    label: String,
    color: Color,
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(Sharp)
            .background(GhSurface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PulseLine(item: GhPulse, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!item.avatarUrl.isNullOrBlank()) {
            GhAvatar(item.avatarUrl, item.title, size = 22.dp)
        } else {
            StatusDot(item.key)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.meta.isNotBlank()) {
                Text(
                    item.meta,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.tag?.let { StatusTag(it, item.tagColor ?: GhAccent) }
        GhRelative(item.at)
    }
}

@Composable
private fun RecentRepoChip(
    repo: GitHubRepo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val touched = remember(repo.pushedAt, repo.updatedAt) { repo.lastTouchedAt() }
    val fresh = remember(touched) {
        touched != null && ChronoUnit.HOURS.between(touched, Instant.now()) < 1
    }
    Row(
        modifier = Modifier
            .clip(Pill)
            .background(if (selected) GhAccent.copy(alpha = 0.16f) else GhChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> GhAccent
                        fresh -> GhOpen
                        else -> GhDraft
                    },
                ),
        )
        Text(
            repo.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
        )
        GhRelative(repo.pushedAt ?: repo.updatedAt)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        color = TextSecondary,
    )
}

@Composable
private fun IssuesTab(
    issues: List<GitHubIssue>,
    focusedRepo: String?,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    var filterName by rememberSaveable { mutableStateOf(IssueFilter.ALL.name) }
    val filter = IssueFilter.entries.find { it.name == filterName } ?: IssueFilter.ALL
    val filtered = remember(issues, filter) {
        when (filter) {
            IssueFilter.ALL -> issues
            IssueFilter.ASSIGNED -> issues.filter { it.assignedToMe }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterRow {
            IssueFilter.entries.forEach { f ->
                val count = when (f) {
                    IssueFilter.ALL -> issues.size
                    IssueFilter.ASSIGNED -> issues.count { it.assignedToMe }
                }
                FilterChip(
                    label = "${f.label} $count",
                    active = filter == f,
                    activeColor = GhOpen,
                    onClick = { haptics.tick(); filterName = f.name },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyHint(
                if (focusedRepo != null) "No open issues in $focusedRepo"
                else "No open issues across your repos",
            )
        } else {
            WidgetScrollBox(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filtered.take(30).forEach { issue ->
                    IssueRow(issue) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
private fun PullsTab(
    pulls: List<GitHubPullRequest>,
    focusedRepo: String?,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    var filterName by rememberSaveable { mutableStateOf(PrFilter.ALL.name) }
    val filter = PrFilter.entries.find { it.name == filterName } ?: PrFilter.ALL
    val filtered = remember(pulls, filter) {
        when (filter) {
            PrFilter.ALL -> pulls
            PrFilter.MINE -> pulls.filter { it.authoredByMe }
            PrFilter.REVIEW -> pulls.filter { it.reviewRequested }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterRow {
            PrFilter.entries.forEach { f ->
                val count = when (f) {
                    PrFilter.ALL -> pulls.size
                    PrFilter.MINE -> pulls.count { it.authoredByMe }
                    PrFilter.REVIEW -> pulls.count { it.reviewRequested }
                }
                val color = when (f) {
                    PrFilter.REVIEW -> GhReview
                    PrFilter.MINE -> GhOpen
                    PrFilter.ALL -> GhMerged
                }
                FilterChip(
                    label = "${f.label} $count",
                    active = filter == f,
                    activeColor = color,
                    onClick = { haptics.tick(); filterName = f.name },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyHint(
                if (focusedRepo != null) "No open pull requests in $focusedRepo"
                else "No open pull requests across your repos",
            )
        } else {
            WidgetScrollBox(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filtered.take(30).forEach { pr ->
                    PullRow(pr) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, pr.htmlUrl.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(activity: List<GitHubActivity>, haptics: HapticHelper) {
    val context = LocalContext.current
    if (activity.isEmpty()) {
        EmptyHint("No recent activity")
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WidgetScrollBox(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            activity.take(30).forEach { item ->
                ActivityRow(item) {
                    haptics.tick()
                    item.htmlUrl?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReposTab(
    repos: List<GitHubRepo>,
    selectedFullName: String,
    focus: GitHubRepoFocus?,
    focusLoading: Boolean,
    onSelectRepo: (String) -> Unit,
    haptics: HapticHelper,
) {
    val selected = focus?.repo ?: repos.firstOrNull { it.fullName.equals(selectedFullName, true) }
    if (selected != null) {
        RepoDetail(
            repo = selected,
            focus = focus,
            loading = focusLoading,
            haptics = haptics,
            onClear = { haptics.tick(); onSelectRepo("") },
        )
        return
    }
    if (repos.isEmpty()) {
        EmptyHint("No repositories yet")
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetScrollBox(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repos.take(40).forEach { repo ->
                RepoRow(repo) {
                    haptics.tick()
                    onSelectRepo(repo.fullName)
                }
            }
        }
        Text(
            "Tap a repo to focus it · most recently updated first",
            fontSize = 11.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun InboxTab(
    notifications: List<GitHubNotification>,
    needReconnect: Boolean,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    if (needReconnect && notifications.isEmpty()) {
        EmptyHint("Reconnect GitHub to load your inbox (notifications scope).")
        return
    }
    if (notifications.isEmpty()) {
        EmptyHint("Inbox is clear")
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetScrollBox(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            notifications.take(30).forEach { item ->
                NotificationRow(item) {
                    haptics.tick()
                    item.htmlUrl?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: GitHubNotification, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (item.unread) GhReview else GhDraft),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Medium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(item.repoFullName)
                    val reason = notificationReason(item.reason)
                    if (reason.isNotBlank()) append(" · $reason")
                },
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusTag(item.type.take(8).uppercase(), GhAccent)
        GhRelative(item.updatedAt)
    }
}

@Composable
private fun RepoPicker(
    repos: List<GitHubRepo>,
    selectedFullName: String,
    onSelect: (String) -> Unit,
    compact: Boolean = false,
    compactMeta: String? = null,
) {
    var open by rememberSaveable(compact) { mutableStateOf(false) }
    val selected = repos.firstOrNull { it.fullName.equals(selectedFullName, ignoreCase = true) }
    val rotation by animateFloatAsState(
        if (open) 180f else 0f,
        MacroMotion.pressSpring(),
        label = "ghPickerRot",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .border(0.5.dp, GhHairline.copy(alpha = 0.8f), Sharp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (compact) {
                Text(
                    selected?.name ?: "All repos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!compactMeta.isNullOrBlank()) {
                    Text(
                        compactMeta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selected?.name ?: "All repos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        selected?.fullName ?: "${repos.size} repos · recently updated first",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                AppIcons.ChevronDown,
                contentDescription = if (open) "Close repo list" else "Choose repo",
                tint = TextSecondary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = open) {
            WidgetScrollBox(
                maxHeight = if (compact) 148.dp else 240.dp,
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                HorizontalDivider(color = GhHairline, thickness = 0.5.dp)
                PickerRow(
                    title = "All repos",
                    subtitle = "Issues, PRs, and activity across your account",
                    active = selectedFullName.isBlank(),
                    at = null,
                    compact = compact,
                ) {
                    onSelect("")
                    open = false
                }
                repos.forEach { repo ->
                    PickerRow(
                        title = repo.name,
                        subtitle = repo.fullName,
                        active = repo.fullName.equals(selectedFullName, ignoreCase = true),
                        at = repo.pushedAt ?: repo.updatedAt,
                        language = repo.language,
                        compact = compact,
                    ) {
                        onSelect(repo.fullName)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    active: Boolean,
    at: String?,
    language: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) GhAccent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (compact) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!language.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(languageColor(language)),
            )
        }
        GhRelative(at)
    }
}

@Composable
private fun RepoDetail(
    repo: GitHubRepo,
    focus: GitHubRepoFocus?,
    loading: Boolean,
    haptics: HapticHelper,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val commits = focus?.commits.orEmpty()
    val runs = focus?.workflowRuns.orEmpty()
    val release = focus?.latestRelease

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    repo.fullName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (repo.isPrivate) {
                        Icon(AppIcons.Lock, null, tint = GhDraft, modifier = Modifier.size(12.dp))
                    }
                    Text("Focused", fontSize = 11.sp, color = GhAccent, fontWeight = FontWeight.SemiBold)
                    GhRelative(repo.pushedAt ?: repo.updatedAt)
                }
            }
            IconButton(
                onClick = {
                    haptics.tick()
                    context.startActivity(Intent(Intent.ACTION_VIEW, repo.htmlUrl.toUri()))
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    AppIcons.ExternalLink,
                    contentDescription = "Open on GitHub",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        repo.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, color = TextSecondary)
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!repo.language.isNullOrBlank()) {
                MetaChip(dotColor = languageColor(repo.language), text = repo.language)
            }
            MetaChip(icon = AppIcons.Star, text = compactCount(repo.stars), color = GhReview)
            MetaChip(icon = AppIcons.GitFork, text = compactCount(repo.forks))
            repo.defaultBranch?.let { MetaChip(text = it) }
            repo.license?.takeIf { it != "NOASSERTION" }?.let { MetaChip(text = it) }
        }

        if (release != null) {
            ReleaseRow(release) {
                haptics.tick()
                context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()))
            }
        }

        if (loading && focus == null) {
            GhLoading()
        }

        if (commits.isNotEmpty()) {
            SectionLabel("COMMITS")
            WidgetScrollBox(
                maxHeight = 200.dp,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                commits.take(10).forEach { commit ->
                    CommitRow(commit) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, commit.htmlUrl.toUri()))
                    }
                }
            }
        }

        if (runs.isNotEmpty()) {
            SectionLabel("ACTIONS")
            WidgetScrollBox(
                maxHeight = 180.dp,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                runs.take(8).forEach { run ->
                    WorkflowRow(run) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, run.htmlUrl.toUri()))
                    }
                }
            }
        }

        TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
            Text("All repos", color = GhAccent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReleaseRow(release: GitHubRelease, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Tag, null, tint = GhAccent, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (release.prerelease) "Pre-release ${release.tagName}" else release.tagName,
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        GhRelative(release.publishedAt)
    }
}

@Composable
private fun CommitRow(commit: GitHubCommit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GhAvatar(commit.authorAvatarUrl, commit.authorLogin ?: "c", size = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                commit.message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(commit.sha.take(7))
                    commit.authorLogin?.let { append(" · $it") }
                },
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        GhRelative(commit.committedAt)
    }
}

@Composable
private fun WorkflowRow(run: GitHubWorkflowRun, onClick: () -> Unit) {
    val key = run.statusKey()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(key)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                run.displayTitle.ifBlank { run.name },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(run.name)
                    run.headBranch?.let { append(" · $it") }
                },
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        StatusTag(key.uppercase().take(8), statusColor(key))
        GhRelative(run.updatedAt)
    }
}

private fun notificationReason(reason: String): String = when (reason) {
    "review_requested" -> "Review"
    "mention" -> "Mention"
    "assign" -> "Assigned"
    "author" -> "Yours"
    "comment" -> "Comment"
    "subscribed" -> "Watching"
    "state_change" -> "Updated"
    "ci_activity" -> "CI"
    "security_alert" -> "Security"
    "manual" -> "Manual"
    else -> reason.replace('_', ' ')
}

@Composable
private fun AccountTab(
    snapshot: GitHubSnapshot?,
    authState: GitHubAuthUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
    haptics: HapticHelper,
) {
    val connected = authState.isConnected || snapshot != null

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (snapshot != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhAvatar(snapshot.user.avatarUrl, snapshot.user.login, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snapshot.user.name?.takeIf { it.isNotBlank() } ?: snapshot.user.login,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text("@${snapshot.user.login}", fontSize = 12.sp, color = TextSecondary)
                }
            }
            snapshot.user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = TextSecondary)
            }
            if (snapshot.notificationsNeedReconnect) {
                Text(
                    "Disconnect and connect again to enable inbox notifications.",
                    fontSize = 12.sp,
                    color = GhReview,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetaChip(text = "${snapshot.user.publicRepos + snapshot.user.totalPrivateRepos} repos")
                MetaChip(text = "${compactCount(snapshot.user.followers)} followers")
                MetaChip(text = "${compactCount(snapshot.user.following)} following")
            }
            val remaining = snapshot.rateLimitRemaining
            val limit = snapshot.rateLimitLimit
            if (remaining != null && limit != null) {
                Text(
                    "API · $remaining / $limit left this hour",
                    fontSize = 11.sp,
                    color = if (remaining < 10) GhClosed else TextSecondary,
                )
            }
        } else {
            Text(
                "Sign in with GitHub to load issues, PRs, and activity from every repo your account can see.",
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }

        GitHubAccountActions(
            connected = connected,
            authState = authState,
            onConnect = {
                if (!authState.isBusy) {
                    haptics.click()
                    onConnect()
                }
            },
            onDisconnect = {
                haptics.reject()
                onDisconnect()
            },
            onCancelLogin = onCancelLogin,
            onOpenActivation = onOpenActivation,
        )

        authState.statusMessage?.let { msg ->
            Text(
                msg,
                fontSize = 11.sp,
                color = if (authState.isError) Error else TextSecondary,
            )
        }
    }
}

@Composable
private fun GitHubAccountActions(
    connected: Boolean,
    authState: GitHubAuthUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
) {
    when {
        authState.isAwaitingBrowser -> {
            GitHubDeviceCodePanel(
                userCode = authState.deviceLogin?.userCode,
                onOpenActivation = onOpenActivation,
                onCancelLogin = onCancelLogin,
            )
        }
        connected -> {
            MacroButton(
                text = "Disconnect",
                onClick = onDisconnect,
                variant = ButtonVariant.SECONDARY,
            )
        }
        !authState.isConfigured -> {
            Text(
                "Add GITHUB_CLIENT_ID to local.properties and rebuild, then tap Connect.",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        else -> {
            Button(
                onClick = onConnect,
                enabled = !authState.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = GhAccent),
                shape = Sharp,
            ) {
                if (authState.isBusy) {
                    LoadingSpinner(color = Color.White, size = LoadingSpec.SizeInline)
                } else {
                    Icon(
                        AppIcons.Account,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (authState.isBusy) "Connecting…" else "Connect GitHub",
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun GitHubDeviceCodePanel(
    userCode: String?,
    onOpenActivation: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val code = userCode?.takeIf { it.isNotBlank() }

    fun copyCode(fromUser: Boolean = false) {
        val value = code ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", value))
        copied = true
        if (fromUser) haptics.confirm()
    }

    LaunchedEffect(code) {
        if (code != null) copyCode(fromUser = false)
    }
    LaunchedEffect(copied, code) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhAccent.copy(alpha = 0.08f))
            .border(1.dp, GhAccent.copy(alpha = 0.35f), Sharp)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Enter this code on GitHub",
            fontSize = 12.sp,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(Sharp)
                .background(GhSurface)
                .clickable(enabled = code != null, onClick = { copyCode(fromUser = true) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                code ?: "····",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Icon(
                imageVector = if (copied) AppIcons.Check else AppIcons.Copy,
                contentDescription = if (copied) "Copied" else "Copy code",
                tint = if (copied) GhOpen else GhAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (copied) "Copied to clipboard" else "Copied automatically · tap to copy again",
            fontSize = 11.sp,
            color = if (copied) GhOpen else TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Approve DailyDash at github.com/login/device",
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    haptics.click()
                    onOpenActivation()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GhAccent),
                shape = Sharp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    AppIcons.ExternalLink,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open GitHub", fontSize = 13.sp)
            }
            Button(
                onClick = { copyCode(fromUser = true) },
                enabled = code != null,
                colors = ButtonDefaults.buttonColors(containerColor = GhChip, contentColor = TextPrimary),
                shape = Sharp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    if (copied) AppIcons.Check else AppIcons.Copy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (copied) "Copied" else "Copy", fontSize = 13.sp)
            }
            TextButton(onClick = { haptics.tick(); onCancelLogin() }) {
                Text("Cancel", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun IssueRow(issue: GitHubIssue, onClick: () -> Unit) {
    val key = if (issue.isOpen) "open" else "closed"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(key)
            Text(
                "#${issue.number}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                issue.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepoTag(issue.repoFullName)
            if (issue.assignedToMe) StatusTag("ASSIGNED", GhAccent)
            Spacer(Modifier.weight(1f))
            issue.labels.take(2).forEach { LabelChip(it) }
            if (issue.comments > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(AppIcons.Chat, null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                    Text("${issue.comments}", fontSize = 11.sp, color = TextSecondary)
                }
            }
            GhRelative(issue.updatedAt)
        }
    }
}

@Composable
private fun PullRow(pr: GitHubPullRequest, onClick: () -> Unit) {
    val key = pr.statusKey()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(key)
            Text(
                "#${pr.number}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                pr.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusTag(key.uppercase(), statusColor(key))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepoTag(pr.repoFullName)
            GhAvatar(pr.userAvatarUrl, pr.userLogin, size = 14.dp)
            Text(pr.userLogin, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            pr.labels.take(2).forEach { LabelChip(it) }
            GhRelative(pr.updatedAt)
        }
    }
}

@Composable
private fun ActivityRow(
    item: GitHubActivity,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .then(if (compact) Modifier.background(GhSurface) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 4.dp, vertical = if (compact) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GhAvatar(item.actorAvatarUrl, item.actorLogin)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.repoFullName,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusTag(item.type.removeSuffix("Event").uppercase().take(8), GhAccent)
        GhRelative(item.createdAt)
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                repo.fullName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (repo.isPrivate) {
                Icon(AppIcons.Lock, null, tint = GhDraft, modifier = Modifier.size(13.dp))
            }
            Icon(
                AppIcons.ExternalLink,
                contentDescription = "Open on GitHub",
                tint = TextTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, repo.htmlUrl.toUri()))
                    },
            )
        }
        repo.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!repo.language.isNullOrBlank()) {
                MetaChip(dotColor = languageColor(repo.language), text = repo.language)
            }
            MetaChip(icon = AppIcons.Star, text = compactCount(repo.stars), color = GhReview)
            MetaChip(icon = AppIcons.GitFork, text = compactCount(repo.forks))
            Spacer(Modifier.weight(1f))
            GhRelative(repo.pushedAt)
        }
    }
}

@Composable
private fun RepoTag(fullName: String) {
    if (fullName.isBlank()) return
    Text(
        fullName,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = GhAccent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .clip(Pill)
            .background(GhAccent.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun MetaChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dotColor: Color? = null,
    color: Color = TextSecondary,
) {
    Row(
        modifier = Modifier
            .clip(Pill)
            .background(GhChip)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        } else if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberWidgetCrossAxisScrollLock())
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() },
    )
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) activeColor.copy(alpha = 0.18f) else GhChip
    val fg = if (active) activeColor else TextSecondary
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(Pill)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LabelChip(label: GitHubLabel) {
    val bg = remember(label.color) { parseHexColor(label.color) }
    val fg = remember(label.color) { labelForeground(label.color) }
    Text(
        label.name,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 88.dp)
            .clip(Pill)
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        color = color,
        modifier = Modifier
            .clip(Pill)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun StatusDot(key: String) {
    val color = statusColor(key)
    Canvas(modifier = Modifier.size(12.dp)) {
        when (key) {
            "merged", "review" -> drawCircle(color, radius = size.minDimension / 2.2f)
            "closed", "failure" -> {
                drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 2.2.dp.toPx()))
            }
            "draft" -> drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 1.8.dp.toPx()))
            else -> drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 2.2.dp.toPx()))
        }
    }
}

@Composable
private fun GhAvatar(url: String?, name: String, size: Dp = 22.dp) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(GhChip),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(GhChip),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GhRelative(iso: String?) {
    val instant = remember(iso) { parseGitHubInstant(iso) } ?: return
    val text = rememberRelativeTime(instant)
    Text(text, fontSize = 11.sp, color = TextSecondary)
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

private fun statusColor(key: String): Color = when (key) {
    "open", "success" -> GhOpen
    "merged" -> GhMerged
    "review" -> GhReview
    "closed", "failure" -> GhClosed
    "draft", "cancelled" -> GhDraft
    else -> TextSecondary
}

private fun parseHexColor(hex: String): Color {
    val rgb = hex.trim().removePrefix("#").toLongOrNull(16) ?: return GhDraft
    return Color(
        ((rgb shr 16) and 0xFF).toInt(),
        ((rgb shr 8) and 0xFF).toInt(),
        (rgb and 0xFF).toInt(),
    )
}

private fun labelForeground(hex: String): Color {
    val rgb = hex.trim().removePrefix("#").toLongOrNull(16) ?: return Color.White
    val r = ((rgb shr 16) and 0xFF) / 255.0
    val g = ((rgb shr 8) and 0xFF) / 255.0
    val b = (rgb and 0xFF) / 255.0
    val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return if (l > 0.55) Color(0xFF0D1117) else Color.White
}

private fun languageColor(language: String): Color = when (language.lowercase()) {
    "kotlin" -> Color(0xFFA97BFF)
    "java" -> Color(0xFFB07219)
    "javascript" -> Color(0xFFF1E05A)
    "typescript" -> Color(0xFF3178C6)
    "python" -> Color(0xFF3572A5)
    "go", "golang" -> Color(0xFF00ADD8)
    "rust" -> Color(0xFFDEA584)
    "swift" -> Color(0xFFF05138)
    "c++" -> Color(0xFFF34B7D)
    "ruby" -> Color(0xFF701516)
    "shell" -> Color(0xFF89E051)
    "dart" -> Color(0xFF00B4AB)
    else -> GhAccent
}
