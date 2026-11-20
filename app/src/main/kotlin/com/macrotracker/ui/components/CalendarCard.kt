package com.macrotracker.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTileLoading
import com.macrotracker.ui.screens.home.HomeTilePrompt
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.contentColorOn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.CalendarBrand
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CalendarUiState
import com.macrotracker.ui.theme.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val CalendarAccent = CalendarBrand

/**
 * The calendar as a Home tile: what is ahead in one line, a strip of days and the picked
 * day's events as cards. A tap opens the calendar page with every day's agenda.
 */
@Composable
fun CalendarCard(
    state: CalendarUiState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onOpen: () -> Unit = {},
) {
    val tone = HomeTone.Calendar
    if (!isVisible) {
        HomeTileLoading("Calendar", tone, icon = AppIcons.CalendarDays)
        return
    }

    WidgetStateSwitch(
        targetState = when (state) {
            is CalendarUiState.Loading -> 0
            is CalendarUiState.Success -> 1
            is CalendarUiState.PermissionRequired -> 2
            is CalendarUiState.Unavailable -> 3
        },
        label = "calendarContent",
        modifier = modifier,
    ) { stateKey ->
        val currentState = state
        when (stateKey) {
            0 -> HomeTileLoading("Calendar", tone, icon = AppIcons.CalendarDays)

            1 -> {
                val successState = currentState as? CalendarUiState.Success
                if (successState != null) {
                    val events = rememberAheadEvents(successState)
                    if (events.isEmpty()) {
                        HomeTile(title = "Calendar", tone = tone, icon = AppIcons.CalendarDays) {
                            Text("Nothing coming up in the next month.", fontSize = 14.sp, color = TextSecondary)
                        }
                    } else {
                        HomeTile(
                            title = "Calendar",
                            tone = tone,
                            icon = AppIcons.CalendarDays,
                            meta = calendarSummary(events),
                            onOpen = onOpen,
                        ) {
                            CalendarGlance(events)
                        }
                    }
                }
            }

            2 -> HomeTilePrompt(
                title = "Calendar",
                tone = tone,
                icon = AppIcons.CalendarDays,
                message = "Allow calendar access to see what is ahead.",
                actionLabel = "Allow",
                actionIcon = AppIcons.Calendar,
                onAction = onRequestPermission,
            )

            // 3 = Unavailable: switched off in Settings, or the calendar could not be read.
            else -> HomeTilePrompt(
                title = "Calendar",
                tone = tone,
                icon = AppIcons.CalendarDays,
                message = "Calendar is off or could not be read. Check Settings, Connections.",
                actionLabel = "Try again",
                actionIcon = AppIcons.Refresh,
                onAction = onRetry,
            )
        }
    }
}
