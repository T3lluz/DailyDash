package com.macrotracker.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.CalendarBrand
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CalendarUiState
import com.macrotracker.ui.theme.AppIcons

private val CalendarAccent = CalendarBrand

@Composable
fun CalendarCard(
    state: CalendarUiState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
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
            0 -> MacroCard {
                ContentSkeleton(lines = 3, accent = Border)
            }

            1 -> {
                val successState = currentState as? CalendarUiState.Success
                if (successState != null) {
                val events = successState.events
                val upcomingEvents = successState.upcomingEvents
                val allVisibleEvents = (events + upcomingEvents).distinctBy { it.id }
                
                if (allVisibleEvents.isEmpty()) {
                    MacroCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                AppIcons.Calendar,
                                contentDescription = null,
                                tint = CalendarAccent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Calendar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                Text(
                                    "No events found for selected calendars.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    }
                } else {
                    MacroCard {
                        Column {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (events.isNotEmpty()) AppIcons.CalendarDays else AppIcons.NotepadText,
                                        contentDescription = null,
                                        tint = CalendarAccent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (events.isNotEmpty()) "Today's Schedule" else "Upcoming Events",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    LastUpdatedText(
                                        lastUpdatedAt = successState.lastUpdatedAt,
                                        color = TextSecondary,
                                    )
                                    IconButton(onClick = { showDetails = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = AppIcons.NotepadText,
                                            contentDescription = "Full View",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    WidgetExpandChevron(
                                        expanded = expanded,
                                        onClick = {
                                            val wasExpanded = expanded
                                            expanded = !expanded
                                            if (!wasExpanded) haptics.toggleOn() else haptics.toggleOff()
                                        },
                                        accentColor = CalendarAccent,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Featured Event (Next up)
                            val featured = allVisibleEvents.first()
                            EventTile(
                                event = featured,
                                featured = true
                            )

                            // Expandable list
                            WidgetExpandSection(visible = expanded && allVisibleEvents.size > 1) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    allVisibleEvents.drop(1).take(2).forEachIndexed { index, event ->
                                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                                        EventTile(event = event, featured = false)
                                    }

                                    if (allVisibleEvents.size > 3) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "+${allVisibleEvents.size - 3} more events · Tap icon for full list",
                                            fontSize = 11.sp,
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 22.dp)
                                        )
                                    }

                                    WidgetExpandFooter(
                                        expanded = true,
                                        onToggle = { expanded = false },
                                        accentColor = CalendarAccent,
                                        collapseLabel = "Show less",
                                    )
                                }
                            }

                            if (!expanded && allVisibleEvents.size > 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                                WidgetExpandFooter(
                                    expanded = false,
                                    onToggle = { expanded = true },
                                    accentColor = CalendarAccent,
                                    expandLabel = "${allVisibleEvents.size - 1} more event${if (allVisibleEvents.size - 1 != 1) "s" else ""}",
                                )
                            }
                        }
                    }

                    if (showDetails) {
                        CalendarDetailsDialog(
                            events = allVisibleEvents,
                            onDismiss = { showDetails = false }
                        )
                    }
                }
                }
            }

            2 -> {
                MacroCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Calendar",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                "Allow calendar access to see today's events",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestPermission)
                                .background(CalendarAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Calendar,
                                contentDescription = null,
                                tint = CalendarAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Enable",
                                color = CalendarAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            else -> { } // 3 = Unavailable, nothing to show
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDetailsDialog(
    events: List<CalendarEvent>,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = com.macrotracker.ui.theme.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Detailed Schedule",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(AppIcons.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(events) { event ->
                        EventTile(event = event, featured = false, showFullInfo = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTile(
    event: CalendarEvent,
    featured: Boolean,
    showFullInfo: Boolean = false,
) {
    val eventColor = try {
        Color(event.calendarColor).copy(alpha = 1f)
    } catch (_: Exception) {
        CalendarAccent
    }
    val uriHandler = LocalUriHandler.current

    val bgColor = if (featured) Background else Background.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (featured) 10.dp else 8.dp))
            .background(bgColor)
            .padding(if (featured) 12.dp else 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(if (featured) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(eventColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontSize = if (featured) 15.sp else 14.sp,
                        fontWeight = if (featured) FontWeight.Bold else FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = if (showFullInfo) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (event.calendarName.isNotBlank()) {
                        Text(
                            text = event.calendarName,
                            fontSize = 10.sp,
                            color = eventColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (event.isHappeningNow) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(eventColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            InfoTag(icon = AppIcons.Clock, text = event.formattedDateAndTime, color = TextSecondary)

            if (event.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                InfoTag(icon = AppIcons.MapPin, text = event.location, color = TextSecondary)
            }

            val link = event.meetingLink
            if (link != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val displayLink = when {
                    link.contains("meet.google.com") -> "Google Meet"
                    link.contains("zoom.us") || link.contains("zoom.") -> "Zoom Meeting"
                    else -> "Join Meeting"
                }
                InfoTag(
                    icon = AppIcons.Link,
                    text = displayLink,
                    color = CalendarAccent,
                    onClick = { try { uriHandler.openUri(link) } catch (_: Exception) { } }
                )
            }

            if (showFullInfo && event.description.isNotBlank()) {
                val snippet = event.description.replace(Regex("<[^>]*>"), "").trim()
                if (snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = snippet,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
