package com.macrotracker.ui.screens.git

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.DiffFile
import com.macrotracker.data.hermes.DiffLine
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TileDark
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DiffSource
import com.macrotracker.ui.viewmodel.GitViewModel
import com.macrotracker.ui.viewmodel.ReviewComment
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val Mono = FontFamily.Monospace
private val AddTint = ServerGood.copy(alpha = 0.11f)
private val DelTint = ServerCritical.copy(alpha = 0.11f)

/** One file to review: where its diff comes from and what to call it. */
data class ReviewFile(val path: String, val status: String, val plus: Int, val minus: Int, val source: DiffSource)

/**
 * A diff to read and mark up, after T3 Code's review screen: files you step through, hunks with both
 * line numbers, and a note on any line (a tap writes one in place). The notes wait at the foot and go
 * to Hermes together, each with its file and line, as the changes to make.
 */
@Composable
fun ReviewScreen(
    title: String,
    context: String,
    files: List<ReviewFile>,
    startIndex: Int,
    viewModel: GitViewModel,
    onBack: () -> Unit,
    onSendToHermes: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))) }
    val file = files.getOrNull(index)
    var parsed by remember { mutableStateOf<com.macrotracker.ui.viewmodel.Load<DiffFile?>>(com.macrotracker.ui.viewmodel.Load(loading = true)) }
    LaunchedEffect(file?.path, file?.source) {
        val f = file ?: return@LaunchedEffect
        parsed = com.macrotracker.ui.viewmodel.Load(loading = true)
        parsed = try {
            val all = viewModel.diffFiles(f.source, f.path)
            com.macrotracker.ui.viewmodel.Load(all.firstOrNull { it.path == f.path } ?: all.firstOrNull())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.macrotracker.ui.viewmodel.Load(error = e.message ?: "Could not read the diff")
        }
    }
    val comments by viewModel.comments.collectAsState()
    var writing by remember(file?.path) { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize().background(Background).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // header: which file, how big, and the way to the next
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(top = 36.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { haptics.tick(); onBack() }) {
                        Icon(AppIcons.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            file?.path?.substringAfterLast('/') ?: title,
                            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            file?.path?.substringBeforeLast('/', "")?.ifBlank { null } ?: title,
                            color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (files.size > 1) {
                        IconButton(onClick = { haptics.tick(); index = (index - 1 + files.size) % files.size }) {
                            Icon(AppIcons.ChevronUp, "Previous file", tint = TextSecondary, modifier = Modifier.size(22.dp))
                        }
                        Text("${index + 1}/${files.size}", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { haptics.tick(); index = (index + 1) % files.size }) {
                            Icon(AppIcons.ChevronDown, "Next file", tint = TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                file?.let {
                    Row(Modifier.padding(start = 56.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(it.status.firstOrNull() ?: 'M')
                        Text("+${parsed.value?.plus ?: it.plus}", color = ServerGood, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                        Text("−${parsed.value?.minus ?: it.minus}", color = ServerCritical, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                        if (context.isNotBlank()) Text(context, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val d = parsed.value
            when {
                parsed.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingSpinner(color = ServerBrand) }
                parsed.error != null -> Note(AppIcons.Warning, parsed.error!!, Modifier.weight(1f))
                d == null || (d.hunks.isEmpty() && !d.binary) -> Note(AppIcons.Code, "Nothing to show for this file.", Modifier.weight(1f))
                d.binary -> Note(AppIcons.File, "A binary file. Nothing to read here.", Modifier.weight(1f))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().hazeSource(haze),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp),
                ) {
                    d.hunks.forEachIndexed { hi, hunk ->
                        item(key = "h$hi") { HunkHeader(hunk.header) }
                        itemsIndexed(hunk.lines, key = { li, _ -> "l$hi-$li" }) { _, line ->
                            val lineNo = if (line.kind == DiffLine.Kind.DEL) line.old else line.new
                            val isNew = line.kind != DiffLine.Kind.DEL
                            val here = comments.filter { it.path == d.path && it.line == lineNo && it.isNew == isNew }
                            val open = writing == (lineNo to isNew)
                            Column {
                                DiffRow(line, marked = here.isNotEmpty() || open) {
                                    if (line.kind != DiffLine.Kind.NOTE && lineNo > 0) {
                                        haptics.tick()
                                        writing = if (open) null else (lineNo to isNew)
                                    }
                                }
                                here.forEach { c -> CommentBubble(c) { haptics.click(); viewModel.removeComment(c) } }
                                AnimatedVisibility(open, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut()) {
                                    CommentWriter(
                                        onAdd = { text ->
                                            haptics.click()
                                            viewModel.addComment(ReviewComment(d.path, lineNo, isNew, line.text, text))
                                            writing = null
                                        },
                                        onCancel = { writing = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // the notes so far, and the way to send them
        val mine = comments
        AnimatedVisibility(
            visible = (mine.isNotEmpty() || file != null) && writing == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .chromeGlass(haze, RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Chat, null, tint = if (mine.isEmpty()) TextTertiary else TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (mine.isEmpty()) "Tap a line to note it" else "${mine.size} note${if (mine.size == 1) "" else "s"}",
                        color = if (mine.isEmpty()) TextSecondary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (mine.isEmpty()) "Notes go to Hermes with their file and line" else "in ${mine.map { it.path }.distinct().size} file${if (mine.map { it.path }.distinct().size == 1) "" else "s"}",
                        color = TextTertiary, fontSize = 11.sp,
                    )
                }
                if (mine.isNotEmpty()) {
                    Text(
                        "Clear",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { haptics.tick(); viewModel.clearComments() }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ServerBrand)
                            .clickable { haptics.click(); onSendToHermes(viewModel.reviewPrompt(context.ifBlank { title })) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Send, null, tint = com.macrotracker.ui.theme.OnAccent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Send to Hermes", color = com.macrotracker.ui.theme.OnAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HorizontalRule() = Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

@Composable
internal fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp)
    }
}

/** `M`, `A`, `D`, `R`, `U` or `?` as the little coloured square every git client draws. */
@Composable
internal fun StatusBadge(letter: Char, modifier: Modifier = Modifier) {
    val (color, label) = when (letter.uppercaseChar()) {
        'A' -> ServerGood to "A"
        'D' -> ServerCritical to "D"
        'R', 'C' -> ServerBrand to "R"
        'U' -> ServerWarn to "!"
        '?' -> TextSecondary to "U"
        else -> ServerWarn to "M"
    }
    Box(modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

@Composable
private fun HunkHeader(header: String) {
    val rest = header.substringAfter("@@", "").substringAfter("@@", "").trim()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(TileDark)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            header.removePrefix("@@").substringBefore("@@").trim(),
            color = TextSecondary, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        if (rest.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(rest, color = TextTertiary, fontSize = 11.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DiffRow(line: DiffLine, marked: Boolean, onTap: () -> Unit) {
    val tint = when {
        marked -> Color.White.copy(alpha = 0.10f)
        line.kind == DiffLine.Kind.ADD -> AddTint
        line.kind == DiffLine.Kind.DEL -> DelTint
        else -> Color.Transparent
    }
    val sign = when (line.kind) { DiffLine.Kind.ADD -> "+"; DiffLine.Kind.DEL -> "−"; else -> " " }
    val signColor = when (line.kind) { DiffLine.Kind.ADD -> ServerGood; DiffLine.Kind.DEL -> ServerCritical; else -> TextTertiary }
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint)
            .clickable(onClick = onTap),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(4.dp))
        Text(if (line.old > 0) "${line.old}" else "", color = TextTertiary, fontSize = 10.5.sp, fontFamily = Mono, lineHeight = 19.sp, modifier = Modifier.width(34.dp).padding(end = 5.dp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text(if (line.new > 0) "${line.new}" else "", color = TextTertiary, fontSize = 10.5.sp, fontFamily = Mono, lineHeight = 19.sp, modifier = Modifier.width(34.dp).padding(end = 5.dp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text(sign, color = signColor, fontSize = 12.sp, fontFamily = Mono, lineHeight = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
        Text(
            line.text.ifEmpty { " " },
            color = if (line.kind == DiffLine.Kind.NOTE) TextTertiary else TextPrimary,
            fontSize = 12.sp, lineHeight = 19.sp, fontFamily = Mono,
            modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentBubble(c: ReviewComment, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TileDark)
            .combinedClickable(onClick = {}, onLongClick = onRemove, onLongClickLabel = "Remove this note"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("Note for Hermes", color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(c.text, color = TextPrimary, fontSize = 13.5.sp, lineHeight = 19.sp)
            Text("Hold to remove", color = TextTertiary, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CommentWriter(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        // once the keyboard has made room, the writer and its buttons are brought into view
        kotlinx.coroutines.delay(350)
        runCatching { bring.bringIntoView() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .padding(start = 56.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TileDark)
            .padding(12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(ServerBrand),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            minLines = 2,
            decorationBox = { inner ->
                if (text.isEmpty()) Text("What should change here?", color = TextTertiary, fontSize = 14.sp)
                inner()
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 7.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Add note",
                color = if (text.isBlank()) TextTertiary else com.macrotracker.ui.theme.OnAccent,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (text.isBlank()) Color.White.copy(alpha = 0.06f) else ServerBrand)
                    .clickable(enabled = text.isNotBlank()) { onAdd(text.trim()) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
