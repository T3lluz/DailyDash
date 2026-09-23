package com.macrotracker.ui.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * Markdown for release notes and chat replies, parsed by [MarkdownParser] with the web
 * dashboard's rules: headings, paragraphs, nested and task lists, quotes and callouts,
 * rules, GFM tables (they scroll sideways when wider than the bubble), and fenced code
 * with a copy button. Inline: bold, italic, strikethrough, code, links and bare URLs.
 *
 * [breaks] keeps a single line break as a break, as chat replies expect; release notes
 * run their lines together. [streaming] draws a cursor at the end of the text and leaves
 * an unclosed fence open.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = (fontSize.value + 5).sp,
    linkColor: Color = Primary,
    breaks: Boolean = false,
    streaming: Boolean = false,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val look = remember(color, fontSize, lineHeight, linkColor, breaks) {
        MdLook(color, fontSize, lineHeight, linkColor, breaks)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val last = index == blocks.lastIndex
            val top = when {
                index == 0 -> 0.dp
                block is MdBlock.Heading -> 12.dp
                else -> 8.dp
            }
            Box(Modifier.padding(top = top)) {
                MdBlockView(block, look, cursor = streaming && last)
            }
        }
        if (streaming && blocks.isEmpty()) Text(CURSOR, color = color, fontSize = fontSize)
    }
}

private const val CURSOR = "▌"

private data class MdLook(
    val color: Color,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val linkColor: Color,
    val breaks: Boolean,
)

@Composable
private fun MdBlockView(block: MdBlock, look: MdLook, cursor: Boolean) {
    when (block) {
        is MdBlock.Heading -> InlineText(
            text = block.text + if (cursor) CURSOR else "",
            look = look,
            color = TextPrimary,
            fontSize = when (block.level) {
                1 -> (look.fontSize.value + 5).sp
                2 -> (look.fontSize.value + 3).sp
                else -> (look.fontSize.value + 1).sp
            },
            weight = FontWeight.Bold,
        )
        is MdBlock.Paragraph -> InlineText(
            text = block.lines.joinToString(if (look.breaks) "\n" else " ") + if (cursor) CURSOR else "",
            look = look,
        )
        is MdBlock.ListBlock -> MdList(block, look, cursor)
        is MdBlock.Quote -> MdQuote(block, look, cursor)
        is MdBlock.Table -> Column {
            MdTable(block, look)
            if (cursor) Text(CURSOR, color = look.color, fontSize = look.fontSize)
        }
        is MdBlock.Rule -> Box(
            Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Border),
        )
        is MdBlock.Code -> MdFence(block, look, cursor)
    }
}

@Composable
private fun InlineText(
    text: String,
    look: MdLook,
    modifier: Modifier = Modifier,
    color: Color = look.color,
    fontSize: TextUnit = look.fontSize,
    weight: FontWeight? = null,
    align: TextAlign = TextAlign.Start,
) {
    val annotated = remember(text, color, look.linkColor) { inlineMarkdown(text, color, look.linkColor) }
    Text(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = if (fontSize == look.fontSize) look.lineHeight else (fontSize.value + 6).sp,
            fontWeight = weight,
            textAlign = align,
        ),
    )
}

@Composable
private fun MdList(block: MdBlock.ListBlock, look: MdLook, cursor: Boolean, depth: Int = 0) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { i, item ->
            val lastItem = cursor && i == block.items.lastIndex
            Row {
                val marker = when {
                    item.checked != null -> null
                    block.ordered -> "${block.start + i}."
                    else -> if (depth == 0) "•" else "◦"
                }
                if (marker != null) {
                    Text(
                        marker,
                        color = if (block.ordered) TextSecondary else look.color,
                        fontSize = look.fontSize,
                        lineHeight = look.lineHeight,
                        fontWeight = if (block.ordered) FontWeight.SemiBold else null,
                        modifier = Modifier.widthIn(min = if (block.ordered) 22.dp else 14.dp).padding(end = 4.dp),
                    )
                } else {
                    TaskBox(checked = item.checked == true, look = look)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val tail = lastItem && item.children.isEmpty()
                    InlineText(
                        text = item.lines.joinToString(if (look.breaks) "\n" else " ") + if (tail) CURSOR else "",
                        look = look,
                        color = if (item.checked == true) TextTertiary else look.color,
                    )
                    item.children.forEachIndexed { c, child ->
                        MdList(child, look, cursor = lastItem && c == item.children.lastIndex, depth = depth + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskBox(checked: Boolean, look: MdLook) {
    val box = with(LocalDensity.current) { (look.fontSize.toDp() + 2.dp) }
    Box(
        modifier = Modifier
            .padding(top = 2.dp, end = 8.dp)
            .size(box)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) ServerGood.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (checked) ServerGood else Border, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(AppIcons.Check, contentDescription = "Done", tint = ServerGood, modifier = Modifier.size(box - 4.dp))
    }
}

@Composable
private fun MdQuote(block: MdBlock.Quote, look: MdLook, cursor: Boolean) {
    val (accent, label) = when (block.callout) {
        "note" -> Primary to "Note"
        "tip" -> ServerGood to "Tip"
        "important" -> Color(0xFFB388FF) to "Important"
        "warning" -> ServerWarn to "Warning"
        "caution" -> ServerCritical to "Caution"
        else -> Border to null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .background(if (label != null) accent.copy(alpha = 0.08f) else Color.Transparent)
            .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column {
            if (label != null) {
                Text(label, color = accent, fontSize = (look.fontSize.value - 1).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
            }
            InlineText(
                text = block.lines.joinToString("\n") + if (cursor) CURSOR else "",
                look = look,
                color = if (label != null) look.color else TextSecondary,
            )
        }
    }
}

/** Where the rows and columns landed, written by the table's layout and read by its drawing. */
private class TableGeometry {
    var colX: IntArray = IntArray(0)
    var rowY: IntArray = IntArray(0)
}

/**
 * A GFM table. Each column is as wide as its widest cell up to a cap, so short columns
 * stay short and long text wraps; a table wider than the bubble scrolls sideways.
 */
@Composable
private fun MdTable(block: MdBlock.Table, look: MdLook) {
    val cols = block.header.size.coerceAtLeast(1)
    val geometry = remember(block) { TableGeometry() }
    val density = LocalDensity.current
    val cellPadH = 10.dp
    val cellPadV = 6.dp
    val maxCol = with(density) { MaxColumnWidth.roundToPx() }
    val minCol = with(density) { MinColumnWidth.roundToPx() }
    val headerFill = Color.White.copy(alpha = 0.06f)
    val stripe = Color.White.copy(alpha = 0.025f)
    // As wide as the table, up to the bubble; wider than that, it scrolls.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Layout(
            modifier = Modifier.drawBehind {
                val rows = geometry.rowY
                if (rows.size < 2) return@drawBehind
                drawRect(headerFill, size = Size(size.width, rows[1].toFloat()))
                for (r in 1 until rows.size - 1) {
                    if (r % 2 == 0) {
                        drawRect(stripe, topLeft = Offset(0f, rows[r].toFloat()), size = Size(size.width, (rows[r + 1] - rows[r]).toFloat()))
                    }
                    drawLine(Border, Offset(0f, rows[r].toFloat()), Offset(size.width, rows[r].toFloat()), strokeWidth = 1f)
                }
                val xs = geometry.colX
                for (c in 1 until xs.size - 1) {
                    drawLine(Border.copy(alpha = 0.5f), Offset(xs[c].toFloat(), 0f), Offset(xs[c].toFloat(), size.height), strokeWidth = 1f)
                }
            },
            content = {
                (listOf(block.header) + block.rows).forEachIndexed { r, row ->
                    for (c in 0 until cols) {
                        InlineText(
                            text = row.getOrElse(c) { "" },
                            look = look,
                            color = if (r == 0) TextPrimary else look.color,
                            fontSize = (look.fontSize.value - 1).sp,
                            weight = if (r == 0) FontWeight.SemiBold else null,
                            align = when (block.align.getOrElse(c) { MdAlign.START }) {
                                MdAlign.START -> TextAlign.Start
                                MdAlign.CENTER -> TextAlign.Center
                                MdAlign.END -> TextAlign.End
                            },
                            modifier = Modifier.padding(horizontal = cellPadH, vertical = cellPadV),
                        )
                    }
                }
            },
        ) { measurables, _ ->
            val rowCount = measurables.size / cols
            val widths = IntArray(cols) { c ->
                (0 until rowCount).maxOf { r -> measurables[r * cols + c].maxIntrinsicWidth(Constraints.Infinity) }
                    .coerceIn(minCol, maxCol)
            }
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i % cols])) }
            val heights = IntArray(rowCount) { r -> (0 until cols).maxOf { c -> placeables[r * cols + c].height } }
            val colX = IntArray(cols + 1).also { x -> for (c in 0 until cols) x[c + 1] = x[c] + widths[c] }
            val rowY = IntArray(rowCount + 1).also { y -> for (r in 0 until rowCount) y[r + 1] = y[r] + heights[r] }
            geometry.colX = colX
            geometry.rowY = rowY
            layout(colX[cols], rowY[rowCount]) {
                placeables.forEachIndexed { i, p -> p.place(colX[i % cols], rowY[i / cols]) }
            }
        }
    }
}

private val MaxColumnWidth: Dp = 220.dp
private val MinColumnWidth: Dp = 44.dp

/**
 * A fence. `run`, `search` and `fetch` are requests Hermes makes, so they show as the
 * one-line asks they are; `ask` is drawn by its card; diffs colour their lines.
 */
@Composable
private fun MdFence(block: MdBlock.Code, look: MdLook, cursor: Boolean) {
    val tag = block.info.substringBefore(' ').substringBefore(':').lowercase()
    when (tag) {
        "ask" -> return
        "run", "search", "fetch" -> {
            val jobs = block.code.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                jobs.forEach { job ->
                    Text(
                        when (tag) {
                            "run" -> "$ $job"
                            "search" -> "search $job"
                            else -> "read $job"
                        },
                        color = TextPrimary,
                        fontSize = (look.fontSize.value - 2).sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Background)
                            .border(1.dp, Border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            return
        }
    }
    CodeBlock(block.info, block.code, look.fontSize, cursor)
}

/**
 * A fenced code block: monospace, its own well, and horizontally scrollable.
 *
 * The scroll is the point: a command that soft-wraps mid-flag is a broken command when
 * pasted. Long blocks fold to their first lines until opened.
 */
@Composable
private fun CodeBlock(info: String, code: String, fontSize: TextUnit, cursor: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val rows = remember(code) { code.lines() }
    val isDiff = remember(info, code) {
        info.startsWith("diff", ignoreCase = true) || info.startsWith("patch", ignoreCase = true) ||
            rows.any { it.startsWith("@@ ") || it.startsWith("diff --git") }
    }
    val tall = !cursor && rows.size > TALL_LINES
    val shown = if (tall && !open) rows.take(TALL_LINES - 2) else rows
    val label = info.substringBefore(' ').ifBlank { if (isDiff) "diff" else "text" }
    val plus = if (isDiff) rows.count { it.startsWith("+") && !it.startsWith("+++") } else 0
    val minus = if (isDiff) rows.count { it.startsWith("-") && !it.startsWith("---") } else 0
    val body = remember(shown, isDiff, cursor) {
        buildAnnotatedString {
            shown.forEachIndexed { i, line ->
                if (i > 0) append('\n')
                val tint = when {
                    !isDiff -> null
                    line.startsWith("+") && !line.startsWith("+++") -> ServerGood
                    line.startsWith("-") && !line.startsWith("---") -> ServerCritical
                    line.startsWith("@@") -> Primary
                    else -> null
                }
                if (tint != null) withStyle(SpanStyle(color = tint, background = tint.copy(alpha = 0.08f))) { append(line) } else append(line)
            }
            if (cursor) append(CURSOR)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (isDiff) {
                Text("+$plus", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ServerGood)
                Spacer(Modifier.width(4.dp))
                Text("−$minus", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ServerCritical)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (copied) "Copied" else "Copy",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (copied) Primary else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Code", code))) }
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            text = body,
            fontSize = (fontSize.value - 1).sp,
            lineHeight = (fontSize.value + 5).sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = if (tall) 4.dp else 10.dp),
        )
        if (tall) {
            Text(
                if (open) "Show less" else "Show ${rows.size - shown.size} more lines",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

private const val TALL_LINES = 18

// ── Inline ──────────────────────────────────────────────────────────────────

/** Tried in this order at each position, as the web's `mdInline` replaces them. */
private val InlineToken = Regex(
    listOf(
        """`([^`\n]+)`""", // 1 code
        """!\[([^\]\n]*)]\((\S+?)\)""", // 2 alt, 3 src
        """\[([^\]\n]+)]\((\S+?)\)""", // 4 label, 5 url
        """\*\*\*(.+?)\*\*\*""", // 6 bold italic
        """\*\*(.+?)\*\*""", // 7 bold
        """(?<![\w])__(.+?)__(?![\w])""", // 8 bold
        """~~(.+?)~~""", // 9 strike
        """(?<![\w*])\*(?![\s*])([^*\n]+?)(?<!\s)\*(?![\w*])""", // 10 italic
        """(?<![\w_])_(?![\s_])([^_\n]+?)(?<!\s)_(?![\w_])""", // 11 italic
        """(https?://[^\s<]+[^\s<.,;:!?)\]'"])""", // 12 bare URL
    ).joinToString("|"),
)

internal fun inlineMarkdown(text: String, color: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val links = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
    )
    fun AnnotatedString.Builder.walk(s: String) {
        var cursor = 0
        for (m in InlineToken.findAll(s)) {
            if (m.range.first > cursor) append(s.substring(cursor, m.range.first))
            val g = m.groups
            when {
                g[1] != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = TextPrimary, background = color.copy(alpha = 0.12f)),
                ) { append(g[1]!!.value) }
                g[3] != null -> withLink(LinkAnnotation.Url(g[3]!!.value, links)) { append(g[2]!!.value.ifBlank { "image" }) }
                g[5] != null -> withLink(LinkAnnotation.Url(g[5]!!.value, links)) { walk(g[4]!!.value) }
                g[6] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { walk(g[6]!!.value) }
                g[7] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { walk(g[7]!!.value) }
                g[8] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { walk(g[8]!!.value) }
                g[9] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { walk(g[9]!!.value) }
                g[10] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(g[10]!!.value) }
                g[11] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(g[11]!!.value) }
                g[12] != null -> {
                    val url = g[12]!!.value
                    withLink(LinkAnnotation.Url(url, links)) { append(urlLabel(url)) }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < s.length) append(s.substring(cursor))
    }
    withStyle(SpanStyle(color = color)) { walk(text) }
}

/** Release-note links read as what they are; anything else drops its scheme. */
private fun urlLabel(url: String): String = when {
    "github.com" in url && "/pull/" in url -> "PR #${url.substringAfterLast('/')}"
    "github.com" in url && "/compare/" in url -> "Changelog"
    "github.com" in url && "/releases/" in url -> "Release"
    else -> url.removePrefix("https://").removePrefix("http://").removePrefix("www.").let {
        if (it.length > 48) it.take(45) + "…" else it
    }
}
