package com.macrotracker.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import com.macrotracker.ui.theme.MacroMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PermissionItem(
    val key: String,
    val permission: String?,
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val PERMISSION_ITEMS = listOf(
    PermissionItem(
        key = "notifications",
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        icon = Icons.Outlined.Notifications,
        title = "Notifications",
        description = "Get daily reminders to log your meals and stay on track with your goals.",
    ),
    PermissionItem(
        key = "camera",
        permission = Manifest.permission.CAMERA,
        icon = Icons.Outlined.CameraAlt,
        title = "Camera",
        description = "Scan nutrition labels to auto-fill macro data instantly.",
    ),
    PermissionItem(
        key = "location",
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        icon = Icons.Outlined.LocationOn,
        title = "Location",
        description = "Show local weather conditions on your home dashboard.",
    ),
    PermissionItem(
        key = "calendar",
        permission = Manifest.permission.READ_CALENDAR,
        icon = Icons.Outlined.CalendarMonth,
        title = "Calendar",
        description = "Display your upcoming events alongside your nutrition data.",
    ),
    PermissionItem(
        key = "health",
        permission = null,
        icon = Icons.Outlined.FavoriteBorder,
        title = "Health Connect",
        description = "Sync steps, heart rate, sleep, workouts and more. Connect in Settings after setup.",
    ),
)

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    fun isGranted(permission: String?): Boolean {
        if (permission == null) return false
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val grantedState = remember {
        mutableStateOf(PERMISSION_ITEMS.associate { it.key to isGranted(it.permission) })
    }

    val permissionsToRequest = PERMISSION_ITEMS.mapNotNull { it.permission }.toTypedArray()

    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val updated = grantedState.value.toMutableMap()
        PERMISSION_ITEMS.forEach { item ->
            if (item.permission != null) {
                updated[item.key] = results[item.permission] ?: isGranted(item.permission)
            }
        }
        grantedState.value = updated
    }

    val singleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val updated = grantedState.value.toMutableMap()
        PERMISSION_ITEMS.forEach { item ->
            if (item.permission != null) updated[item.key] = isGranted(item.permission)
        }
        grantedState.value = updated
    }

    // Alpha-only reveal — NavHost already owns the horizontal slide entrance.
    val headerAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            headerAlpha.animateTo(1f, MacroMotion.revealTween())
        }
        delay(150)
        launch {
            contentAlpha.animateTo(1f, MacroMotion.revealTween())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = headerAlpha.value
            }
        ) {
            Text(
                text = "Permissions",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grant permissions to unlock all features. Everything is optional — you can change these at any time in Settings.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 21.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MacroButton(
                text = "Grant all permissions",
                onClick = { multiLauncher.launch(permissionsToRequest) },
                variant = ButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .graphicsLayer {
                    alpha = contentAlpha.value
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PERMISSION_ITEMS.forEach { item ->
                val granted = grantedState.value[item.key] == true
                PermissionRow(
                    item = item,
                    granted = granted,
                    onClick = {
                        if (item.permission != null && !granted) {
                            singleLauncher.launch(item.permission)
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MacroButton(
            text = "Continue →",
            onClick = onContinue,
            variant = ButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (granted) Success.copy(alpha = 0.5f) else Border,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = !granted && item.permission != null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (granted) Success.copy(alpha = 0.12f) else Primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (granted) Success else Primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = if (granted || item.permission == null) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline,
            contentDescription = if (granted) "Granted" else "Tap to grant",
            tint = when {
                granted -> Success
                item.permission == null -> TextSecondary
                else -> Primary
            },
            modifier = Modifier.size(24.dp),
        )
    }
}
