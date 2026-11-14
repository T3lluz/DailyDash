package com.macrotracker.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.remote.AiAccess
import com.macrotracker.data.remote.AiModel
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.screens.ai.ModelLogo
import com.macrotracker.ui.screens.ai.PickerTab
import com.macrotracker.ui.screens.ai.RailHeading
import com.macrotracker.ui.screens.ai.SearchBox
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AiProviderStatus
import com.macrotracker.ui.viewmodel.AiUiState
import com.macrotracker.ui.viewmodel.AiSetupViewModel

/*
 * The app's AI picker, the same everywhere the AI is chosen (Settings → AI, Tilesmith's
 * composer, food estimates): each provider with its maker's logo, how the phone is let in
 * (your account, an API key, or the dashboard: [AiAccessBadge]), and its models in one
 * sheet, drawn like the AI tab's Hermes model sheet.
 */

/** The maker's logo for a provider (Hermes' face for the dashboard's own model). */
fun AiProvider.logoBrand(): String = when (this) {
    AiProvider.ANTHROPIC -> "anthropic"
    AiProvider.CURSOR -> "cursor"
    AiProvider.GEMINI -> "google"
    AiProvider.OPENAI -> "openai"
    AiProvider.HERMES -> ""
}

@Composable
fun AiProviderLogo(provider: AiProvider, size: Dp, modifier: Modifier = Modifier) {
    ModelLogo(provider.logoBrand(), size, modifier)
}

/** What a picker calls each provider, next to its name. */
val AiProvider.tagline: String
    get() = when (this) {
        AiProvider.ANTHROPIC -> "Your Claude plan, or an API key"
        AiProvider.CURSOR -> "Your Cursor plan, through the dashboard"
        AiProvider.HERMES -> "The dashboard's own model"
        AiProvider.GEMINI -> "Google's models, with a free key"
        AiProvider.OPENAI -> "GPT models, with an API key"
    }

private fun AiAccess.icon(): ImageVector = when (this) {
    AiAccess.ACCOUNT -> AppIcons.Account
    AiAccess.API_KEY -> AppIcons.Key
    AiAccess.DASHBOARD -> AppIcons.Server
    AiAccess.NONE -> AppIcons.Add
}

fun AiAccess.tone(): Color = when (this) {
    AiAccess.ACCOUNT -> Success
    AiAccess.API_KEY -> Primary
    AiAccess.DASHBOARD -> ServerBrand
    AiAccess.NONE -> TextTertiary
}

/**
 * How the phone is let in to a provider: **Account** (signed in; the plan's usage), **API key**
 * (billed per token) or **Dashboard** (no key on the phone). Shown next to every provider in
 * every picker so the two kinds never look alike.
 */
@Composable
fun AiAccessBadge(access: AiAccess, modifier: Modifier = Modifier, label: String = access.label) {
    val tone = access.tone()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.13f))
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(access.icon(), contentDescription = null, tint = tone, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tone, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * The AI in use as one pill, like the AI tab's model pill: the maker's logo, the model, and a
 * dot in its access colour. A tap opens [AiPickerSheet]. [compact] is the logo and dot alone,
 * for a composer that needs the room.
 */
@Composable
fun AiPill(status: AiProviderStatus?, onClick: () -> Unit, modifier: Modifier = Modifier, open: Boolean = false, compact: Boolean = false) {
    val haptics = rememberHaptics()
    val name = status?.let { if (it.provider == AiProvider.HERMES) "Hermes" else it.modelLabel } ?: "Pick an AI"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (open) ServerBrand.copy(alpha = 0.16f) else ServerWell)
            .border(1.dp, if (open) ServerBrand.copy(alpha = 0.5f) else Border, RoundedCornerShape(999.dp))
            .clickable { haptics.tick(); onClick() }
            .semantics { contentDescription = "AI: $name, ${status?.access?.label ?: "not set up"}" }
            .padding(start = 4.dp, end = if (compact) 6.dp else 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (status != null) AiProviderLogo(status.provider, 22.dp) else Icon(AppIcons.Sparkles, null, tint = ServerBrand, modifier = Modifier.size(22.dp))
            if (status != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ServerWell)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(status.access.tone()),
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 128.dp),
            )
        }
        Spacer(Modifier.width(3.dp))
        Icon(AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
    }
}

/**
 * Every provider and its models in one sheet: the ready ones with their models to pick from
 * (search across all of them), then the ones still to set up, which go to Settings → AI
 * ([onManage]). Picking a model switches to its provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPickerSheet(
    viewModel: AiSetupViewModel,
    onDismiss: () -> Unit,
    onManage: (() -> Unit)?,
    /** Shown above the list, e.g. "Pictures need Claude, Gemini or OpenAI". */
    note: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        state.providers.filter { it.ready }.forEach { viewModel.refreshModels(it.provider) }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding().imePadding()) {
            AiModelList(
                state = state,
                note = note,
                onPick = { provider, model ->
                    haptics.tick()
                    viewModel.select(provider)
                    if (model != null) viewModel.setModel(provider, model)
                    onDismiss()
                },
                onManage = onManage?.let { m -> { onDismiss(); m() } },
            )
        }
    }
}

@Composable
private fun ColumnScope.AiModelList(
    state: AiUiState,
    note: String?,
    onPick: (AiProvider, String?) -> Unit,
    onManage: (() -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    // The tabs: the ones that work first, in their own order, then the ones still to set up.
    // Where there is nowhere to set one up from (Settings → AI is already this page's parent),
    // only the ones that work are listed.
    val providers = remember(state.providers, onManage == null) {
        state.providers.filter { it.ready || onManage != null }.sortedBy { !it.ready }
    }
    var picked by rememberSaveable { mutableStateOf(state.selected) }
    // The AI in use may have no tab (not set up, with nowhere here to set it up from): the
    // sheet then opens on the first one that has.
    val tab = picked.takeIf { t -> providers.any { it.provider == t } } ?: providers.firstOrNull()?.provider
    val haptics = rememberHaptics()
    SearchBox(value = query, onValueChange = { query = it }, hint = "Search all models", modifier = Modifier.padding(horizontal = 16.dp))
    if (q.isEmpty()) {
        val scroll = rememberScrollState()
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().horizontalEdgeFade(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            providers.forEach { p ->
                PickerTab(
                    p.provider.displayName, selected = p.provider == tab, picked = p.provider == state.selected,
                    onClick = { haptics.tick(); picked = p.provider }, dim = !p.ready,
                ) { AiProviderLogo(p.provider, 22.dp) }
            }
        }
    }
    Text(
        note ?: "Used for Tilesmith, food estimates, widget lines and chat",
        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
    )
    @Composable
    fun option(p: AiProviderStatus, m: AiModel) = ModelOption(
        logo = p.provider, title = m.name, note = m.note,
        selected = state.selected == p.provider && p.model == m.id,
        onClick = { onPick(p.provider, m.id) },
    )
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        if (q.isNotEmpty()) {
            var any = false
            providers.filter { it.ready && it.provider != AiProvider.HERMES }.forEach { p ->
                val hits = modelsFor(state, p.provider).filter { "${it.name} ${it.id} ${p.provider.displayName}".lowercase().contains(q) }
                if (hits.isEmpty()) return@forEach
                any = true
                item(key = "h-${p.provider}") { RailHeading(p.provider.displayName) }
                hits.forEach { m -> item(key = "m-${p.provider}-${m.id}") { option(p, m) } }
            }
            if (!any) item(key = "none") { Text("No match", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
            return@LazyColumn
        }
        val p = providers.firstOrNull { it.provider == tab }
        if (p == null) {
            item(key = "none") { Text("No AI is set up yet", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
            return@LazyColumn
        }
        item(key = "how-${p.provider}") {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AiAccessBadge(p.access, label = if (p.ready) p.access.label else "Set up")
                Spacer(Modifier.width(8.dp))
                Text(p.detail, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        when {
            !p.ready -> item(key = "setup-${p.provider}") {
                ModelOption(
                    logo = p.provider, title = "Set up ${p.provider.displayName}", note = null,
                    selected = false, dim = true, onClick = { onManage?.invoke() },
                )
            }
            p.provider == AiProvider.HERMES -> item(key = "m-hermes") {
                ModelOption(
                    logo = AiProvider.HERMES,
                    title = p.modelLabel,
                    note = "Change it in the AI tab; the web uses it too",
                    selected = state.selected == AiProvider.HERMES,
                    onClick = { onPick(AiProvider.HERMES, null) },
                )
            }
            else -> {
                modelsFor(state, p.provider).forEach { m -> item(key = "m-${p.provider}-${m.id}") { option(p, m) } }
                if (p.provider in state.loadingModels) {
                    item(key = "l-${p.provider}") {
                        Text("Loading ${p.provider.displayName}'s models…", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 64.dp, top = 2.dp, bottom = 6.dp))
                    }
                }
            }
        }
    }
    if (onManage != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .clickable(onClick = onManage)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Settings, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Sign-ins, keys and models", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

/** A provider's models for the pickers; Claude's own list, the others' last read list. */
fun modelsFor(state: AiUiState, provider: AiProvider): List<AiModel> = state.models[provider].orEmpty()

@Composable
private fun ModelOption(logo: AiProvider, title: String, note: String?, selected: Boolean, onClick: () -> Unit, dim: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiProviderLogo(logo, 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (dim) TextSecondary else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrBlank()) Text(note, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(visible = selected) {
            Icon(AppIcons.Check, null, tint = ServerBrand, modifier = Modifier.size(18.dp))
        }
        if (dim) Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
    }
}

/** Every provider as a card, two across, with its status; the Settings screen's grid. */
@Composable
fun AiProviderGrid(state: AiUiState, focused: AiProvider, onFocus: (AiProvider) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.providers.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { p ->
                    val on = p.provider == focused
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (on) ServerBrand.copy(alpha = 0.10f) else ServerWell)
                            .border(if (on) 1.5.dp else 1.dp, if (on) ServerBrand.copy(alpha = 0.7f) else Border, shape)
                            .clickable { onFocus(p.provider) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AiProviderLogo(p.provider, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.provider.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (p.provider == state.selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(ServerBrand))
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            AiAccessBadge(p.access, label = if (p.ready) p.access.label else "Set up")
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
