package com.macrotracker.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons

/** Bottom padding for tab content so the last card scrolls clear of the floating nav pill. */
val TabContentBottomPadding = 120.dp

/**
 * Shared 32.sp bold page title for the bottom-nav tabs (Home, Health, AI, Settings).
 * Pushed screens use [SubScreenHeader] instead.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = HealthDisplay,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                letterSpacing = (-1.2).sp,
                color = TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = HealthDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ScreenHeaderSpacer() {
    // Room for the island: one line fits in the usual 48dp; a second line (Hermes at work)
    // pushes the title down on the island's own spring, and lets it back up after.
    val island by animateDpAsState(LocalIslandClearance.current, MacroMotion.navTabSpring(), label = "header_island")
    Spacer(modifier = Modifier.height(maxOf(48.dp, island)))
}

/**
 * Title row for every pushed screen (settings pages, Help, Stats, Widgets, Servers):
 * back arrow, 26.sp title, optional subtitle and trailing actions. Expects the
 * caller's 16.dp horizontal content padding.
 */
@Composable
fun SubScreenHeader(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(WellFill)
                .clickable(role = Role.Button) {
                    haptics.click()
                    onNavigateBack()
                }
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = AppIcons.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HealthDisplay,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Bottom padding for pushed screens: they have no nav pill, only the system bar. */
fun Modifier.subScreenBottomPadding(): Modifier = navigationBarsPadding().padding(bottom = 24.dp)
