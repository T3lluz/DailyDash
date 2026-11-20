package com.macrotracker.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.macrotracker.ui.screens.server.serverLede
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.PageTab
import com.macrotracker.ui.screens.home.GlassRound
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.theme.ServerCpu
import com.macrotracker.ui.screens.health.HealthDisplayFamily
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.WellFill
import androidx.compose.foundation.background
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberIsResumed
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
import com.macrotracker.ui.screens.server.ServerRoom
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
    /** The dashboard server's shell (ConsoleScreen). */
    onOpenConsole: (() -> Unit)? = null,
    initialServerId: String? = null,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val runtimes by viewModel.runtimes.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val haptics = rememberHaptics()

    // Polling is reference-counted, so holding it for the lifetime of the screen
    // is enough — the home card and the live notification share these sessions.
    // This screen also asks for the detail lane: container usage, ports, the journal.
    // Held while the screen is in front; the live notification keeps its own hold.
    val resumed = rememberIsResumed()
    DisposableEffect(resumed) {
        if (resumed) viewModel.startPolling(detailed = true)
        onDispose { if (resumed) viewModel.stopPolling() }
    }
    LaunchedWhileResumed { viewModel.followDashboard(DASHBOARD_REFRESH_MS) }
    val dashboardLink by viewModel.dashboardLink.collectAsState()

    val sectionOrder by viewModel.sectionOrder.collectAsState()
    val sections = remember(sectionOrder) { parseWidgetConfig(sectionOrder, ServerSections) }
    var editing by rememberSaveable { mutableStateOf(false) }

    var selectedId by rememberSaveable { mutableStateOf(initialServerId) }
    // A tapped notification names the server it was about.
    LaunchedEffect(Unit) { viewModel.consumeFocus()?.let { selectedId = it } }
    val activeId = selectedId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
    val runtime = activeId?.let { runtimes[it] }

    if (profiles.isEmpty() || runtime == null) {
        HomePage(title = "Servers", tone = HomeTone.Servers, onBack = onNavigateBack, lede = "No servers yet.") {
            item(key = "empty") { ServerEmptyState(onNavigateToSettings) }
        }
        return
    }

    // Every room gets the same affordance: bundle this section's live readings
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
    // The page's four sides, each holding its sections in the order the pencil gave them.
    val pages = remember(shown) { ServerPages.map { (_, ids) -> shown.filter { it.id in ids } } }

    HomePage(
        title = runtime.profile.label.ifBlank { "Server" },
        tone = HomeTone.Servers,
        onBack = onNavigateBack,
        lede = serverLede(runtime, link),
        tabs = ServerPages.map { it.first },
        actions = { haze ->
            onOpenConsole?.let { open ->
                GlassRound(AppIcons.SquareTerminal, "Console", haze) {
                    haptics.tick()
                    open()
                }
            }
            GlassRound(
                if (editing) AppIcons.Check else AppIcons.Edit,
                if (editing) "Done" else "Edit sections",
                haze,
                tint = if (editing) HomeTone.Servers else TextPrimary,
            ) {
                haptics.tick()
                editing = !editing
            }
            GlassRound(AppIcons.Settings, "Server settings", haze) {
                haptics.tick()
                onNavigateToSettings()
            }
        },
    ) { page ->
        if (profiles.size > 1) {
            item(key = "servers") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.forEach { profile ->
                        val isActive = profile.id == activeId
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isActive) HomeTone.Servers.copy(alpha = 0.24f) else WellFill)
                                .clickable {
                                    haptics.tick()
                                    selectedId = profile.id
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(runtimes[profile.id])
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = profile.label,
                                style = HealthDisplay,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (editing) {
            item(key = "editor") {
                WidgetEditor(
                    configs = sections,
                    onConfigsChanged = { viewModel.updateSectionOrder(encodeWidgetConfig(it)) },
                    onClose = { editing = false },
                    toneOf = { HomeTone.Servers },
                )
                Text(
                    "Advisories, Happening now, Services and Logged in only show when there is something in them.",
                    color = TextTertiary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            return@HomePage
        }
        val mine = pages.getOrNull(page).orEmpty()
        items(mine, key = { it.id }, contentType = { it.id }) { section ->
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
                SECTION_CONTAINERS -> ContainersCard(runtime, ask(ServerAiSection.DOCKER), link)
                SECTION_SYSTEM -> SystemCard(runtime, ask(ServerAiSection.SERVICES))
                SECTION_UPDATES -> ServerUpdatesCard(
                    runtime = runtime,
                    onRefresh = { viewModel.refreshNews(runtime.profile.id) },
                    onAskAi = ask(ServerAiSection.UPDATES),
                )
                SECTION_SESSIONS -> ServerSessionsCard(runtime, ask(ServerAiSection.SESSIONS))
            }
        }
        if (mine.isEmpty()) {
            item(key = "all-hidden") {
                PageRoom(HomeTone.Neutral) {
                    Text(
                        if (shown.isEmpty()) "Every section is switched off." else "Nothing on this side is switched on.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                    TonePill("Edit sections", HomeTone.Servers, Modifier.padding(top = 12.dp), icon = AppIcons.Edit) { editing = true }
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

/** The page's sides in the dock, and which sections stand on each. */
private val ServerPages = listOf(
    PageTab("Now", AppIcons.Activity) to setOf(SECTION_OVERVIEW, SECTION_ADVISORIES, SECTION_ACTIVITY, SECTION_HISTORY),
    PageTab("Machine", AppIcons.Cpu) to setOf(SECTION_COMPUTE, SECTION_MEMORY, SECTION_NETWORK, SECTION_STORAGE, SECTION_SENSORS),
    PageTab("Apps", AppIcons.Blocks) to setOf(SECTION_SERVICES, SECTION_CONTAINERS, SECTION_PROCESSES),
    PageTab("System", AppIcons.Settings) to setOf(SECTION_SYSTEM, SECTION_UPDATES, SECTION_SESSIONS),
)

@Composable
private fun ServerEmptyState(onNavigateToSettings: () -> Unit) {
    PageRoom(HomeTone.Servers) {
        Text(
            "Add an SSH host and DailyDash reads live stats straight from /proc. There is nothing to install on the server.",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        TonePill("Add a server", HomeTone.Servers, Modifier.padding(top = 14.dp), icon = AppIcons.Server, primary = true, onClick = onNavigateToSettings)
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
    ServerRoom(if (critical > 0) ServerCritical else ServerWarn) {
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
                Spacer(Modifier.height(12.dp))
            }
        }
        if (link != null && appAlerts.isNotEmpty()) {
            // What the apps themselves report, via the dashboard's collector.
            if (runtime.advisories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
            }
            DashboardAlertRows(link)
        }
        val hostKeyChanged = (runtime.connection as? ServerConnectionState.Offline)
            ?.reason is ServerError.HostKeyChanged
        if (hostKeyChanged) {
            Spacer(modifier = Modifier.height(6.dp))
            TonePill("Trust the new host key", ServerCritical, icon = AppIcons.Warning, primary = true, onClick = onTrustHostKey)
            Text(
                text = "Only do this if you rebuilt or reinstalled the server yourself.",
                color = TextSecondary,
                fontSize = 12.5.sp,
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
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ServerTag(advisory.category.name.lowercase().replaceFirstChar { it.uppercase() }, color)
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
    ServerRoom(ServerWarn) {
        SectionHeader(
            title = "Updates & news",
            icon = AppIcons.Download,
            accent = ServerWarn,
            trailing = runtime.hostProfile?.packageManager?.label,
            onAskAi = onAskAi,
        )
        if (news == null) {
            // First check still running: a placeholder shaped like the card, as elsewhere.
            RoomLoading(ServerWarn, bars = 3)
            return@ServerRoom
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
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
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
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
            )
            TonePill("Check again", ServerWarn, icon = AppIcons.Refresh) { onRefresh() }
        }
    }
}

@Composable
private fun ServerSessionsCard(runtime: ServerRuntime, onAskAi: (() -> Unit)? = null) {
    val sessions = runtime.snapshot?.sessions.orEmpty()
    ServerRoom(ServerMemory) {
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
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(92.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.tty,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1,
                )
                Text(
                    text = session.from.ifBlank { session.since },
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val DASHBOARD_REFRESH_MS = 30_000L
