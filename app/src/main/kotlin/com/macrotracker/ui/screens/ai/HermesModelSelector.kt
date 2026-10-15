package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HermesReach
import com.macrotracker.ui.viewmodel.HermesViewModel

/**
 * The AI tab's model pill and sheet, reused in Settings → AI and Tilesmith so Hermes
 * is picked the same way everywhere. Changing the model here is the dashboard's
 * current one; the desk sees it too.
 */
@Composable
fun HermesModelSelector(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val favouriteModels by viewModel.favouriteModels.collectAsState()
    val haptics = rememberHaptics()
    var sheet by remember { mutableStateOf<HermesSheet?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh(quiet = true) }

    Column(modifier = modifier) {
        ModelPill(
            model = state.currentModel,
            fallback = state.status?.modelLabel,
            switching = state.switchingModel,
            modifiers = HermesCatalog.modifiers(state.status),
            onClick = { haptics.tick(); sheet = HermesSheet.MODEL },
        )
        if (showStatus && state.reach == HermesReach.DOWN) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.reachError ?: "Couldn't reach Hermes. Are you on the tailnet?",
                color = Error,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else if (showStatus && state.reach == HermesReach.CHECKING) {
            Spacer(Modifier.height(8.dp))
            Text("Checking the dashboard…", color = TextTertiary, fontSize = 12.sp)
        }
    }

    sheet?.let { which ->
        HermesPickerSheet(
            sheet = which,
            status = state.status,
            favourites = favouriteModels,
            onToggleFavourite = viewModel::toggleFavourite,
            onDismiss = { sheet = null },
            onPickFamily = { family ->
                haptics.tick()
                sheet = null
                viewModel.pickFamily(family)
            },
            onPickEffort = { effort ->
                haptics.tick()
                viewModel.setEffort(effort)
            },
            onToggleThink = { haptics.tick(); viewModel.toggleThink() },
            onToggleFast = { haptics.tick(); viewModel.toggleFast() },
            onPickWindow = { tokens ->
                haptics.tick()
                viewModel.setWindow(tokens)
            },
        )
    }
}
