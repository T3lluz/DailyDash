package com.macrotracker.ui.screens.ai

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesPreview
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics

/*
 * The server's files, after T3 Code's file browser and inline preview: a folder lists what is in it, a
 * file shows its text with line numbers (the lines a change touched, marked), a picture shows itself.
 * One sheet serves both the header's "Server files" and a change card's "Open file". Everything comes
 * from the bridge's `/ai/preview`, which already refuses secrets and what lies outside the home folder.
 */

private val Mono = FontFamily.Monospace

/** What the viewer is showing: a path, and the lines to mark when it is a file. */
private data class FileTarget(val path: String, val markFrom: Int = 0, val markTo: Int = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerFilesSheet(
    startPath: String,
    markFrom: Int = 0,
    markTo: Int = 0,
    loader: suspend (path: String, from: Int, to: Int) -> HermesPreview,
    /** Puts the path in the composer, so Hermes is asked about it. */
    onMention: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // where the person has been, so back goes back and not just up
    val trail = remember(startPath) { mutableStateListOf(FileTarget(startPath, markFrom, markTo)) }
    val here = trail.last()
    var preview by remember { mutableStateOf<HermesPreview?>(null) }
    LaunchedEffect(here) {
        preview = null
        preview = try {
            loader(here.path, here.markFrom, here.markTo)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HermesPreview.Failed(here.path, e.message ?: "Could not open it")
        }
    }
    val shown = preview
    val absolute = shown?.path ?: here.path
    val name = when (shown) {
        is HermesPreview.Text -> shown.name
        is HermesPreview.Folder -> if (shown.rel == "." || shown.rel == "~") "Home" else shown.name
        is HermesPreview.Image -> shown.name
        is HermesPreview.Binary -> shown.name
        else -> here.path.substringAfterLast('/').ifBlank { here.path }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Surface,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        if (trail.size > 1) trail.removeAt(trail.lastIndex) else onDismiss()
                    },
                ) { Icon(AppIcons.ArrowBack, "Back", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        absolute.takeIf { it != "~" } ?: "Home",
                        color = TextTertiary, fontSize = 11.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (shown != null && shown !is HermesPreview.Failed) {
                    val up = (shown as? HermesPreview.Folder)?.path?.substringBeforeLast('/', "")?.ifBlank { null }
                    if (up != null) {
                        IconButton(onClick = { haptics.tick(); trail += FileTarget(up) }) {
                            Icon(AppIcons.ArrowUp, "Parent folder", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { haptics.click(); onMention(shown.path); onDismiss() }) {
                        Icon(AppIcons.Chat, "Ask about this", tint = ServerBrand, modifier = Modifier.size(20.dp))
                    }
                }
            }
            HorizontalDivider(color = Border)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (shown) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand) }
                    is HermesPreview.Failed -> Note(AppIcons.Lock, shown.error)
                    is HermesPreview.Binary -> Note(AppIcons.File, "Binary file, ${sizeLabel(shown.bytes)}. Nothing to show here.")
                    is HermesPreview.Image -> FileImage(shown)
                    is HermesPreview.Folder -> FolderList(shown) { entry ->
                        haptics.tick()
                        trail += FileTarget(entry.path)
                    }
                    is HermesPreview.Text -> FileText(shown)
                }
            }
        }
    }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun FolderList(folder: HermesPreview.Folder, onOpen: (HermesPreview.Folder.Entry) -> Unit) {
    if (folder.entries.isEmpty()) {
        Note(AppIcons.Folder, "Nothing in this folder.")
        return
    }
    // folders first, and what is not hidden before what is
    val entries = remember(folder.path) {
        folder.entries.sortedWith(compareBy({ !it.dir }, { it.name.startsWith(".") }, { it.name.lowercase() }))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.path }) { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(e) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (e.dir) AppIcons.Folder else AppIcons.File, null, tint = if (e.dir) ServerBrand else TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(14.dp))
                Text(e.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!e.dir) Text(sizeLabel(e.bytes), color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FileText(file: HermesPreview.Text) {
    val lines = remember(file.path, file.text) { file.text.lines().let { if (it.lastOrNull().isNullOrEmpty()) it.dropLast(1) else it } }
    val state = rememberLazyListState()
    val first = if (file.markFrom > 0) file.markFrom else 0
    // a marked line is brought into view, a few lines down from the top so what leads to it shows
    LaunchedEffect(file.path, first) {
        if (first > 0) state.scrollToItem((first - 3).coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
    }
    val gutter = lines.size.toString().length
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val n = i + 1
            val marked = file.markFrom > 0 && n in file.markFrom..maxOf(file.markFrom, file.markTo)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (marked) ServerWarn.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    n.toString().padStart(gutter),
                    color = TextTertiary, fontSize = 11.5.sp, lineHeight = 18.sp, fontFamily = Mono,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    line.ifEmpty { " " },
                    color = TextPrimary, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "end") {
            Text(
                buildString {
                    append(sizeLabel(file.bytes)).append(" · ").append(file.lines).append(" lines")
                    if (file.truncated) append(" · the start of it")
                },
                color = TextTertiary, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        }
    }
}

@Composable
private fun FileImage(image: HermesPreview.Image) {
    val bitmap = remember(image.path, image.base64) {
        runCatching {
            val bytes = Base64.decode(image.base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) {
        Note(AppIcons.Images, "This picture could not be drawn on the phone.")
        return
    }
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap, contentDescription = image.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceChrome),
        )
    }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}
