package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.draggableWidgetItems
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.components.rememberDraggableWidgetListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.server.AdvisorySeverity
import com.macrotracker.data.server.DashboardLink
import com.macrotracker.data.server.ServerAdvisory
import com.macrotracker.data.server.ServerAiSection
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerError
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.ServerStatChip
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.StatLabel
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.screens.server.AskAiButton
import com.macrotracker.ui.screens.server.ComputeCard
import com.macrotracker.ui.screens.server.ContainersCard
import com.macrotracker.ui.screens.server.DashboardAlertRows
import com.macrotracker.ui.screens.server.MemoryCard
import com.macrotracker.ui.screens.server.NetworkCard
import com.macrotracker.ui.screens.server.ProcessesCard
import com.macrotracker.ui.screens.server.SectionHeader
import com.macrotracker.ui.screens.server.SensorsCard
import com.macrotracker.ui.screens.server.ServerActivityCard
import com.macrotracker.ui.screens.server.ServerHeroCard
import com.macrotracker.ui.screens.server.ServerHistoryCard
import com.macrotracker.ui.screens.server.ServicesWallCard
import com.macrotracker.ui.screens.server.StatusDot
import com.macrotracker.ui.screens.server.StorageCard
import com.macrotracker.ui.screens.server.SystemCard
import com.macrotracker.ui.screens.server.relativeSeconds
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerMemory
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerViewModel

/**
 * The full server dashboard.
 *
 * Deliberately data-dense: every section is a fixed slot so numbers do not jump
 * between polls, and anything the server could not answer renders as a dash
 * rather than disappearing (a vanishing card at 5-second intervals is worse
 * than an empty one).
 */
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    /** Null hides every "ask the AI" affordance (no provider configured). */
    onAskAi: ((String) -> Unit)? = null,
    initialServerId: String? = null,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val runtimes by viewModel.runtimes.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val haptics = rememberHaptics()

    // Polling is reference-counted, so holding it for the lifetime of the screen
    // is enough — the home card and the live notification share these sessions.
    // This screen also asks for the detail lane: container usage, ports, the journal.
    DisposableEffect(Unit) {
        viewModel.startPolling(detailed = true)
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(Unit) { viewModel.followDashboard(DASHBOARD_REFRESH_MS) }
    val dashboardLink by viewModel.dashboardLink.collectAsState()

    val sectionOrder by viewModel.sectionOrder.collectAsState()
    val sections = remember(sectionOrder) { parseWidgetConfig(sectionOrder, ServerSections) }
    var editing by rememberSaveable { mutableStateOf(false) }

    var selectedId by rememberSaveable { mutableStateOf(initialServerId) }
    // A tapped notification names the server it was about.
    LaunchedEffect(Unit) { viewModel.consumeFocus()?.let { selectedId = it } }
    val activeId = selectedId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
    val runtime = activeId?.let { runtimes[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        SubScreenHeader(
            title = "Servers",
            subtitle = runtime?.profile?.displayTarget,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                if (profiles.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            editing = !editing
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (editing) AppIcons.Check else AppIcons.Edit,
                            contentDescription = if (editing) "Done" else "Edit sections",
                            tint = if (editing) Primary else TextSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tick()
                        onNavigateToSettings()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        AppIcons.Settings,
                        contentDescription = "Server settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        if (profiles.isEmpty()) {
            ServerEmptyState(onNavigateToSettings)
            return@Column
        }

        if (profiles.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeId
                    val health = runtimes[profile.id]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) Primary.copy(alpha = 0.18f) else ServerWell)
                            .clickable {
                                haptics.tick()
                                selectedId = profile.id
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(health)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = profile.label,
                            color = if (isActive) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (runtime == null) {
            ServerEmptyState(onNavigateToSettings)
            return@Column
        }

        // Every card gets the same affordance: bundle this section's live readings
        // and open a Sysop thread on them.
        val ask: (ServerAiSection) -> (() -> Unit)? = { section ->
            onAskAi?.let { open ->
                {
                    haptics.click()
                    viewModel.askAiAbout(runtime.profile.id, section)?.let(open)
                }
            }
        }

        val link = dashboardLink?.takeIf { it.belongsTo(runtime.hostProfile?.hostname) }

        // Only what this server can fill: advisories when there are some, the dashboard's
        // sections when it runs the dashboard. A hidden or empty section keeps its place.
        val available = remember(runtime, link) {
            buildSet {
                add(SECTION_OVERVIEW)
                if (runtime.advisories.isNotEmpty() || link?.alerts?.isNotEmpty() == true) add(SECTION_ADVISORIES)
                if (link != null && (link.activity.nowPlaying != null || link.activity.downloads.isNotEmpty())) add(SECTION_ACTIVITY)
                add(SECTION_HISTORY)
                if (link != null && link.services.isNotEmpty()) add(SECTION_SERVICES)
                addAll(listOf(SECTION_COMPUTE, SECTION_MEMORY, SECTION_NETWORK, SECTION_STORAGE, SECTION_SENSORS))
                addAll(listOf(SECTION_PROCESSES, SECTION_CONTAINERS, SECTION_SYSTEM, SECTION_UPDATES))
                if (runtime.snapshot?.sessions?.isNotEmpty() == true) add(SECTION_SESSIONS)
            }
        }
        val shown = remember(sections, available) { sections.filter { it.isVisible && it.id in available } }
        val listState = rememberLazyListState()
        val dragState = rememberDraggableWidgetListState(
            items = shown,
            lazyListState = listState,
            itemKey = { it.id },
            onReorder = { reordered -> viewModel.updateSectionOrder(encodeWidgetConfig(mergeReordered(sections, reordered))) },
            haptics = haptics,
        )

        LazyColumn(
            state = listState,
            userScrollEnabled = !dragState.isDragActive,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            if (editing) {
                item(key = "editor") {
                    WidgetEditor(
                        configs = sections,
                        onConfigsChanged = { viewModel.updateSectionOrder(encodeWidgetConfig(it)) },
                        onClose = { editing = false },
                    )
                    Text(
                        "Advisories, Happening now, Services and Logged in only show when there is something in them.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                return@LazyColumn
            }
            draggableWidgetItems(
                state = dragState,
                itemKey = { it.id },
                haptics = haptics,
            ) { _, section, _ ->
                when (section.id) {
                    SECTION_OVERVIEW -> ServerHeroCard(runtime, link, ask(ServerAiSection.OVERVIEW))
                    SECTION_ADVISORIES -> ServerAdvisoriesCard(
                        runtime = runtime,
                        link = link,
                        onTrustHostKey = { viewModel.trustNewHostKey(runtime.profile.id) },
                        onAskAi = ask(ServerAiSection.ADVISORIES),
                        onAskAiAbout = onAskAi?.let { open ->
                            { advisory ->
                                haptics.click()
                                viewModel.askAiAboutAdvisory(runtime.profile.id, advisory)?.let(open)
                            }
                        },
                    )
                    SECTION_ACTIVITY -> link?.let { ServerActivityCard(it) }
                    SECTION_HISTORY -> ServerHistoryCard(runtime, link)
                    SECTION_SERVICES -> link?.let { ServicesWallCard(it, ask(ServerAiSection.OVERVIEW)) }
                    SECTION_COMPUTE -> ComputeCard(runtime, ask(ServerAiSection.COMPUTE))
                    SECTION_MEMORY -> MemoryCard(runtime, ask(ServerAiSection.MEMORY))
                    SECTION_NETWORK -> NetworkCard(runtime, ask(ServerAiSection.NETWORK))
                    SECTION_STORAGE -> StorageCard(runtime, ask(ServerAiSection.STORAGE))
                    SECTION_SENSORS -> SensorsCard(runtime, ask(ServerAiSection.THERMAL))
                    SECTION_PROCESSES -> ProcessesCard(runtime, ask(ServerAiSection.PROCESSES))
                    SECTION_CONTAINERS -> ContainersCard(runtime, ask(ServerAiSection.DOCKER))
                    SECTION_SYSTEM -> SystemCard(runtime, ask(ServerAiSection.SERVICES))
                    SECTION_UPDATES -> ServerUpdatesCard(
                        runtime = runtime,
                        onRefresh = { viewModel.refreshNews(runtime.profile.id) },
                        onAskAi = ask(ServerAiSection.UPDATES),
                    )
                    SECTION_SESSIONS -> ServerSessionsCard(runtime, ask(ServerAiSection.SESSIONS))
                }
            }
            if (shown.isEmpty()) {
                item(key = "all-hidden") {
                    Column(modifier = Modifier.padding(vertical = 24.dp)) {
                        Text(
                            "Every section is switched off. Tap the pencil to bring some back.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MacroButton(
                            text = "Edit sections",
                            onClick = { editing = true },
                            variant = ButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        }
    }
}

private const val SECTION_OVERVIEW = "OVERVIEW"
private const val SECTION_ADVISORIES = "ADVISORIES"
private const val SECTION_ACTIVITY = "ACTIVITY"
private const val SECTION_HISTORY = "HISTORY"
private const val SECTION_SERVICES = "SERVICES"
private const val SECTION_COMPUTE = "COMPUTE"
private const val SECTION_MEMORY = "MEMORY"
private const val SECTION_NETWORK = "NETWORK"
private const val SECTION_STORAGE = "STORAGE"
private const val SECTION_SENSORS = "SENSORS"
private const val SECTION_PROCESSES = "PROCESSES"
private const val SECTION_CONTAINERS = "CONTAINERS"
private const val SECTION_SYSTEM = "SYSTEM"
private const val SECTION_UPDATES = "UPDATES"
private const val SECTION_SESSIONS = "SESSIONS"

/** The server screen's sections in their built-in order, for the pencil editor. */
private val ServerSections = listOf(
    Triple(SECTION_OVERVIEW, "Overview", AppIcons.Dashboard),
    Triple(SECTION_ADVISORIES, "Advisories", AppIcons.Warning),
    Triple(SECTION_ACTIVITY, "Happening now", AppIcons.Play),
    Triple(SECTION_HISTORY, "History", AppIcons.ChartLine),
    Triple(SECTION_SERVICES, "Services", AppIcons.Activity),
    Triple(SECTION_COMPUTE, "Compute", AppIcons.Cpu),
    Triple(SECTION_MEMORY, "Memory", AppIcons.ChartPie),
    Triple(SECTION_NETWORK, "Network", AppIcons.SwapVertical),
    Triple(SECTION_STORAGE, "Storage", AppIcons.HardDrive),
    Triple(SECTION_SENSORS, "Sensors & power", AppIcons.Flame),
    Triple(SECTION_PROCESSES, "Top processes", AppIcons.Terminal),
    Triple(SECTION_CONTAINERS, "Containers", AppIcons.Blocks),
    Triple(SECTION_SYSTEM, "System", AppIcons.Settings),
    Triple(SECTION_UPDATES, "Updates & news", AppIcons.Download),
    Triple(SECTION_SESSIONS, "Logged in", AppIcons.Account),
)

/**
 * Puts a drag's new order back into the full list. Sections that were hidden, or had
 * nothing to show, keep their own slots instead of all piling up at the end.
 */
private fun mergeReordered(all: List<WidgetConfig>, reordered: List<WidgetConfig>): List<WidgetConfig> {
    val moved = reordered.map { it.id }.toSet()
    val next = reordered.iterator()
    return all.map { if (it.id in moved && next.hasNext()) next.next() else it }
}

@Composable
private fun ServerEmptyState(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AppIcons.Server,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No servers yet",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add an SSH host and DailyDash reads live stats straight from /proc — " +
                "no agent to install on the server.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        MacroButton(text = "Add a server", onClick = onNavigateToSettings)
    }
}

@Composable
private fun ServerAdvisoriesCard(
    runtime: ServerRuntime,
    link: DashboardLink?,
    onTrustHostKey: () -> Unit,
    onAskAi: (() -> Unit)? = null,
    onAskAiAbout: ((ServerAdvisory) -> Unit)? = null,
) {
    val critical = runtime.advisories.count { it.severity == AdvisorySeverity.CRITICAL }
    val appAlerts = link?.alerts.orEmpty()
    MacroCard(
        delayMs = 60,
        borderColor = if (critical > 0) ServerCritical.copy(alpha = 0.45f) else Border,
    ) {
        SectionHeader(
            title = "Advisories",
            icon = AppIcons.Warning,
            accent = if (critical > 0) ServerCritical else ServerWarn,
            trailing = "${runtime.advisories.size + appAlerts.size}",
            onAskAi = onAskAi,
        )
        runtime.advisories.forEachIndexed { index, advisory ->
            AdvisoryRow(advisory, onAskAi = onAskAiAbout?.let { handler -> { handler(advisory) } })
            if (index < runtime.advisories.lastIndex) {
                HorizontalDivider(
                    color = Border.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        if (link != null && appAlerts.isNotEmpty()) {
            // What the apps themselves report, via the dashboard's collector.
            if (runtime.advisories.isNotEmpty()) {
                HorizontalDivider(color = Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
            }
            DashboardAlertRows(link)
        }
        val hostKeyChanged = (runtime.connection as? ServerConnectionState.Offline)
            ?.reason is ServerError.HostKeyChanged
        if (hostKeyChanged) {
            Spacer(modifier = Modifier.height(6.dp))
            MacroButton(
                text = "Trust the new host key",
                onClick = onTrustHostKey,
                variant = ButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Only do this if you rebuilt or reinstalled the server yourself.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AdvisoryRow(advisory: ServerAdvisory, onAskAi: (() -> Unit)? = null) {
    val color = when (advisory.severity) {
        AdvisorySeverity.CRITICAL -> ServerCritical
        AdvisorySeverity.WARNING -> ServerWarn
        AdvisorySeverity.INFO -> TextSecondary
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advisory.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = advisory.detail,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ServerTag(advisory.category.name.take(4), color)
        if (onAskAi != null) {
            Spacer(modifier = Modifier.width(6.dp))
            AskAiButton(accent = color, onClick = onAskAi)
        }
    }
}

@Composable
private fun ServerUpdatesCard(
    runtime: ServerRuntime,
    onRefresh: () -> Unit,
    onAskAi: (() -> Unit)? = null,
) {
    val news = runtime.news
    val haptics = rememberHaptics()
    MacroCard(delayMs = 190) {
        SectionHeader(
            title = "Updates & news",
            icon = AppIcons.Download,
            accent = ServerWarn,
            trailing = runtime.hostProfile?.packageManager?.label,
            onAskAi = onAskAi,
        )
        if (news == null) {
            // First check still running: a placeholder shaped like the card, as elsewhere.
            ContentSkeleton(lines = 2, accent = Border)
            return@MacroCard
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            ServerStatChip(
                "UPDATES",
                news.updatesAvailable?.toString() ?: "—",
                Modifier.weight(1f),
                valueColor = if ((news.updatesAvailable ?: 0) > 0) ServerWarn else TextPrimary,
            )
            ServerStatChip(
                "SECURITY",
                news.securityUpdatesAvailable?.toString() ?: "—",
                Modifier.weight(1f),
                valueColor = if ((news.securityUpdatesAvailable ?: 0) > 0) ServerCritical else TextPrimary,
            )
            ServerStatChip(
                "REBOOT",
                if (news.rebootRequired) "yes" else "no",
                Modifier.weight(1f),
                valueColor = if (news.rebootRequired) ServerWarn else TextPrimary,
            )
            ServerStatChip(
                "FAILED SSH",
                news.failedLoginsLastDay?.toString() ?: "—",
                Modifier.weight(1f),
            )
        }
        if (news.updatablePackages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            StatLabel("PENDING PACKAGES")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = news.updatablePackages.take(14).joinToString(", "),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (news.fail2banJails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                news.fail2banJails.take(5).forEach { ServerTag(it.name, ServerGood) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Checked ${relativeSeconds(news.fetchedAtMs)} ago",
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Re-check now",
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.click()
                        onRefresh()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ServerSessionsCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val sessions = runtime.snapshot?.sessions.orEmpty()
    MacroCard(delayMs = 200) {
        SectionHeader(
            title = "Logged in",
            icon = AppIcons.Account,
            accent = ServerMemory,
            trailing = "${sessions.size}",
            onAskAi = onAskAi,
        )
        sessions.forEach { session ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.user,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(92.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.tty,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.from.ifBlank { session.since },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val DASHBOARD_REFRESH_MS = 30_000L
