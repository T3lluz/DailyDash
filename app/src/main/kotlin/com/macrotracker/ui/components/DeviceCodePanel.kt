package com.macrotracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/**
 * OAuth device-code step shared by the Twitch and GitHub hubs: the code (copied as soon as
 * it arrives, tap to copy again), where to enter it, and equal-width Open / Cancel buttons.
 * Owns its haptics, so callers pass plain callbacks.
 */
@Composable
fun DeviceCodePanel(
    service: String,
    userCode: String?,
    activationHint: String,
    accent: Color,
    codeSurface: Color,
    onOpenActivation: () -> Unit,
    onCancelLogin: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    formatCode: (String) -> String = { it },
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val code = userCode?.takeIf { it.isNotBlank() }
    var copied by remember { mutableStateOf(false) }

    fun copyCode(fromUser: Boolean) {
        val value = code ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("$service code", value))
        copied = true
        if (fromUser) haptics.confirm()
    }

    LaunchedEffect(code) {
        if (code != null) copyCode(fromUser = false)
    }
    LaunchedEffect(copied, code) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Enter this code on $service", fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(shape)
                .background(codeSurface)
                .clickable(enabled = code != null, onClick = { copyCode(fromUser = true) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                code?.let(formatCode) ?: "····",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Icon(
                imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy code",
                tint = if (copied) Success else accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (copied) "Copied to clipboard" else "Tap the code to copy it",
            fontSize = 11.sp,
            color = if (copied) Success else TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(activationHint, fontSize = 11.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonModifier = Modifier.weight(1f).height(40.dp)
            val padding = PaddingValues(horizontal = 12.dp)
            Button(
                onClick = { haptics.click(); onOpenActivation() },
                modifier = buttonModifier,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                shape = shape,
                contentPadding = padding,
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open $service", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { haptics.tick(); onCancelLogin() },
                modifier = buttonModifier,
                shape = shape,
                border = BorderStroke(1.dp, Border),
                contentPadding = padding,
            ) {
                Text("Cancel", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
