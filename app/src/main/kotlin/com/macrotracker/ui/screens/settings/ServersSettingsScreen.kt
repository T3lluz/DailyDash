package com.macrotracker.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.server.ServerAuthMode
import com.macrotracker.data.server.ServerConnectionState
import com.macrotracker.data.server.ServerProfile
import com.macrotracker.data.server.parseServerTarget
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.ServerTag
import com.macrotracker.ui.components.StatLabel
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ServerTestUiState
import com.macrotracker.ui.viewmodel.ServerViewModel
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

/**
 * Server configuration: hosts, credentials, alert thresholds and the live
 * notification. Reached from Settings → Connections → Servers.
 */
@Composable
fun ServersSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenDashboard: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val runtimes by viewModel.runtimes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val haptics = rememberHaptics()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(viewModel.hasNotificationPermission()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsEnabled(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureChannels()
        notificationsGranted = viewModel.hasNotificationPermission()
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
            title = "Servers",
            subtitle = "SSH monitoring for your own machines",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ── Server list ──────────────────────────────────────────────────
        MacroCard(delayMs = 40) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Server,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Your servers",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (profiles.isNotEmpty()) {
                    Text(
                        "Open dashboard",
                        color = Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.click()
                                onOpenDashboard()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (profiles.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Nothing configured yet. DailyDash reads stats over plain SSH — no agent, " +
                        "no exporter, nothing to install on the server.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            profiles.forEach { profile ->
                Spacer(modifier = Modifier.height(10.dp))
                ServerListRow(
                    profile = profile,
                    statusColor = statusColor(runtimes[profile.id]?.connection),
                    statusText = statusText(runtimes[profile.id]?.connection),
                    onEdit = {
                        haptics.click()
                        editingId = profile.id
                        showForm = true
                        viewModel.resetTestState()
                    },
                    onToggle = { enabled ->
                        haptics.tick()
                        viewModel.setServerEnabled(profile.id, enabled)
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    haptics.click()
                    editingId = null
                    showForm = true
                    viewModel.resetTestState()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add server")
            }
        }

        if (showForm) {
            ServerEditorCard(
                existing = editingId?.let { id -> profiles.firstOrNull { it.id == id } },
                testState = testState,
                onTest = { target, user, port, mode, secret, passphrase ->
                    viewModel.testConnection(target, user, port, mode, secret, passphrase)
                },
                onSave = { target, label, user, port, mode, secret, passphrase ->
                    val result = viewModel.saveServer(
                        existingId = editingId,
                        label = label,
                        target = target,
                        username = user,
                        port = port,
                        authMode = mode,
                        secret = secret,
                        keyPassphrase = passphrase,
                    )
                    if (result is ServerViewModel.SaveResult.Saved) {
                        showForm = false
                        editingId = null
                        viewModel.resetTestState()
                    }
                    result
                },
                onDelete = editingId?.let { id ->
                    {
                        viewModel.deleteServer(id)
                        showForm = false
                        editingId = null
                    }
                },
                onCancel = {
                    showForm = false
                    editingId = null
                    viewModel.resetTestState()
                },
            )
        }

        // ── Notifications ────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(4.dp))
        MacroCard(delayMs = 70) {
            Text(
                "Notifications",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "DailyDash posts a push notification when a server needs you. Repeats of the same " +
                    "problem are held back for the cooldown below, so a full disk alerts once, not " +
                    "every five seconds.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )

            ToggleRow(
                title = "Server notifications",
                subtitle = if (notificationsGranted) {
                    "Alerts for outages, resource pressure and updates"
                } else {
                    "Tap to grant notification permission"
                },
                checked = settings.enabled && notificationsGranted,
                onCheckedChange = { enabled ->
                    haptics.tick()
                    if (enabled) requestNotifications() else viewModel.setNotificationsEnabled(false)
                },
            )

            if (settings.enabled && notificationsGranted) {
                HorizontalDivider(color = Border.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Critical",
                    subtitle = "Unreachable hosts, failed units, disks about to fill",
                    checked = settings.criticalEnabled,
                    onCheckedChange = { haptics.tick(); viewModel.setCriticalEnabled(it) },
                    accent = ServerCritical,
                )
                ToggleRow(
                    title = "Warnings",
                    subtitle = "CPU, memory, swap, temperature and container problems",
                    checked = settings.warningEnabled,
                    onCheckedChange = { haptics.tick(); viewModel.setWarningEnabled(it) },
                    accent = ServerWarn,
                )
                ToggleRow(
                    title = "Updates & security news",
                    subtitle = "Pending packages, security patches, reboot-required flags",
                    checked = settings.updatesEnabled,
                    onCheckedChange = { haptics.tick(); viewModel.setUpdatesEnabled(it) },
                    accent = Primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                StepperRow(
                    label = "Alert cooldown",
                    value = "${settings.alertCooldownMinutes} min",
                    onDecrease = {
                        viewModel.setAlertCooldownMinutes((settings.alertCooldownMinutes - 5).coerceAtLeast(1))
                    },
                    onIncrease = {
                        viewModel.setAlertCooldownMinutes((settings.alertCooldownMinutes + 5).coerceAtMost(240))
                    },
                )
            }
        }

        // ── Live notification ────────────────────────────────────────────
        MacroCard(delayMs = 90) {
            Text(
                "Live stats notification",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Keeps an ongoing notification with live gauges. It stays compact; tap More on it " +
                    "for the full panel with dials, history and every core.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            ToggleRow(
                title = "Show live notification",
                subtitle = if (profiles.isEmpty()) "Add a server first" else "Runs while it is switched on",
                checked = settings.liveNotificationEnabled,
                onCheckedChange = { enabled ->
                    haptics.tick()
                    if (enabled && !notificationsGranted) {
                        requestNotifications()
                    }
                    viewModel.setLiveNotificationEnabled(enabled && profiles.isNotEmpty())
                },
            )
            if (settings.liveNotificationEnabled) {
                ToggleRow(
                    title = "Open onto the full panel",
                    subtitle = "Off keeps it compact until you tap More",
                    checked = settings.liveNotificationDetailed,
                    onCheckedChange = { haptics.tick(); viewModel.setLiveNotificationDetailed(it) },
                )
                ToggleRow(
                    title = "Restart after reboot",
                    subtitle = "Bring the notification back when the phone starts",
                    checked = settings.startOnBoot,
                    onCheckedChange = { haptics.tick(); viewModel.setStartOnBoot(it) },
                )
                if (profiles.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatLabel("SERVER SHOWN")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.forEach { profile ->
                            val selected = settings.liveNotificationServerId == profile.id ||
                                (settings.liveNotificationServerId == null && profile == profiles.first())
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Primary.copy(alpha = 0.2f) else ServerWell)
                                    .clickable {
                                        haptics.tick()
                                        viewModel.setLiveNotificationServer(profile.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    profile.label,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Polling + thresholds ─────────────────────────────────────────
        MacroCard(delayMs = 110) {
            Text(
                "Polling & thresholds",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Each poll is one batched command over a session that stays open, so a 5-second " +
                    "refresh costs the server almost nothing.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            StepperRow(
                label = "Refresh while open",
                value = "${settings.pollSeconds}s",
                onDecrease = { viewModel.setPollSeconds((settings.pollSeconds - 1).coerceAtLeast(2)) },
                onIncrease = { viewModel.setPollSeconds((settings.pollSeconds + 1).coerceAtMost(60)) },
            )
            StepperRow(
                label = "Refresh while screen off",
                value = "${settings.backgroundPollSeconds}s",
                onDecrease = {
                    viewModel.setBackgroundPollSeconds((settings.backgroundPollSeconds - 5).coerceAtLeast(5))
                },
                onIncrease = {
                    viewModel.setBackgroundPollSeconds((settings.backgroundPollSeconds + 5).coerceAtMost(300))
                },
            )
            HorizontalDivider(color = Border.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 10.dp))
            ThresholdRow("CPU", settings.thresholds.cpuPercent, "%") { delta ->
                viewModel.updateThresholds { it.copy(cpuPercent = clampPercent(it.cpuPercent + delta)) }
            }
            ThresholdRow("Memory", settings.thresholds.memoryPercent, "%") { delta ->
                viewModel.updateThresholds { it.copy(memoryPercent = clampPercent(it.memoryPercent + delta)) }
            }
            ThresholdRow("Disk", settings.thresholds.diskPercent, "%") { delta ->
                viewModel.updateThresholds { it.copy(diskPercent = clampPercent(it.diskPercent + delta)) }
            }
            ThresholdRow("Swap", settings.thresholds.swapPercent, "%") { delta ->
                viewModel.updateThresholds { it.copy(swapPercent = clampPercent(it.swapPercent + delta)) }
            }
            ThresholdRow("Temperature", settings.thresholds.temperatureCelsius, "°C") { delta ->
                viewModel.updateThresholds {
                    it.copy(temperatureCelsius = (it.temperatureCelsius + delta).coerceIn(40, 110))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Load is judged per core, so the same threshold works on a 2-core VPS and a " +
                    "32-core box.",
                color = TextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

private fun clampPercent(value: Int) = value.coerceIn(50, 99)

@Composable
private fun ServerListRow(
    profile: ServerProfile,
    statusColor: Color,
    statusText: String,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .clickable(onClick = onEdit)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profile.displayTarget,
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ServerTag(statusText, statusColor)
        Spacer(modifier = Modifier.width(6.dp))
        Switch(checked = profile.enabled, onCheckedChange = onToggle)
    }
}

private fun statusColor(state: ServerConnectionState?): Color = when (state) {
    is ServerConnectionState.Online -> ServerGood
    is ServerConnectionState.Connecting -> ServerWarn
    is ServerConnectionState.Offline -> ServerCritical
    else -> TextSecondary
}

private fun statusText(state: ServerConnectionState?): String = when (state) {
    is ServerConnectionState.Online -> "ONLINE"
    is ServerConnectionState.Connecting -> "SYNC"
    is ServerConnectionState.Offline -> "OFFLINE"
    else -> "IDLE"
}

/**
 * Add / edit form.
 *
 * The address field takes `user@host`, a bare host, `host:port` or an `ssh://`
 * paste — including Tailscale MagicDNS names, which are just hostnames as far
 * as SSH is concerned.
 */
@Composable
private fun ServerEditorCard(
    existing: ServerProfile?,
    testState: ServerTestUiState,
    onTest: (String, String, String, ServerAuthMode, String, String) -> Unit,
    onSave: (String, String, String, String, ServerAuthMode, String, String) -> ServerViewModel.SaveResult,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val haptics = rememberHaptics()
    var target by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { "${it.username}@${it.host}" } ?: "")
    }
    var label by rememberSaveable(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var username by rememberSaveable(existing?.id) { mutableStateOf(existing?.username.orEmpty()) }
    var port by rememberSaveable(existing?.id) { mutableStateOf(existing?.port?.toString() ?: "22") }
    var authMode by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.authMode ?: ServerAuthMode.PASSWORD)
    }
    var secret by rememberSaveable(existing?.id) { mutableStateOf("") }
    var passphrase by rememberSaveable(existing?.id) { mutableStateOf("") }
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable(existing?.id) { mutableStateOf<String?>(null) }

    // Typing `deploy@box` should fill the username field rather than making the
    // user enter the same thing twice.
    LaunchedEffect(target) {
        val parsed = parseServerTarget(target)
        parsed?.username?.takeIf { it.isNotBlank() }?.let { username = it }
        parsed?.port?.let { port = it.toString() }
    }

    MacroCard(delayMs = 50, borderColor = Primary.copy(alpha = 0.4f)) {
        Text(
            if (existing == null) "New server" else "Edit ${existing.label}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))

        StatLabel("ADDRESS")
        MacroTextField(
            value = target,
            onValueChange = { target = it; error = null },
            placeholder = "fredde@100.101.102.103  or  box.tail1234.ts.net",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(2f)) {
                StatLabel("USERNAME")
                MacroTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    placeholder = "root",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                StatLabel("PORT")
                MacroTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    placeholder = "22",
                    keyboardType = KeyboardType.Number,
                )
            }
        }

        StatLabel("NAME (OPTIONAL)")
        MacroTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = "Home server",
        )

        Spacer(modifier = Modifier.height(4.dp))
        StatLabel("AUTHENTICATION")
        Spacer(modifier = Modifier.height(6.dp))
        SettingsSegmentedToggle(
            options = listOf(
                ServerAuthMode.PASSWORD to "Password",
                ServerAuthMode.PRIVATE_KEY to "SSH key",
            ),
            selected = authMode,
            onSelect = { authMode = it; error = null },
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (authMode == ServerAuthMode.PASSWORD) {
            MacroTextField(
                value = secret,
                onValueChange = { secret = it; error = null },
                placeholder = if (existing == null) "Password" else "Password (leave blank to keep)",
                visualTransformation = if (revealSecret) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealSecret = !revealSecret }) {
                        Icon(
                            imageVector = if (revealSecret) {
                                AppIcons.EyeOff
                            } else {
                                AppIcons.Eye
                            },
                            contentDescription = if (revealSecret) "Hide password" else "Show password",
                            tint = TextSecondary,
                        )
                    }
                },
            )
        } else {
            MacroTextField(
                value = secret,
                onValueChange = { secret = it; error = null },
                placeholder = if (existing == null) {
                    "Paste the private key (-----BEGIN OPENSSH PRIVATE KEY-----)"
                } else {
                    "Paste a new private key, or leave blank to keep"
                },
                singleLine = false,
            )
            MacroTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                placeholder = "Key passphrase (optional)",
                visualTransformation = PasswordVisualTransformation(),
            )
        }

        Text(
            "Credentials are encrypted with an Android Keystore key and never leave the phone.",
            color = TextTertiary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        when (testState) {
            is ServerTestUiState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Connecting…", color = TextSecondary, fontSize = 12.sp)
            }
            is ServerTestUiState.Success -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ServerGood.copy(alpha = 0.12f))
                    .padding(10.dp),
            ) {
                Text(
                    "Connected · ${testState.host.prettyName.ifBlank { "unknown distribution" }}",
                    color = ServerGood,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${testState.host.kernel} · ${testState.host.packageManager.label} · " +
                        "${testState.host.cpuCores} cores",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = testState.fingerprint,
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Check this fingerprint matches `ssh-keyscan` on the server if you are on an " +
                        "untrusted network.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
            is ServerTestUiState.Failure -> Text(
                text = testState.error.message,
                color = ServerCritical,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ServerCritical.copy(alpha = 0.1f))
                    .padding(10.dp),
            )
            ServerTestUiState.Idle -> Unit
        }

        error?.let {
            Text(
                text = it,
                color = ServerCritical,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onTest(target, username, port, authMode, secret, passphrase)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Test", fontSize = 13.sp)
            }
            Button(
                onClick = {
                    haptics.click()
                    val result = onSave(target, label, username, port, authMode, secret, passphrase)
                    error = (result as? ServerViewModel.SaveResult.Invalid)?.message
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f),
            ) {
                Text("Save", fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", fontSize = 13.sp, color = TextSecondary)
            }
            if (onDelete != null) {
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onDelete()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = null,
                        tint = ServerCritical,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", fontSize = 13.sp, color = ServerCritical)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color = Primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) accent else Border),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        StepperButton("−") { haptics.tick(); onDecrease() }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp),
            maxLines = 1,
        )
        StepperButton("+") { haptics.tick(); onIncrease() }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThresholdRow(label: String, value: Int, unit: String, onDelta: (Int) -> Unit) {
    StepperRow(
        label = "$label alert above",
        value = "$value$unit",
        onDecrease = { onDelta(-5) },
        onIncrease = { onDelta(5) },
    )
}
