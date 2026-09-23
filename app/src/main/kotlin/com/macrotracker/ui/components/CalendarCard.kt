package com.macrotracker.ui.components

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
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.CalendarBrand
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CalendarUiState
import com.macrotracker.ui.theme.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val CalendarAccent = CalendarBrand

@Composable
fun CalendarCard(
    state: CalendarUiState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    if (!isVisible) {
        WidgetPlaceholderCard(
            title = "Calendar",
            icon = AppIcons.CalendarDays,
            accent = CalendarAccent,
        )
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
            0 -> WidgetPlaceholderCard(
                title = "Calendar",
                icon = AppIcons.CalendarDays,
                accent = CalendarAccent,
            )

            1 -> {
                val successState = currentState as? CalendarUiState.Success
                if (successState != null) {
                val events = successState.events
                val upcomingEvents = successState.upcomingEvents
                // A recurring event's instances share one id, so an instance is its id and its start.
                // Ones that have ended drop off; the card is about what is still ahead.
                val allVisibleEvents = remember(events, upcomingEvents) {
                    val now = java.time.LocalDateTime.now()
                    (events + upcomingEvents)
                        .distinctBy { it.id to it.beginMillis }
                        .filter { it.endTime.isAfter(now) }
                        .sortedWith(agendaOrder)
                }
                
                if (allVisibleEvents.isEmpty()) {
                    MacroCard {
                        CardHeader(
                            title = "Calendar",
                            icon = AppIcons.CalendarDays,
                            accent = CalendarAccent,
                            subtitle = "Nothing coming up in the next month.",
                        )
                    }
                } else {
                    MacroCard {
                        CalendarContent(
                            events = allVisibleEvents,
                            lastUpdatedAt = successState.lastUpdatedAt,
                            expanded = expanded,
                            onToggleExpanded = {
                                expanded = !expanded
                                if (expanded) haptics.toggleOn() else haptics.toggleOff()
                            },
                        )
                    }
                }
                }
            }

            2 -> {
                WidgetPromptCard(
                    title = "Calendar",
                    message = "Allow calendar access to see today's events",
                    actionLabel = "Enable",
                    actionIcon = AppIcons.Calendar,
                    accent = CalendarAccent,
                    onAction = onRequestPermission,
                )
            }

            // 3 = Unavailable: switched off in Settings, or the calendar could not be read.
            // Say so rather than leaving an empty slot in the list.
            else -> WidgetPromptCard(
                title = "Calendar",
                message = "Calendar is off or could not be read. Check Settings → Connections.",
                actionLabel = "",
                actionIcon = AppIcons.CalendarDays,
                accent = CalendarAccent,
                onAction = null,
            )
        }
    }
}
