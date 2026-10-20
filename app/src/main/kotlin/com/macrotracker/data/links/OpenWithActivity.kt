package com.macrotracker.data.links

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import com.macrotracker.R
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.DailyDashTheme
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Open mail in": the apps that can open a kind of link, as a sheet over whatever is on
 * screen. Picking one keeps it for the kind ([AppLinks.choose]) and opens the link in it.
 * Started without a link (Settings → Connections), it only changes the pick.
 */
class OpenWithActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DailyDash_Sheet)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent?.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }
        val target = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_TARGET, Intent::class.java) }
            ?: url?.let { targetFor(it, intent) }
        val kind = LinkKind.of(intent?.getStringExtra(EXTRA_KIND)) ?: url?.let(LinkKind::forUrl)
        if (kind == null) {
            if (url != null) AppLinks.open(this, url)
            finish()
            return
        }
        if (target != null) {
            // Picked meanwhile, or nothing to pick between: no sheet.
            val chosen = AppLinks.chosen(this, kind)
            if (chosen != null || AppLinks.apps(this, kind, target).size <= 1) {
                AppLinks.open(this, kind, target)
                finish()
                return
            }
        }
        setContent {
            DailyDashTheme {
                OpenWithSheet(
                    kind = kind,
                    target = target,
                    onPick = { app ->
                        AppLinks.choose(this, kind, app.packageName)
                        if (target != null) AppLinks.openIn(this, app.packageName, target)
                        finish()
                    },
                    onForget = {
                        AppLinks.forget(this, kind)
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        // Widgets start this with Glance action parameters, which arrive as these extras.
        const val EXTRA_KIND = "open_with_kind"
        const val EXTRA_URL = "open_with_url"
        const val EXTRA_BEGIN = "open_with_begin"
        const val EXTRA_END = "open_with_end"
        private const val EXTRA_TARGET = "open_with_target"

        /** A widget's link: a view of [url], with a calendar event's times when it is one. */
        private fun targetFor(url: String, extras: Intent?): Intent {
            val target = AppLinks.viewIntent(url)
            val begin = extras?.getLongExtra(EXTRA_BEGIN, 0L) ?: 0L
            val end = extras?.getLongExtra(EXTRA_END, 0L) ?: 0L
            if (begin > 0) target.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            if (end > 0) target.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            return target
        }

        /** The sheet for [target], what is opened once an app is picked; null only changes the pick. */
        fun intent(context: Context, kind: LinkKind, target: Intent?): Intent =
            Intent(context, OpenWithActivity::class.java)
                .putExtra(EXTRA_KIND, kind.key)
                .putExtra(EXTRA_TARGET, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        /** Settings' "change": the sheet without a link to open. */
        fun choose(context: Context, kind: LinkKind) {
            runCatching { context.startActivity(intent(context, kind, null)) }
        }
    }
}

/** What the sheet is titled for each kind. */
val LinkKind.sheetTitle: String
    get() = when (this) {
        LinkKind.MAIL -> "Open mail in"
        LinkKind.CALENDAR -> "Open events in"
        LinkKind.VIDEO -> "Open YouTube in"
        LinkKind.STREAM -> "Open Twitch in"
        LinkKind.CODE -> "Open GitHub in"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenWithSheet(
    kind: LinkKind,
    target: Intent?,
    onPick: (LinkApp) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val apps = remember(kind) { AppLinks.apps(context, kind, target) }
    val chosen = remember(kind) { AppLinks.chosen(context, kind) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                kind.sheetTitle,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            if (apps.isEmpty()) {
                Text(
                    "No app on this phone opens these",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        selected = app.packageName == chosen,
                        onClick = { haptics.tick(); onPick(app) },
                    )
                }
                if (target == null && chosen != null) {
                    item(key = "forget") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { haptics.tick(); onForget() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Undo, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            Text("Ask next time", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: LinkApp, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(108, 108).asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SelectedFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bmp = icon
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.isBrowser) Text("Browser", color = TextTertiary, fontSize = 12.sp)
        }
        if (selected) Icon(AppIcons.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
    }
}
