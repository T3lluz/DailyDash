package com.macrotracker.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.remote.AiAccess
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiModels
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.components.AiAccessBadge
import com.macrotracker.ui.components.AiPickerSheet
import com.macrotracker.ui.components.AiProviderGrid
import com.macrotracker.ui.components.AiProviderLogo
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.modelsFor
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.tagline
import com.macrotracker.ui.navigation.SettingsRoutes
import com.macrotracker.ui.navigation.SubScreenRoutes
import com.macrotracker.ui.screens.ai.HermesModelSelector
import com.macrotracker.ui.screens.ai.SearchBox
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.openUrl
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AiProviderStatus
import com.macrotracker.ui.viewmodel.AiTest
import com.macrotracker.ui.viewmodel.AiUiState
import com.macrotracker.ui.viewmodel.AiSetupViewModel
import com.macrotracker.ui.viewmodel.HermesViewModel

/**
 * Settings → AI, after Tilesmith for Windows: the AI in use (Change opens the same sheet as
 * Tilesmith's composer, Test asks it for one word), every provider with how the phone is let
 * in (your account, an API key, the dashboard), and the setup of the one you tap: Claude's
 * sign-in, Cursor through the dashboard, a key, and the provider's own model list.
 */
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: AiSetupViewModel = hiltViewModel(),
    hermesViewModel: HermesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var focused by rememberSaveable { mutableStateOf<AiProvider?>(null) }
    var picking by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val focus = focused ?: state.selected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .subScreenBottomPadding(),
    ) {
        SubScreenHeader(title = "AI", subtitle = "Your plans, keys and models", onNavigateBack = onNavigateBack)
        Spacer(Modifier.height(10.dp))

        InUseCard(
            state = state,
            onChange = { haptics.tick(); picking = true },
            onTest = { haptics.tick(); viewModel.test() },
        )
        AnimatedVisibility(visible = state.test != null) {
            state.test?.let { TestBanner(it, onDismiss = viewModel::dismissTest) }
        }

        SectionLabel("Providers")
        AiProviderGrid(state, focused = focus, onFocus = { haptics.tick(); focused = it })

        Spacer(Modifier.height(12.dp))
        AnimatedContent(targetState = focus, label = "ai_setup") { p ->
            state.status(p)?.let { status ->
                SetupCard(
                    state = state,
                    status = status,
                    viewModel = viewModel,
                    hermesViewModel = hermesViewModel,
                    onOpenRoute = onOpenRoute,
                )
            }
        }
    }

    if (picking) {
        AiPickerSheet(viewModel = viewModel, onDismiss = { picking = false }, onManage = null)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp),
    )
}

private val CardShape = RoundedCornerShape(22.dp)

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, Border, CardShape),
    ) { content() }
}

// ── The AI in use ───────────────────────────────────────────────────────────

@Composable
private fun InUseCard(state: AiUiState, onChange: () -> Unit, onTest: () -> Unit) {
    val cur = state.current
    Card {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cur != null) AiProviderLogo(cur.provider, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("IN USE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.8.sp)
                Text(
                    cur?.modelLabel ?: "Nothing set up",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cur?.provider?.displayName ?: "", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    if (cur != null) AiAccessBadge(cur.access, label = if (cur.ready) cur.access.label else "Not set up")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillAction("Test", AppIcons.Bolt, filled = false, enabled = cur?.ready == true && state.test != AiTest.Running, modifier = Modifier.weight(1f), onClick = onTest)
            PillAction("Change", AppIcons.Sparkles, filled = true, modifier = Modifier.weight(1f), onClick = onChange)
        }
    }
}

@Composable
private fun TestBanner(test: AiTest, onDismiss: () -> Unit) {
    val (tone, title, body) = when (test) {
        AiTest.Running -> Triple(ServerBrand, "Asking it for one word…", null)
        is AiTest.Passed -> Triple(Success, "${test.provider.displayName} answered in ${"%.1f".format(test.seconds)} s", "You're all set.")
        is AiTest.Failed -> Triple(Error, "It didn't answer", test.message)
    }
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(enabled = test != AiTest.Running, onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (test == AiTest.Running) {
            LoadingSpinner(color = tone, size = 16.dp)
        } else {
            Icon(if (test is AiTest.Passed) AppIcons.CheckCircleFilled else AppIcons.Warning, null, tint = tone, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (body != null) Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}

// ── Setup for one provider ──────────────────────────────────────────────────

@Composable
private fun SetupCard(
    state: AiUiState,
    status: AiProviderStatus,
    viewModel: AiSetupViewModel,
    hermesViewModel: HermesViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val p = status.provider
    val haptics = rememberHaptics()
    LaunchedEffect(p, status.ready) { if (status.ready) viewModel.refreshModels(p) }
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AiProviderLogo(p, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(p.tagline, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (state.selected == p) {
                InUseTag()
            } else {
                PillAction("Use", AppIcons.Check, filled = true, enabled = status.ready, onClick = { haptics.confirm(); viewModel.select(p) })
            }
        }
        Hairline()
        when (p) {
            AiProvider.ANTHROPIC -> {
                ClaudeAccountRow(state, viewModel)
                if (status.access != AiAccess.ACCOUNT || state.keys[p].orEmpty().isNotBlank()) {
                    Hairline()
                    KeyRow(p, saved = state.keys[p].orEmpty(), optional = true, onSave = { viewModel.saveKey(p, it) })
                }
            }
            AiProvider.CURSOR -> DashboardAccountRow(
                status = status,
                lines = listOf(
                    "Cursor has no sign-in for apps, so the dashboard runs Cursor's own CLI on your plan, and DailyDash asks it for each answer.",
                    "To change the account, run cursor-agent login in the console.",
                ),
                onSetUp = { onOpenRoute(SettingsRoutes.CONNECTIONS) },
                onConsole = { onOpenRoute(SubScreenRoutes.CONSOLE) },
            )
            AiProvider.HERMES -> {
                DashboardAccountRow(
                    status = status,
                    lines = listOf("The model your dashboard is set to. Changing it here changes it for the web and Tech support too."),
                    onSetUp = { onOpenRoute(SettingsRoutes.CONNECTIONS) },
                    onConsole = null,
                )
                if (status.ready) {
                    Hairline()
                    Column(Modifier.padding(16.dp)) {
                        RowTitle(AppIcons.Gauge, "Model")
                        Spacer(Modifier.height(10.dp))
                        HermesModelSelector(viewModel = hermesViewModel)
                    }
                }
            }
            AiProvider.GEMINI, AiProvider.OPENAI -> KeyRow(p, saved = state.keys[p].orEmpty(), optional = false, onSave = { viewModel.saveKey(p, it) })
        }
        if (p != AiProvider.HERMES) {
            Hairline()
            ModelList(state, status, onPick = { haptics.tick(); viewModel.setModel(p, it) }, onRefresh = { viewModel.refreshModels(p, force = true) })
        }
    }
}

@Composable
private fun InUseTag() {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ServerBrand.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Check, null, tint = ServerBrand, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("In use", color = ServerBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hairline() = HorizontalDivider(color = Border, thickness = 1.dp)

@Composable
private fun RowTitle(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(ServerBrand.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ServerBrand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Claude: sign in with the plan (the code comes back on Claude's page), or signed in as who. */
@Composable
private fun ClaudeAccountRow(state: AiUiState, viewModel: AiSetupViewModel) {
    val claude = state.claude
    var code by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        RowTitle(AppIcons.Account, "Account") { if (claude.connected) AiAccessBadge(AiAccess.ACCOUNT, label = "Signed in") }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                claude.connected -> listOfNotNull(claude.email ?: "Signed in", claude.plan?.let { "$it plan" }).joinToString(" · ")
                claude.awaitingCode -> "Approve DailyDash on Claude's page, then paste the code it shows."
                else -> "Sign in with Claude Pro, Max, Team or Enterprise. Answers use your plan, not API credits."
            },
            fontSize = 12.5.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        when {
            claude.awaitingCode && !claude.connected -> {
                Field(value = code, onValue = { code = it }, hint = "Paste code#state", mono = true)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction(if (claude.busy) "Signing in…" else "Finish", AppIcons.Check, filled = true, enabled = code.isNotBlank() && !claude.busy, modifier = Modifier.weight(1f)) {
                        viewModel.finishClaudeLogin(code)
                        code = ""
                    }
                    PillAction("Cancel", null, filled = false, enabled = !claude.busy, modifier = Modifier.weight(1f)) {
                        viewModel.cancelClaudeLogin()
                        code = ""
                    }
                }
            }
            claude.connected -> PillAction("Sign out", AppIcons.Close, filled = false, onClick = viewModel::signOutClaude)
            else -> PillAction("Sign in with Claude", AppIcons.Account, filled = true, modifier = Modifier.fillMaxWidth(), onClick = viewModel::startClaudeLogin)
        }
        claude.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = if (claude.connected) Success else TextSecondary)
        }
    }
}

/** Cursor and Hermes: they live on the dashboard; this says whether it is set and where to change things. */
@Composable
private fun DashboardAccountRow(status: AiProviderStatus, lines: List<String>, onSetUp: () -> Unit, onConsole: (() -> Unit)?) {
    Column(Modifier.padding(16.dp)) {
        RowTitle(if (status.provider == AiProvider.CURSOR) AppIcons.Account else AppIcons.Server, if (status.provider == AiProvider.CURSOR) "Account" else "Dashboard") {
            if (status.ready) AiAccessBadge(status.access, label = if (status.access == AiAccess.ACCOUNT) "Your plan" else "Connected")
        }
        Spacer(Modifier.height(8.dp))
        Text(status.detail, fontSize = 13.sp, color = TextPrimary)
        lines.forEach { Text(it, fontSize = 12.sp, color = TextTertiary, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!status.ready) {
                PillAction("Set your dashboard", AppIcons.Server, filled = true, modifier = Modifier.weight(1f), onClick = onSetUp)
            } else if (onConsole != null) {
                PillAction("Open the console", AppIcons.Terminal, filled = false, modifier = Modifier.weight(1f), onClick = onConsole)
            }
        }
    }
}

/** A provider's API key: masked, with show, save, clear, and where to get one. */
@Composable
private fun KeyRow(provider: AiProvider, saved: String, optional: Boolean, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var draft by remember(provider, saved) { mutableStateOf(saved) }
    var visible by remember { mutableStateOf(false) }
    val dirty = draft.trim() != saved
    val formatOk = AiApiClient.looksLikeValidKey(provider, draft)
    Column(Modifier.padding(16.dp)) {
        RowTitle(AppIcons.Key, if (optional) "API key (optional)" else "API key") {
            if (saved.isNotBlank()) AiAccessBadge(AiAccess.API_KEY, label = "Saved")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (optional) "Used only while you're not signed in. Billed per token by Anthropic." else "Stored on this phone only. Requests go straight to ${provider.displayName}.",
            fontSize = 12.sp,
            color = TextTertiary,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        Field(
            value = draft,
            onValue = { draft = it },
            hint = AiApiClient.keyPlaceholder(provider),
            mono = true,
            secret = !visible,
            error = !formatOk,
            trailing = {
                Icon(
                    if (visible) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = if (visible) "Hide key" else "Show key",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { visible = !visible },
                )
            },
        )
        if (!formatOk) {
            Text("That doesn't look like a ${provider.displayName} key (${AiApiClient.keyPlaceholder(provider)})", fontSize = 11.5.sp, color = Warning, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillAction("Save", AppIcons.Check, filled = true, enabled = dirty && draft.isNotBlank(), modifier = Modifier.weight(1f)) { onSave(draft.trim()) }
            if (saved.isNotBlank()) {
                PillAction("Clear", null, filled = false, modifier = Modifier.weight(1f)) {
                    draft = ""
                    onSave("")
                }
            } else {
                AiApiClient.keyUrl(provider)?.let { url ->
                    PillAction("Get a key", AppIcons.ExternalLink, filled = false, modifier = Modifier.weight(1f)) { context.openUrl(url) }
                }
            }
        }
    }
}

/** The provider's models as radio rows: its own list (refreshable), search when it is long. */
@Composable
private fun ModelList(state: AiUiState, status: AiProviderStatus, onPick: (String) -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val p = status.provider
    val models = modelsFor(state, p)
    var query by remember(p) { mutableStateOf("") }
    val loading = p in state.loadingModels
    val error = state.modelErrors[p]
    val shown = models.filter { query.isBlank() || "${it.name} ${it.id}".contains(query.trim(), ignoreCase = true) }
    Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            RowTitle(AppIcons.Sparkles, "Model") {
                if (p != AiProvider.ANTHROPIC && status.ready) {
                    if (loading) {
                        LoadingSpinner(color = ServerBrand, size = 16.dp)
                    } else {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = "Refresh the list",
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRefresh).padding(7.dp),
                        )
                    }
                }
            }
            Text(
                when {
                    error != null -> "Couldn't load the list: $error"
                    p == AiProvider.ANTHROPIC -> "Anthropic's current models"
                    !status.ready -> "Set it up to see its models"
                    loading -> "Loading the latest list…"
                    else -> AiModels.lastFetched(context, p).takeIf { it > 0 }?.let { "${models.size} models · updated ${ago(it)}" } ?: "Suggested models"
                },
                fontSize = 12.sp,
                color = if (error != null) Warning else TextTertiary,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp),
            )
            if (models.size > 8) {
                Spacer(Modifier.height(10.dp))
                SearchBox(value = query, onValueChange = { query = it }, hint = "Find a model…")
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            shown.forEach { m ->
                val on = m.id == status.model
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onPick(m.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(if (on) 5.dp else 1.5.dp, if (on) ServerBrand else TextTertiary, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        m.note?.let { Text(it, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(if (query.isBlank()) "No models listed yet." else "Nothing matches.", fontSize = 12.5.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
    }
}

private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun PillAction(
    label: String,
    icon: ImageVector?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val bg = when {
        !enabled -> ServerWell
        filled -> ServerBrand
        else -> ServerWell
    }
    val fg = when {
        !enabled -> TextTertiary
        filled -> OnAccent
        else -> TextPrimary
    }
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(bg)
            .then(if (filled && enabled) Modifier else Modifier.border(1.dp, Border, RoundedCornerShape(21.dp)))
            .clickable(enabled = enabled) { haptics.tick(); onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Field(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    mono: Boolean = false,
    secret: Boolean = false,
    error: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceChrome)
            .border(1.dp, if (error) Warning.copy(alpha = 0.7f) else Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(ServerBrand),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = TextTertiary, fontSize = 14.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
                inner()
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
