package com.macrotracker.ui.screens.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.AnthropicModels
import com.macrotracker.data.remote.OpenRouterModels
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.SettingsViewModel
import com.macrotracker.ui.theme.AppIcons

@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedKey by viewModel.geminiApiKey.collectAsState()
    val savedOpenAiKey by viewModel.openAiApiKey.collectAsState()
    val savedOpenRouterKey by viewModel.openRouterApiKey.collectAsState()
    val openRouterModelId by viewModel.openRouterModelId.collectAsState()
    val savedAnthropicKey by viewModel.anthropicApiKey.collectAsState()
    val anthropicModelId by viewModel.anthropicModelId.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val claudeConnected by viewModel.claudeConnected.collectAsState()
    val claudeEmail by viewModel.claudeAccountEmail.collectAsState()
    val claudePlan by viewModel.claudeSubscriptionLabel.collectAsState()
    val claudeAwaitingCode by viewModel.claudeAwaitingCode.collectAsState()
    val claudeBusy by viewModel.claudeBusy.collectAsState()
    val claudeMessage by viewModel.claudeMessage.collectAsState()
    var claudeCode by remember { mutableStateOf("") }

    val activeSavedKey = when (aiProvider) {
        AiProvider.GEMINI -> savedKey
        AiProvider.OPENAI -> savedOpenAiKey
        AiProvider.OPENROUTER -> savedOpenRouterKey
        AiProvider.ANTHROPIC -> savedAnthropicKey
    }
    var draftKey by remember(aiProvider, activeSavedKey) { mutableStateOf(activeSavedKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val isDirty = draftKey.trim() != activeSavedKey
    val hasKey = activeSavedKey.isNotBlank() ||
        (aiProvider == AiProvider.ANTHROPIC && claudeConnected)
    val keyFormatOk = AiApiClient.looksLikeValidKey(aiProvider, draftKey)
    val keyFeedback: String? = when {
        draftKey.isNotBlank() && !keyFormatOk -> when (aiProvider) {
            AiProvider.GEMINI -> "Doesn't look like a Gemini key (should start with AIza…)"
            AiProvider.OPENAI -> "Doesn't look like an OpenAI key (should start with sk-…)"
            AiProvider.OPENROUTER -> "Doesn't look like an OpenRouter key (should start with sk-or-…)"
            AiProvider.ANTHROPIC -> "Doesn't look like an Anthropic key (should start with sk-ant-…)"
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 120.dp),
    ) {
        SettingsSubScreenHeader(
            title = "AI",
            subtitle = "Provider, Claude login, API keys, and models",
            onNavigateBack = onNavigateBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        MacroCard(delayMs = 50) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Key,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  Provider",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                if (hasKey) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = AppIcons.CheckCircleFilled,
                        contentDescription = "Key saved",
                        tint = Success,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose Gemini, OpenAI, OpenRouter, or Claude for food estimates, label scanning, chat, and weather tips.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            AiProviderToggle(
                selected = aiProvider,
                onSelect = { provider ->
                    haptics.tick()
                    viewModel.setAiProvider(provider)
                    keySaved = false
                },
            )

            if (aiProvider == AiProvider.ANTHROPIC) {
                Spacer(modifier = Modifier.height(16.dp))
                ClaudeSubscriptionBlock(
                    connected = claudeConnected,
                    email = claudeEmail,
                    plan = claudePlan,
                    awaitingCode = claudeAwaitingCode,
                    busy = claudeBusy,
                    message = claudeMessage,
                    code = claudeCode,
                    onCodeChange = { claudeCode = it },
                    onConnect = {
                        haptics.tick()
                        viewModel.startClaudeLogin()
                    },
                    onFinish = {
                        haptics.confirm()
                        viewModel.finishClaudeLogin(claudeCode)
                        claudeCode = ""
                    },
                    onCancel = {
                        haptics.reject()
                        viewModel.cancelClaudeLogin()
                        claudeCode = ""
                    },
                    onDisconnect = {
                        haptics.reject()
                        viewModel.disconnectClaude()
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Optional API key",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "${aiProvider.displayName} API Key",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = AiApiClient.keyHint(aiProvider),
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = draftKey,
                onValueChange = {
                    draftKey = it
                    keySaved = false
                },
                placeholder = {
                    Text(
                        AiApiClient.keyPlaceholder(aiProvider),
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = {
                        haptics.tick()
                        keyVisible = !keyVisible
                    }) {
                        Icon(
                            imageVector = if (keyVisible) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = if (keyVisible) "Hide key" else "Show key",
                            tint = TextSecondary,
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedBorderColor = if (!keyFormatOk) Error else Primary,
                    unfocusedBorderColor = if (!keyFormatOk) Error else Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                ),
            )

            if (keyFeedback != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.Warning,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = keyFeedback,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroButton(
                    text = if (keySaved) "Saved ✓" else "Save Key",
                    onClick = {
                        haptics.confirm()
                        viewModel.saveApiKey(aiProvider, draftKey)
                        keySaved = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDirty || !hasKey,
                )
                if (hasKey) {
                    MacroButton(
                        text = "Clear",
                        onClick = {
                            haptics.reject()
                            draftKey = ""
                            viewModel.saveApiKey(aiProvider, "")
                            keySaved = false
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MacroCard(delayMs = 100) {
            Text(
                text = when (aiProvider) {
                    AiProvider.OPENROUTER -> "OpenRouter Model"
                    AiProvider.ANTHROPIC -> "Claude Model"
                    else -> "AI Model"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (aiProvider == AiProvider.OPENROUTER) {
                Text(
                    text = "Pick a cheap vision-capable model. Prices are OpenRouter list rates (USD per 1M tokens) and may change. DailyDash calls are short, so even paid models stay fractions of a cent.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OpenRouterModelSelector(
                    selectedId = openRouterModelId,
                    onSelect = { modelId ->
                        haptics.tick()
                        viewModel.setOpenRouterModelId(modelId)
                    },
                )
            } else if (aiProvider == AiProvider.ANTHROPIC) {
                Text(
                    text = if (claudeConnected) {
                        "Connected Claude uses your plan's usage, not console API credits. " +
                            "Opus is still the one to pick when a server is actually broken."
                    } else {
                        "Chat turns carry the whole conversation plus any server context, " +
                            "so they cost more than the one-shot macro estimates if you use an " +
                            "API key. Connect Claude above to bill the subscription instead."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                AnthropicModelSelector(
                    selectedId = anthropicModelId,
                    onSelect = { modelId ->
                        haptics.tick()
                        viewModel.setAnthropicModelId(modelId)
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = AiApiClient.modelLabel(aiProvider),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                        Text(
                            text = when (aiProvider) {
                                AiProvider.GEMINI -> "Fast · Free tier · Sufficient for nutrition"
                                AiProvider.OPENAI -> "Fast · Vision-capable · gpt-4o-mini"
                                AiProvider.OPENROUTER -> ""
                                AiProvider.ANTHROPIC -> ""
                            },
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                    Text(
                        text = if (hasKey) "Active" else "No Key",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasKey) Success else Error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeSubscriptionBlock(
    connected: Boolean,
    email: String?,
    plan: String?,
    awaitingCode: Boolean,
    busy: Boolean,
    message: String?,
    code: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(10.dp))
            .border(1.dp, Border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppIcons.Account,
                contentDescription = null,
                tint = if (connected) Success else Primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (connected) "Claude connected" else "Claude subscription",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                connected && !email.isNullOrBlank() && !plan.isNullOrBlank() ->
                    "$email · $plan plan"
                connected && !email.isNullOrBlank() -> email
                connected -> "Signed in — usage comes from your Claude plan"
                else ->
                    "Sign in with Claude Pro, Max, Team, or Enterprise. Same login T3 Code uses — no console API key."
            },
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp,
        )

        if (awaitingCode && !connected) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Approve DailyDash in the browser, then paste the code from the callback page.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                placeholder = {
                    Text("Paste code#state here", color = TextSecondary, fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroButton(
                    text = if (busy) "Connecting…" else "Finish login",
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                    enabled = code.isNotBlank() && !busy,
                )
                MacroButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.SECONDARY,
                    enabled = !busy,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (connected) {
                    MacroButton(
                        text = "Disconnect",
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.SECONDARY,
                    )
                } else {
                    MacroButton(
                        text = "Connect Claude",
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = if (connected && message.startsWith("Connected")) Success else TextSecondary,
            )
        }
    }
}

@Composable
private fun AiProviderToggle(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(12.dp))
            .border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Four providers no longer fit one row without truncating "OpenRouter".
            AiProvider.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { provider ->
                        val isSelected = provider == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) SurfaceElevated else Color.Transparent)
                                .border(1.dp, if (isSelected) BorderStrong else Color.Transparent, RoundedCornerShape(9.dp))
                                .clickable { onSelect(provider) }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = provider.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenRouterModelSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = remember(selectedId) {
        OpenRouterModels.options.firstOrNull { it.id == selectedId }
            ?: OpenRouterModels.options.first()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = {
                Text(
                    buildString {
                        append(selected.priceLabel)
                        append(" · ")
                        append(selected.approxRequestCostLabel)
                        if (selected.supportsVision) append(" · Vision")
                        if (selected.recommended) append(" · Best value")
                    },
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Background,
                unfocusedContainerColor = Background,
                focusedBorderColor = Primary,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = Primary,
                unfocusedLabelColor = TextSecondary,
                focusedSupportingTextColor = TextSecondary,
                unfocusedSupportingTextColor = TextSecondary,
                cursorColor = Primary,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            OpenRouterModels.options.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                if (model.recommended) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Best value",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                    )
                                }
                            }
                            Text(
                                text = "${model.priceLabel} · ${model.approxRequestCostLabel}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                            )
                            Text(
                                text = model.blurb,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp,
                            )
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    },
                    trailingIcon = if (model.id == selectedId) {
                        {
                            Icon(
                                imageVector = AppIcons.CheckCircleFilled,
                                contentDescription = "Selected",
                                tint = Primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnthropicModelSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = remember(selectedId) { AnthropicModels.find(selectedId) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = {
                Text(
                    buildString {
                        append(selected.priceLabel)
                        append(" · ")
                        append(selected.approxTurnCostLabel)
                        if (selected.recommended) append(" · Recommended")
                    },
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Background,
                unfocusedContainerColor = Background,
                focusedBorderColor = Primary,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = Primary,
                unfocusedLabelColor = TextSecondary,
                focusedSupportingTextColor = TextSecondary,
                unfocusedSupportingTextColor = TextSecondary,
                cursorColor = Primary,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AnthropicModels.options.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                if (model.recommended) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Recommended",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                    )
                                }
                            }
                            Text(
                                text = "${model.priceLabel} · ${model.approxTurnCostLabel}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                            )
                            Text(
                                text = model.blurb,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp,
                            )
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    },
                    trailingIcon = if (model.id == selectedId) {
                        {
                            Icon(
                                imageVector = AppIcons.CheckCircleFilled,
                                contentDescription = "Selected",
                                tint = Primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
