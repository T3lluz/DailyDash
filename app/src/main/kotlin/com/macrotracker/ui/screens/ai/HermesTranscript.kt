package com.macrotracker.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.FeedRow
import com.macrotracker.data.hermes.HermesFileChange
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.data.hermes.StepKind
import com.macrotracker.data.hermes.WorkStep
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.TileDark
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.tookShort
import kotlinx.coroutines.delay

/*
 * The transcript's rows, drawn as T3 Code's phone app draws a thread (`ThreadFeed.tsx`,
 * `thread-work-log.tsx`): what Hermes does is a quiet line with an icon, grouped and
 * closed to a summary; what it says is plain text with no bubble and no byline; a finished
 * run is "Worked for 1m 12s" over its answer. Only what you said is a bubble.
 */

/** A work row is this tall, as T3 Code's `min-h-8`. */
private val WorkRowHeight = 32.dp
private val IconSlot = 24.dp

/** An open group shows this much of its steps and scrolls the rest (T3's `WORK_GROUP_MAX_HEIGHT`). */
private val GroupMaxHeight = 256.dp
private val DetailMaxHeight = 240.dp
private val RowShape = RoundedCornerShape(6.dp)
private val Mono = FontFamily.Monospace

private fun iconOf(kind: StepKind): ImageVector = when (kind) {
    StepKind.THOUGHT -> AppIcons.Brain
    StepKind.COMMAND -> AppIcons.Terminal
    StepKind.READ -> AppIcons.Eye
    StepKind.EDIT -> AppIcons.Edit
    StepKind.WEB_SEARCH, StepKind.FETCH -> AppIcons.Globe
    StepKind.CODE_SEARCH -> AppIcons.Search
    StepKind.RESULT -> AppIcons.ArrowUp
    StepKind.OTHER -> AppIcons.Wrench
}

/** One kind's icon when a group is all of a kind, else the tool box. */
private fun iconOf(group: FeedRow.Work): ImageVector {
    if (group.live) return iconOf((group.steps.lastOrNull { it.running } ?: group.steps.last()).kind)
    // a search and a page read share the globe, so a group of both keeps it
    val icons = group.steps.filter { it.kind != StepKind.THOUGHT }.map { iconOf(it.kind) }.distinct()
    return when (icons.size) {
        0 -> AppIcons.Brain
        1 -> icons.first()
        else -> AppIcons.Wrench
    }
}

/** The chevron every fold shares: it turns as the fold opens. */
@Composable
private fun Chevron(open: Boolean, modifier: Modifier = Modifier, right: Boolean = false) {
    val turn = animateFloatAsState(if (open) (if (right) 90f else 180f) else 0f, MacroMotion.fadeTween(), label = "chevron")
    Icon(
        if (right) AppIcons.ChevronRight else AppIcons.ChevronDown,
        null,
        tint = TextTertiary,
        modifier = modifier.graphicsLayer { rotationZ = turn.value },
    )
}

/** A finished run, folded: how long Hermes worked. A tap opens it to everything it did and said on the way. */
@Composable
internal fun FoldRow(row: FeedRow.Fold, open: Boolean, onToggle: () -> Unit) {
    val label = row.ms?.let { "Worked for ${tookShort(it)}" } ?: "Worked"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .drawBehind {
                drawLine(Border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide the work" else "Show the work", onClick = onToggle)
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = TabularNumbers, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Chevron(open, Modifier.size(15.dp), right = true)
    }
}

/** Hermes is working and there is nothing to show for it yet. */
@Composable
internal fun ThinkingRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WorkRowHeight).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Brain, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        ShimmerText(label, TextSecondary, 14.sp, fontWeight = FontWeight.Normal)
    }
}

/**
 * A run of steps. Closed, it is one line: what the steps come to, or while the turn runs
 * the step Hermes is on, shimmering, with the thought behind it above. Open, the steps
 * are listed under it, each opening to what it ran and what came back.
 */
@Composable
internal fun WorkGroup(row: FeedRow.Work, isOpen: (String) -> Boolean, onToggle: (String) -> Unit, onOpenPath: (String) -> Unit) {
    val single = row.single
    if (single != null) {
        // one step needs no header: the row is the step
        StepRow(single, open = isOpen(single.id), onToggle = { onToggle(single.id) }, onOpenPath = onOpenPath, modifier = Modifier.padding(bottom = 2.dp))
        return
    }
    val open = isOpen(row.key)
    val thought = row.thought
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        if (thought.isNotBlank() && !open) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RowShape).clickable { onToggle(row.key) }.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Brain, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    thought, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                )
            }
        }
        WorkLine(
            icon = iconOf(row),
            label = row.summary,
            shimmer = row.live,
            failed = row.failed,
            open = open,
            expandable = true,
            onClick = { onToggle(row.key) },
            description = "${row.summary}, ${row.steps.size} ${if (row.steps.size == 1) "step" else "steps"}",
        )
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            val scroll = rememberScrollState()
            // a live group stays on its newest step, unless it was scrolled up to read
            LaunchedEffect(row.steps.size, row.live) {
                if (row.live && scroll.maxValue - scroll.value < 160) scroll.scrollTo(scroll.maxValue)
            }
            Column(Modifier.fillMaxWidth().heightIn(max = GroupMaxHeight).verticalScroll(scroll)) {
                row.steps.forEach { step ->
                    StepRow(step, open = isOpen(step.id), onToggle = { onToggle(step.id) }, onOpenPath = onOpenPath, shimmer = row.live && step.running)
                }
            }
        }
    }
}

/** One step: an icon and a line. A tap opens what it ran and what came back; a hold copies it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepRow(
    step: WorkStep,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
    shimmer: Boolean = false,
) {
    // a file Hermes read or changed opens in the file viewer (T3 Code's file rows); a failed one opens to why
    val file = step.path?.takeIf { (step.kind == StepKind.READ || step.kind == StepKind.EDIT) && !step.failed && !step.running && (it.startsWith("/") || it.startsWith("~")) }
    val expandable = file == null && (step.detail != null || step.command != null || step.url != null)
    val copy = rememberCopy(listOfNotNull(step.command ?: step.label, step.detail).joinToString("\n\n"))
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1400); copied = false }
    }
    Column(modifier.fillMaxWidth()) {
        WorkLine(
            icon = iconOf(step.kind),
            label = if (shimmer) step.doing else step.label,
            shimmer = shimmer,
            failed = step.failed,
            open = open,
            expandable = expandable,
            onClick = if (file != null) ({ onOpenPath(file) }) else if (expandable) onToggle else null,
            opens = file != null,
            onLongClick = { copy(); copied = true },
            copied = copied,
            description = if (step.failed) "${step.label}, failed" else step.label,
        )
        AnimatedVisibility(open && expandable, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            StepDetail(step)
        }
    }
}

/** What a step ran and what came back, under its row. */
@Composable
private fun StepDetail(step: WorkStep) {
    SelectionContainer {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = IconSlot + 8.dp, end = 4.dp, top = 2.dp, bottom = 6.dp)
                .heightIn(max = DetailMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            if (step.kind == StepKind.THOUGHT) {
                MarkdownText(markdown = step.detail.orEmpty(), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, linkColor = AiAccent, breaks = true)
                return@Column
            }
            step.command?.let { Text(it.trim(), color = TextPrimary, fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = Mono) }
            step.detail?.let {
                Text(
                    // the end of long output is where the answer usually is
                    if (it.length > DETAIL_CHARS) "…" + it.takeLast(DETAIL_CHARS) else it,
                    color = if (step.failed && step.command == null) ServerCritical else TextSecondary,
                    fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = Mono,
                    modifier = Modifier.padding(top = if (step.command != null) 6.dp else 0.dp),
                )
            }
            step.url?.let { url ->
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                Text(
                    "Open the page",
                    color = AiAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp).clip(RowShape).clickable { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp),
                )
            }
            step.exit?.takeIf { it != 0 }?.let {
                Text("exit $it", color = ServerCritical, fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = Mono, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private const val DETAIL_CHARS = 6000

/** The line every work row is: icon, one line of words, and at the far end whether it failed and whether it opens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkLine(
    icon: ImageVector,
    label: String,
    shimmer: Boolean,
    failed: Boolean,
    open: Boolean,
    expandable: Boolean,
    onClick: (() -> Unit)?,
    description: String,
    onLongClick: (() -> Unit)? = null,
    copied: Boolean = false,
    /** The row leads somewhere (a file) and does not unfold. */
    opens: Boolean = false,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = if (onLongClick != null) "Copy" else null,
                        onLongClick = onLongClick,
                        onClick = { if (onClick != null) { haptics.tick(); onClick() } },
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = WorkRowHeight)
            .padding(horizontal = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (expandable) stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (failed) Error.copy(alpha = 0.7f) else TextTertiary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        if (shimmer) {
            ShimmerText(label, TextSecondary, 14.sp, Modifier.weight(1f), FontWeight.Normal)
        } else {
            Text(label, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (copied) Text("Copied", color = ServerGood, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        if (failed) Icon(AppIcons.Close, null, tint = Error.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 2.dp).size(11.dp))
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (expandable) Chevron(open, Modifier.size(12.dp))
            else if (opens) Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
        }
    }
}

/** What a finished run's answer can do, and whether a turn is running (nothing can be retried or forked mid-turn). */
internal class AnswerActions(
    val idle: Boolean,
    /** The chat's newest answer: the one that can be asked for again. */
    val lastKey: String?,
    /** The chat is in Plan: its newest answer is a plan that can be built. */
    val planMode: Boolean,
    val onRetry: () -> Unit,
    val onFork: (Long) -> Unit,
    val onImplement: () -> Unit,
    val onOpenChanges: (String) -> Unit,
)

/**
 * Something Hermes said: plain text, no bubble, no byline. A finished run's answer carries
 * what the run changed and a quiet row of actions with when it was said.
 */
@Composable
internal fun AssistantMessage(row: FeedRow.Message, actions: AnswerActions) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = if (row.final) 14.dp else 6.dp)) {
        if (row.streaming) {
            MarkdownText(markdown = row.text, fontSize = 15.sp, lineHeight = 22.sp, color = TextPrimary, linkColor = AiAccent, breaks = true, streaming = true)
        } else {
            // selectable: a held word picks it, and the handles take what is wanted
            SelectionContainer {
                MarkdownText(markdown = row.text, fontSize = 15.sp, lineHeight = 22.sp, color = TextPrimary, linkColor = AiAccent, breaks = true)
            }
        }
        if (!row.final) return@Column
        val last = row.key == actions.lastKey
        if (row.changes.isNotEmpty()) ChangesStat(row.changes) { actions.onOpenChanges(row.key) }
        if (actions.planMode && actions.idle && last) {
            // Plan mode wrote a plan: build it, which leaves Plan and says so to Hermes
            Row(
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TileDark).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.ListChecks, null, tint = AiAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Plan ready", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton(icon = AppIcons.Play, label = "Implement", accent = AiAccent, emphasized = true, onClick = actions.onImplement)
            }
        }
        val clock = rememberClockFormatter()
        val meta = listOfNotNull(
            row.ts.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(clock) },
            row.model?.let { HermesModelName(it) ?: it.substringAfter(':') }?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        Row(Modifier.padding(top = 2.dp).graphicsLayer { translationX = -6.dp.toPx() }, verticalAlignment = Alignment.CenterVertically) {
            CopyButton(row.text)
            // a new chat from here: this answer and what came before it
            if (row.ts > 0 && actions.idle) {
                IconButton(onClick = { actions.onFork(row.ts) }, modifier = Modifier.size(28.dp)) {
                    Icon(AppIcons.GitFork, "Fork from here", tint = TextTertiary, modifier = Modifier.size(15.dp))
                }
            }
            // the newest answer can be asked for again: the question is sent anew
            if (last && actions.idle) {
                IconButton(onClick = actions.onRetry, modifier = Modifier.size(28.dp)) {
                    Icon(AppIcons.Refresh, "Ask again", tint = TextTertiary, modifier = Modifier.size(15.dp))
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, color = TextTertiary, fontSize = 11.5.sp, style = TabularNumbers, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** What the run changed, in a line (T3 Code's turn diff stat): a tap opens the files. */
@Composable
private fun ChangesStat(changes: List<HermesFileChange>, onOpen: () -> Unit) {
    val files = changes.map { it.path }.distinct().size
    val plus = changes.sumOf { it.plus }
    val minus = changes.sumOf { it.minus }
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .graphicsLayer { translationX = -6.dp.toPx() }
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Review the changes") { haptics.tick(); onOpen() }
            .heightIn(min = 40.dp)
            .padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$files ${if (files == 1) "file" else "files"} changed, $plus added, $minus removed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Edit, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text("$files ${if (files == 1) "file" else "files"} changed", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text("+$plus", color = ServerGood, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
        Spacer(Modifier.width(6.dp))
        Text("−$minus", color = ServerCritical, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
        Spacer(Modifier.width(6.dp))
        Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
    }
}

/**
 * A turn that ended badly: said plainly, where it happened. The newest one offers the ways
 * out (T3 Code's recovery card): ask again, another model, and what the plans have left
 * when it reads like a limit.
 */
@Composable
internal fun ErrorRow(item: HermesItem.Error, recover: ErrorActions?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Warning, null, tint = Error, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            val limit = remember(item.text) { LIMIT_WORDS.containsMatchIn(item.text) }
            SelectionContainer {
                Column {
                    Text(if (limit) "Usage limit reached" else "Hermes stopped", color = Error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(item.text.trim(), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (recover != null) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    SmallActionChip(icon = AppIcons.Refresh, label = "Ask again", onClick = recover.onRetry)
                    SmallActionChip(icon = AppIcons.SwapVertical, label = "Change model", onClick = recover.onPickModel)
                    if (limit) SmallActionChip(icon = AppIcons.ChartColumn, label = "Usage", onClick = recover.onOpenUsage)
                }
            }
        }
    }
}

/** What the newest error can lead to. */
internal class ErrorActions(val onRetry: () -> Unit, val onPickModel: () -> Unit, val onOpenUsage: () -> Unit)

private val LIMIT_WORDS = Regex("usage limit|rate limit|quota|out of credits|too many requests|\\b429\\b|limit reached|overloaded", RegexOption.IGNORE_CASE)

/**
 * Where Hermes asked for something, as a quiet line: the card itself is docked over the
 * composer while it waits. An answered question stays as what was answered, and opens to
 * what was asked (T3 Code keeps a question and its answer out of the fold).
 */
@Composable
internal fun RequestRow(item: HermesItem, open: Boolean, onToggle: () -> Unit) {
    when (item) {
        is HermesItem.Ask -> {
            val cmd = item.cmds.firstOrNull { it.pending }?.cmd ?: item.cmds.firstOrNull()?.cmd.orEmpty()
            val label = "Waiting for approval: ${cmd.replace(Regex("\\s+"), " ")}"
            WorkLine(icon = AppIcons.Lock, label = label, shimmer = false, failed = false, open = false, expandable = false, onClick = null, description = label)
        }
        is HermesItem.Clarify -> if (item.open) {
            val label = "Asked: ${item.question.replace(Regex("\\s+"), " ")}"
            WorkLine(icon = AppIcons.Help, label = label, shimmer = false, failed = false, open = false, expandable = false, onClick = null, description = label)
        } else {
            val label = if (item.state == "skipped" || item.answer.isNullOrBlank()) "Skipped the question" else "Answered: ${item.answer.replace(Regex("\\s+"), " ")}"
            Column(Modifier.fillMaxWidth()) {
                WorkLine(icon = AppIcons.Chat, label = label, shimmer = false, failed = false, open = open, expandable = true, onClick = onToggle, description = label)
                AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth().padding(start = IconSlot + 8.dp, end = 4.dp, top = 2.dp, bottom = 6.dp)) {
                            Text(item.question.trim(), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                            item.answer?.takeIf { it.isNotBlank() }?.let {
                                Text(it.trim(), color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

/** When a row was said, for the day dividers; work carries no time of its own. */
internal fun rowTime(row: FeedRow): Long = when (row) {
    is FeedRow.User -> row.item.ts
    is FeedRow.Message -> row.ts
    is FeedRow.Fold -> row.rows.firstNotNullOfOrNull { rowTime(it).takeIf { t -> t > 0 } } ?: 0L
    else -> 0L
}

/** What find in chat searches in a row: what was said and answered, a folded run's own words, and what a step ran. */
internal fun rowText(row: FeedRow): String = when (row) {
    is FeedRow.User -> row.item.text
    is FeedRow.Message -> row.text
    is FeedRow.Fold -> row.rows.joinToString("\n") { rowText(it) }
    is FeedRow.Work -> row.steps.joinToString("\n") { it.label }
    is FeedRow.Card -> when (val i = row.item) {
        is HermesItem.Clarify -> i.question
        is HermesItem.Error -> i.text
        is HermesItem.Ask -> i.cmds.joinToString("\n") { it.cmd }
        else -> ""
    }
    is FeedRow.Thinking -> ""
}
