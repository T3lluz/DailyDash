package com.macrotracker.ui.screens.ai

import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesAskCommand
import com.macrotracker.data.hermes.HermesItem
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState

/*
 * What waits on you, docked over the composer as T3 Code's phone app docks it
 * (`PendingUserInputCard.tsx`, `PendingApprovalCard.tsx`): a question with its choices as
 * rows, a line for an answer of your own, and Submit; a command with Allow once and
 * Decline. The card stands where the composer does, since answering is what there is to
 * do, and folds to a bar when you would rather type. The transcript keeps only a quiet
 * line where it was asked.
 */

private val CardShape = RoundedCornerShape(28.dp)
private val OptionShape = RoundedCornerShape(20.dp)
private val ButtonShape = RoundedCornerShape(18.dp)

/** The small capitals over a card: what kind of thing is waiting. */
@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = HealthDisplay,
        color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier,
    )
}

private enum class DockTone { PRIMARY, SECONDARY, DANGER, TEXT }

/** A full-width button, in the composer's own terms: white for the thing to do, a tint for the thing to refuse. */
@Composable
private fun DockButton(label: String, tone: DockTone, enabled: Boolean, onClick: () -> Unit, loading: Boolean = false) {
    val haptics = rememberHaptics()
    val fill by animateColorAsState(
        when {
            tone == DockTone.TEXT -> Color.Transparent
            !enabled -> Color.White.copy(alpha = 0.06f)
            tone == DockTone.PRIMARY -> TextPrimary
            tone == DockTone.DANGER -> Error.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.10f)
        },
        MacroMotion.colorTween(), label = "dockButton",
    )
    val ink = when {
        !enabled -> TextTertiary
        tone == DockTone.PRIMARY -> Color.Black
        tone == DockTone.DANGER -> Error
        tone == DockTone.TEXT -> TextSecondary
        else -> TextPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (tone == DockTone.TEXT) 40.dp else 46.dp)
            .clip(ButtonShape)
            .background(fill)
            .clickable(enabled = enabled && !loading, role = Role.Button) { haptics.click(); onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            LoadingSpinner(color = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The card's frame: the composer's glass, and never taller than the room over the keyboard. */
@Composable
private fun DockCard(hazeState: HazeState?, bottomGap: Dp, content: @Composable ColumnScope.() -> Unit) {
    // a little over half the screen at most, and never more than the room over the keyboard; the rest scrolls inside
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val tallest = minOf(screen * 0.56f, screen - keyboard - 150.dp).coerceAtLeast(220.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = bottomGap)
            .heightIn(max = tallest)
            .chromeGlass(hazeState, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** The card folded away: one bar that says what waits, over the composer. */
@Composable
internal fun RequestBar(label: String, detail: String, hazeState: HazeState?, onExpand: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Open") { haptics.tick(); onExpand() }
            .chromeGlass(hazeState, CircleShape)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(label)
        Spacer(Modifier.width(8.dp))
        Text(detail, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(AppIcons.ChevronUp, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CollapseButton(onCollapse: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClickLabel = "Fold away") { haptics.tick(); onCollapse() },
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.ChevronDown, "Fold away", tint = TextSecondary, modifier = Modifier.size(15.dp)) }
}

/** Back and forward through what a card holds, with where you are between them. */
@Composable
private fun Pager(at: Int, of: Int, onGo: (Int) -> Unit, done: (Int) -> Boolean = { false }) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, to: Int) {
            val can = to in 0 until of
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (can) 0.10f else 0.04f))
                    .clickable(enabled = can, role = Role.Button, onClickLabel = label) { haptics.tick(); onGo(to) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = if (can) TextPrimary else TextTertiary, modifier = Modifier.size(16.dp)) }
        }
        arrow(AppIcons.ChevronLeft, "Previous", at - 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(of) { i ->
                // a dot per page: wide for the one in view, filled once it is answered
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (i == at) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (i == at) TextPrimary else if (done(i)) AiAccent else Color.White.copy(alpha = 0.22f))
                        .clickable { haptics.tick(); onGo(i) },
                )
            }
        }
        arrow(AppIcons.ChevronRight, "Next", at + 1)
    }
}

/**
 * What Hermes will not guess past. A card may hold several questions: they are all there at
 * once, one in view, with arrows and dots to go between them. A choice is picked or an
 * answer typed for each, and Submit sends them together; nothing goes on a stray tap.
 * Dismissing tells Hermes to pick a default.
 */
@Composable
internal fun PendingQuestionCard(
    card: HermesItem.Clarify,
    enabled: Boolean,
    hazeState: HazeState?,
    bottomGap: Dp,
    onSubmit: (text: String, answers: List<String>) -> Unit,
    onDismiss: () -> Unit,
    onCollapse: () -> Unit,
) {
    val questions = card.all
    var at by rememberSaveable(card.id) { mutableStateOf(0) }
    val picked = remember(card.id) { mutableStateListOf<String?>().apply { repeat(questions.size) { add(null) } } }
    val typed = remember(card.id) { mutableStateListOf<String>().apply { repeat(questions.size) { add("") } } }
    fun answerOf(i: Int) = typed[i].trim().ifEmpty { picked[i].orEmpty() }
    val answered = questions.indices.count { answerOf(it).isNotEmpty() }
    val complete = answered == questions.size
    val page = at.coerceIn(0, questions.lastIndex)
    val question = questions[page]
    val haptics = rememberHaptics()
    fun submit() {
        val answers = questions.indices.map(::answerOf)
        // several answers read as a list, so Hermes sees which answer is to which question
        val text = if (answers.size == 1) answers[0] else questions.indices.joinToString("\n") { "${it + 1}. ${questions[it].question} → ${answers[it]}" }
        onSubmit(text, answers)
    }
    DockCard(hazeState, bottomGap) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow(if (questions.size > 1) "Input needed · ${page + 1} of ${questions.size}" else "Input needed")
                Text(if (questions.size > 1) "Hermes has ${questions.size} questions" else "Hermes has a question", color = TextPrimary, style = HealthDisplay, fontSize = 18.sp)
            }
            CollapseButton(onCollapse)
        }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkdownText(markdown = question.question, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, linkColor = AiAccent, breaks = true)
            question.choices.forEach { choice ->
                val on = picked[page] == choice && typed[page].isBlank()
                val fill by animateColorAsState(if (on) AiAccent.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.06f), MacroMotion.colorTween(), label = "optionFill")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(OptionShape)
                        .background(fill)
                        .clickable(enabled = enabled, role = Role.RadioButton) {
                            haptics.tick()
                            picked[page] = choice
                            typed[page] = ""
                            // on to the next one still open, so a run of questions is a run of taps
                            questions.indices.firstOrNull { it > page && answerOf(it).isEmpty() }?.let { at = it }
                        }
                        .semantics { selected = on }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(choice, color = if (on) TextPrimary else TextSecondary, fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            BasicTextField(
                value = typed[page],
                onValueChange = { typed[page] = it },
                enabled = enabled,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.5.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(AiAccent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (page < questions.lastIndex) ImeAction.Next else ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onNext = { at = page + 1 },
                    onSend = { if (complete) submit() },
                ),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(OptionShape)
                    .background(if (typed[page].isNotBlank()) AiAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (typed[page].isEmpty()) Text("Or type a custom answer", color = TextTertiary, fontSize = 14.5.sp)
                    inner()
                },
            )
        }
        if (questions.size > 1) Pager(page, questions.size, onGo = { at = it }, done = { answerOf(it).isNotEmpty() })
        DockButton(
            label = when {
                questions.size == 1 -> "Submit answer"
                complete -> "Submit ${questions.size} answers"
                else -> "$answered of ${questions.size} answered"
            },
            tone = if (complete) DockTone.PRIMARY else DockTone.SECONDARY,
            enabled = enabled && complete,
            onClick = ::submit,
        )
        DockButton("Dismiss without answering", DockTone.TEXT, enabled = enabled, onClick = onDismiss)
    }
}

/**
 * A command Hermes may not run by itself. Nothing on the card runs until it is tapped, and
 * the tap goes straight to the bridge: Hermes is not in that path and cannot approve its
 * own commands. A card with several commands asks for them one at a time.
 */
@Composable
internal fun PendingApprovalCard(
    card: HermesItem.Ask,
    busy: Boolean,
    running: Set<String>,
    hazeState: HazeState?,
    bottomGap: Dp,
    onDecide: (index: Int, run: Boolean) -> Unit,
    onCollapse: () -> Unit,
) {
    // every command still to decide is here at once: one in view, arrows to the others, any order
    val waiting = card.cmds.indices.filter { card.cmds[it].pending }
    if (waiting.isEmpty()) return
    var at by rememberSaveable(card.id) { mutableStateOf(0) }
    val page = at.coerceIn(0, waiting.lastIndex)
    val index = waiting[page]
    val cmd = card.cmds[index]
    val isRunning = "${card.id}:$index" in running
    DockCard(hazeState, bottomGap) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow(if (waiting.size > 1) "Approval needed · ${page + 1} of ${waiting.size}" else "Approval needed")
                Text(cmd.what?.trim()?.takeIf { it.isNotEmpty() } ?: "Run a command", style = HealthDisplay, color = TextPrimary, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
            CollapseButton(onCollapse)
        }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // the command as it will run: scrolled sideways, never wrapped mid-flag
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(cmd.cmd.trim(), color = TextPrimary, fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace, softWrap = false)
            }
            cmd.why.trim().takeIf { it.isNotEmpty() && it != cmd.what?.trim() }?.let {
                Text(it, color = TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            Text(riskLine(cmd), color = if (cmd.risk == "grave") Error else ServerWarn, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        if (waiting.size > 1) Pager(page, waiting.size, onGo = { at = it })
        DockButton(if (isRunning) "Running" else "Allow once", DockTone.PRIMARY, enabled = !busy, loading = isRunning, onClick = { onDecide(index, true) })
        DockButton("Decline", DockTone.DANGER, enabled = !busy && !isRunning, onClick = { onDecide(index, false) })
    }
}

private fun riskLine(cmd: HermesAskCommand): String {
    val risk = when (cmd.risk) {
        "grave" -> "This cannot be undone."
        "act" -> "This changes something on the server."
        else -> "This only reads."
    }
    // what cannot be undone has no way back to name
    return cmd.undo?.trim()?.takeIf { it.isNotEmpty() && cmd.risk != "grave" }?.let { "$risk To undo: $it" } ?: risk
}

/** What a request reads as on the folded bar. */
internal fun requestBarText(item: HermesItem): Pair<String, String> = when (item) {
    is HermesItem.Clarify -> "Input needed" to item.question.replace(Regex("\\s+"), " ")
    is HermesItem.Ask -> {
        val left = item.cmds.count { it.pending }
        "Approval needed" to if (left == 1) item.cmds.first { it.pending }.cmd.replace(Regex("\\s+"), " ") else "$left commands"
    }
    else -> "" to ""
}
