package com.macrotracker.ui.screens.ai

import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.screens.health.pressSink
import androidx.compose.ui.graphics.Shape
import com.macrotracker.ui.screens.home.FieldShape
import com.macrotracker.ui.screens.home.roomFieldColors
import com.macrotracker.ui.screens.home.RoomNeutral
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesAttachment
import com.macrotracker.data.hermes.HermesCatalog
import com.macrotracker.data.hermes.HermesCommand
import com.macrotracker.data.hermes.HermesMode
import com.macrotracker.data.hermes.HermesModelOption
import com.macrotracker.data.hermes.HermesStatus
import com.macrotracker.data.hermes.HermesThreadSummary
import com.macrotracker.data.hermes.HermesTurnActivity
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.IconTile
import com.macrotracker.ui.components.TileMenu
import com.macrotracker.ui.components.TileMenuGroup
import com.macrotracker.ui.components.TileMenuItem
import com.macrotracker.ui.components.TileMenuTiles
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import com.macrotracker.ui.util.agoShort

/*
 * The frame around a Hermes conversation, shaped like the t3lluz dashboard's panel without
 * its side pane: a rail of threads (here a sheet, the phone's own place for one), the
 * conversation, and a tall composer with the pickers under the words — what Hermes may do,
 * which model it thinks with, and how hard.
 */

internal fun modeColor(kind: String): Color = when (kind) {
    "chat" -> TextSecondary
    "read" -> AiAccent
    "write" -> ServerCritical
    else -> ServerWarn
}

internal fun modeIcon(kind: String): ImageVector = when (kind) {
    "chat" -> AppIcons.Chat
    "read" -> AppIcons.Eye
    "write" -> AppIcons.Bolt
    else -> AppIcons.Key
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
internal fun HermesHeader(
    title: String,
    status: String,
    working: Boolean,
    /** Another thread is working or waiting on an approval: worth a glance at the rail. */
    railAttention: Boolean,
    pinned: Boolean,
    hasThread: Boolean,
    onOpenRail: () -> Unit,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onUsePhoneAi: (() -> Unit)?,
    onOpenConsole: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenTilesmith: () -> Unit = {},
    onCopyChat: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenGit: () -> Unit = {},
    followUp: String = "queue",
    onToggleFollowUp: () -> Unit = {},
    onFind: () -> Unit = {},
    onOpenInfo: () -> Unit = {},
    /** The maker of the model in use, for the logo beside the status. */
    brand: String = "",
    /** What the header's usage square fills with; null draws a plain chart icon. */
    usage: com.macrotracker.data.dashboard.UsageSnapshot? = null,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            HeaderRound(AppIcons.PanelLeft, "Chats") { haptics.tick(); onOpenRail() }
            if (railAttention) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(ServerWarn),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // The title opens the chats too, as ChatGPT's and Claude's do; rename is in the menu.
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { haptics.tick(); onOpenRail() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.ifBlank { "New chat" },
                    color = TextPrimary,
                    style = HealthDisplay,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(3.dp))
                Icon(AppIcons.ChevronDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // the model and mode are the chat's details: a tap opens them
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = hasThread) { haptics.tick(); onOpenInfo() },
            ) {
                if (hasThread && brand.isNotBlank()) {
                    ModelLogo(brand, 16.dp)
                    Spacer(Modifier.width(5.dp))
                }
                Text(status, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Usage, one tap away: the square fills with the plan in use.
        UsageButton(usage, onOpenUsage)
        Spacer(Modifier.width(6.dp))
        HeaderRound(AppIcons.NewChat, "New chat") { haptics.tick(); onNewChat() }
        Spacer(Modifier.width(8.dp))
        Box {
            HeaderRound(AppIcons.Settings, "Chat options") { haptics.tick(); menuOpen = true }
            // Sorted by what it is about: this chat, how Hermes works, where else to go,
            // and what can't be undone last and apart.
            TileMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, color = AiMenu, minWidth = 264.dp) {
                if (hasThread) {
                    TileMenuGroup {
                        TileMenuItem("Chat details", AppIcons.Info) { menuOpen = false; onOpenInfo() }
                        TileMenuItem("Rename", AppIcons.Edit) { menuOpen = false; onRename() }
                        TileMenuItem(if (pinned) "Unpin" else "Pin to the top", if (pinned) AppIcons.StarFilled else AppIcons.Star) { menuOpen = false; onTogglePin() }
                        TileMenuItem("Find in chat", AppIcons.Search) { menuOpen = false; onFind() }
                        TileMenuItem("Copy chat", AppIcons.Copy) { menuOpen = false; onCopyChat() }
                    }
                }
                TileMenuGroup {
                    TileMenuItem(
                        "While working",
                        if (followUp == "steer") AppIcons.Bolt else AppIcons.ListChecks,
                        value = if (followUp == "steer") "Steer" else "Queue",
                    ) { menuOpen = false; onToggleFollowUp() }
                    if (onUsePhoneAi != null) {
                        TileMenuItem("Use this phone's AI", AppIcons.Bot) { menuOpen = false; onUsePhoneAi() }
                    }
                }
                TileMenuTiles {
                    IconTile(AppIcons.GitFork, "Git") { menuOpen = false; onOpenGit() }
                    IconTile(AppIcons.Folder, "Files") { menuOpen = false; onOpenFiles() }
                    IconTile(AppIcons.SquareTerminal, "Console") { menuOpen = false; onOpenConsole() }
                    IconTile(AppIcons.Blocks, "Tilesmith") { menuOpen = false; onOpenTilesmith() }
                }
                if (hasThread) {
                    TileMenuGroup {
                        TileMenuItem("Clear this chat", AppIcons.Refresh) { menuOpen = false; onClear() }
                        TileMenuItem("Delete this chat", AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                    }
                }
            }
        }
    }
}

/** A round button of the AI tab's header, as a page's bar draws its own. */
@Composable
internal fun HeaderRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var play by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .pressSink(press, MacroMotion.PRESSED_ROUND)
            .size(42.dp)
            .clip(CircleShape)
            .background(AiRoom)
            .clickable(interactionSource = press, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label) {
                play++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        com.macrotracker.ui.components.AnimatedIcon(icon, play = play, tint = { TextPrimary }, size = 19.dp, contentDescription = label)
    }
}

@Composable
internal fun RailHeading(label: String) {
    Text(
        label,
        style = HealthDisplay,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
internal fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** The AI tab's warm room; elsewhere the app's neutral well. */
    fill: Color = AiRoom,
) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(AiAccent),
            modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = TextTertiary, fontSize = 14.sp)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                AppIcons.Close,
                contentDescription = "Clear search",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp).clickable { haptics.tick(); onValueChange("") },
            )
        }
    }
}

// ── Composer ────────────────────────────────────────────────────────────────

/**
 * The composer, in two shapes. Open (while it has focus, files, a draft of several lines or
 * a new chat): the words on top, and under them attach, the mode tag, the model pill, the
 * usage square and send. Folded: one row of attach, a line, the model's logo, the usage
 * square and send. While Hermes works, send is Stop until something is typed; then it
 * queues the line.
 */
@Composable
internal fun HermesComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    busy: Boolean,
    hint: String,
    mode: HermesMode,
    model: HermesModelOption?,
    modelFallback: String?,
    switchingModel: Boolean,
    modifiers: HermesCatalog.Modifiers,
    attachments: List<HermesAttachment>,
    uploading: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    /** Back to Agent from /plan or /ask. */
    onClearMode: () -> Unit,
    onPickModel: () -> Unit,
    hazeState: HazeState?,
    /** The usage square, beside send (ComposerUsageMeter). */
    usage: (@Composable () -> Unit)? = null,
    /** A chat with nothing in it yet: the composer stays open, as an invitation. */
    roomy: Boolean = false,
    /** The turn running now: the panel grows a tab on its top left that says what Hermes is on. */
    working: com.macrotracker.data.hermes.HermesLive? = null,
    workingLabel: String = "",
    onWorking: () -> Unit = {},
    /** Speak instead of type; null hides the microphone. */
    onDictate: (() -> Unit)? = null,
    /** What sending does while Hermes works: `steer` or `queue` (T3 Code's follow-up behaviour). */
    followUp: String = "queue",
    /** The other one, from a hold on the send button. */
    onSendAlternate: () -> Unit = {},
    /**
     * Where the composer's glass body stands over the pane's foot: how far up its top reaches (not
     * the working tab on it), and the gap under it.
     */
    onBody: (top: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp) -> Unit = { _, _ -> },
) {
    val bottomPad = composerBottomGap()
    val hasContent = value.isNotBlank() || attachments.isNotEmpty()
    val showStop = busy && value.isBlank()
    val canSend = enabled && hasContent && !uploading
    val sendBackground by animateColorAsState(
        targetValue = when {
            showStop -> ServerCritical
            canSend -> AiAccent
            else -> Border
        },
        animationSpec = MacroMotion.colorTween(),
        label = "hermesSend",
    )
    val haptics = rememberHaptics()
    // Open while it is being typed in (or has files, or the chat is new); otherwise one row,
    // so a chat being read keeps the screen. T3 Code's phone composer folds the same way.
    var focused by remember { mutableStateOf(false) }
    val open = focused || roomy || attachments.isNotEmpty() || uploading || '\n' in value

    // Every control is a tile of the panel, joined as Weave's are: round only where the
    // group meets the panel's own corners, small corners where two tiles meet.
    val attach: @Composable (Shape) -> Unit = { shape ->
        ComposerTile(shape, label = "Attach a file", enabled = enabled && !uploading, onClick = { haptics.tick(); onAttach() }) {
            Icon(AppIcons.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
    val send: @Composable (Shape) -> Unit = { shape ->
        SendControl(
            busy = busy,
            hasText = hasContent,
            canSend = canSend,
            followUp = followUp,
            onSend = onSend,
            onAlternate = onSendAlternate,
            onStop = onStop,
            shape = shape,
        )
    }

    // The working tab is the panel itself: one outline, one sheet of glass, no seam where they meet.
    val density = LocalDensity.current
    val tabHeight by androidx.compose.animation.core.animateDpAsState(if (working != null) WorkingTabHeight else 0.dp, MacroMotion.entranceSpring(), label = "workingTab")
    var tabWidth by remember { mutableStateOf(0) }
    val panel = remember(tabHeight, tabWidth, density) {
        ComposerPanelShape(tabWidth = tabWidth.toFloat(), tabHeight = with(density) { tabHeight.coerceAtLeast(0.dp).toPx() }, density = density)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = bottomPad)
            // The navbar's and the island's glass: lift, blur, tint and edge alike.
            .chromeGlass(hazeState, panel),
    ) {
    // clipped to the strip it stands in, and gone with it: at rest nothing of it lies over the field
    Box(Modifier.height(tabHeight.coerceAtLeast(0.dp)).clipToBounds().padding(start = WorkingTabInset)) {
        if (tabHeight > 0.5.dp) WorkingStatus(working, workingLabel, onWorking, Modifier.onSizeChanged { tabWidth = it.width })
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onBody(with(density) { it.height.toDp() } + bottomPad, bottomPad) }
            .animateContentSize(MacroMotion.entranceSpring())
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (attachments.isNotEmpty() || uploading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                attachments.forEach { a ->
                    AttachmentChip(a, onRemove = { onRemoveAttachment(a.id) })
                }
                if (uploading) LoadingSpinner(color = AiAccent, size = 16.dp)
            }
        }
        // One field in both shapes, so folding never drops the cursor or the keyboard.
        // Folded: one row of tiles, attach, the field, the model, send. Open: the field across
        // the top, the tools as a row of tiles under it.
        val trailing = if (open) 0 else (if (mode.kind != "write") 1 else 0) + 1 + (if (usage != null) 1 else 0) + 1
        val fieldFill by animateColorAsState(if (focused) ComposerTileFillHi else ComposerTileFill, MacroMotion.colorTween(), label = "fieldFill")
        Row(horizontalArrangement = Arrangement.spacedBy(ComposerTileGap), verticalAlignment = Alignment.CenterVertically) {
            if (!open) attach(composerTileShape(top = true, bottom = true, first = true, last = false))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = ComposerTileSize)
                    .clip(composerTileShape(top = true, bottom = !open, first = open, last = open || trailing == 0))
                    .background(fieldFill),
                contentAlignment = Alignment.CenterStart,
            ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    // A hardware keyboard: Enter sends, Shift+Enter is a new line. A phone's own keyboard
                    // sends no key event for Enter (it commits a newline), so it is left alone.
                    .onPreviewKeyEvent { e ->
                        val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (enter && e.type == KeyEventType.KeyDown && !e.isShiftPressed && e.isPhysicalKeyboard()) {
                            if (canSend) onSend()
                            true
                        } else {
                            false
                        }
                    }
                    .then(if (open) Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp) else Modifier.padding(horizontal = 14.dp)),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(AiAccent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = if (open) 2 else 1,
                maxLines = if (open) 8 else 1,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            if (open) hint else hint.substringBefore("  /"),
                            color = TextSecondary, fontSize = 15.sp, lineHeight = 21.sp,
                            maxLines = if (open) 2 else 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            }
            if (!open) {
                // a chat in /plan or /ask says so folded too
                if (mode.kind != "write") {
                    ModeTag(mode, onClearMode, shape = composerTileShape(top = true, bottom = true, first = false, last = false))
                }
                ModelPill(
                    model, modelFallback, switchingModel, modifiers, onPickModel, logoOnly = true,
                    shape = composerTileShape(top = true, bottom = true, first = false, last = false),
                )
                usage?.let {
                    ComposerTile(composerTileShape(top = true, bottom = true, first = false, last = false)) { it() }
                }
                send(composerTileShape(top = true, bottom = true, first = false, last = true))
            }
        }
        if (open) {
            val mid = composerTileShape(top = false, bottom = true, first = false, last = false)
            Row(
                Modifier.padding(top = ComposerTileGap),
                horizontalArrangement = Arrangement.spacedBy(ComposerTileGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                attach(composerTileShape(top = false, bottom = true, first = true, last = false))
                // Agent needs no word on the bar; a chat in /plan or /ask shows it, tap to leave.
                if (mode.kind != "write") ModeTag(mode, onClearMode, shape = mid)
                ModelPill(
                    model = model,
                    fallback = modelFallback,
                    switching = switchingModel,
                    modifiers = modifiers,
                    onClick = onPickModel,
                    shape = mid,
                    modifier = Modifier.weight(1f),
                )
                if (onDictate != null) {
                    ComposerTile(mid, label = "Dictate", enabled = enabled, onClick = { haptics.tick(); onDictate() }) {
                        Icon(AppIcons.Mic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(19.dp))
                    }
                }
                usage?.let { ComposerTile(mid) { it() } }
                send(composerTileShape(top = false, bottom = true, first = false, last = true))
            }
        }
    }
    }
}

/**
 * Model and depth as one pill, the way the web's composer keeps them together: the brand,
 * the family, then its depth and Fast / Thinking when the family has them. One tap opens
 * the one sheet with both.
 */
@Composable
internal fun ModelPill(
    model: HermesModelOption?,
    fallback: String?,
    switching: Boolean,
    modifiers: HermesCatalog.Modifiers,
    onClick: () -> Unit,
    /** The folded composer's: the maker's logo alone, in a round button. */
    logoOnly: Boolean = false,
    /** Its corners: a pill, or its place among the composer's tiles. */
    shape: Shape = RoundedCornerShape(999.dp),
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val cur = modifiers.current
    val depth = if (modifiers.efforts.size > 1) {
        cur?.effort?.takeIf { it.isNotBlank() }?.let { HermesCatalog.EFFORT_LABEL[it] ?: it } ?: "Auto"
    } else {
        null
    }
    val name = when {
        switching -> "Switching\u2026"
        model != null -> model.familyLabel.ifBlank { model.label }
        else -> fallback ?: "Pick a model"
    }
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressSink(press)
            .height(ComposerTileSize)
            .then(if (logoOnly) Modifier.width(ComposerTileSize) else Modifier)
            .clip(shape)
            .background(ComposerTileFill)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) { haptics.tick(); onClick() }
            .semantics { contentDescription = listOfNotNull("Model $name", depth?.let { "$it depth" }).joinToString(", ") }
            .padding(start = if (logoOnly) 0.dp else 11.dp, end = if (logoOnly) 0.dp else 12.dp),
        horizontalArrangement = if (logoOnly) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (switching) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { LoadingSpinner(color = AiAccent, size = 13.dp) }
        } else {
            BrandMark(model?.group.orEmpty(), model?.family ?: model?.id.orEmpty(), size = 22.dp)
        }
        if (logoOnly) return@Row
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = HealthDisplay,
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (depth != null && !switching) {
            Text("  \u00b7  ", color = TextTertiary, fontSize = 12.sp)
            Text(depth, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (cur?.fast == true && modifiers.fast && !switching) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.Bolt, contentDescription = null, tint = ServerWarn, modifier = Modifier.size(12.dp))
        }
        if (cur?.think == true && modifiers.think && !switching) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.Sparkles, contentDescription = null, tint = AiAccent, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(3.dp))
        Icon(AppIcons.ChevronDown, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
    }
}

/** The composer's tiles: as tall as a thumb, a quiet fill on the glass, brighter while typing. */
internal val ComposerTileSize = 44.dp
internal val ComposerTileGap = Joined.GlassGap
internal val ComposerTileFill = Color.White.copy(alpha = 0.07f)
internal val ComposerTileFillHi = Color.White.copy(alpha = 0.11f)

/** The panel's 22 dp corner less the 6 dp the tiles stand in by (as far as they stand apart); where tiles meet, 6 dp. */
private val ComposerTileOuter = 16.dp
private val ComposerTileInner = 6.dp

/** A tile's corners by where it sits among the composer's: round only at the panel's own. */
internal fun composerTileShape(top: Boolean, bottom: Boolean, first: Boolean, last: Boolean): Shape = RoundedCornerShape(
    topStart = if (top && first) ComposerTileOuter else ComposerTileInner,
    topEnd = if (top && last) ComposerTileOuter else ComposerTileInner,
    bottomEnd = if (bottom && last) ComposerTileOuter else ComposerTileInner,
    bottomStart = if (bottom && first) ComposerTileOuter else ComposerTileInner,
)

/** One square tile of the composer: an icon button, or whatever [content] is. */
@Composable
internal fun ComposerTile(
    shape: Shape,
    label: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            // gives a little under the finger, as Weave's tiles do
            .then(if (onClick != null) Modifier.pressSink(press) else Modifier)
            .size(ComposerTileSize)
            .clip(shape)
            .background(ComposerTileFill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick)
                        .semantics { if (label != null) contentDescription = label }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "Plan \u00d7": the chat is not in Agent; a tap puts it back. */
@Composable
private fun ModeTag(mode: HermesMode, onClear: () -> Unit, shape: Shape = RoundedCornerShape(999.dp)) {
    val haptics = rememberHaptics()
    val tint = modeColor(mode.kind)
    Row(
        modifier = Modifier
            .height(ComposerTileSize)
            .clip(shape)
            .background(tint.copy(alpha = 0.16f))
            .clickable { haptics.tick(); onClear() }
            .semantics { contentDescription = "${mode.label} mode, tap for Agent" }
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(modeIcon(mode.kind), null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(mode.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.Close, null, tint = tint, modifier = Modifier.size(11.dp))
    }
}

@Composable
internal fun AttachmentChip(attachment: HermesAttachment, onRemove: (() -> Unit)? = null) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AiRoom)
            .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (attachment.isImage) AppIcons.Images else AppIcons.NotepadText, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            attachment.name,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        if (onRemove != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                AppIcons.Close,
                contentDescription = "Remove ${attachment.name}",
                tint = TextTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = { haptics.tick(); onRemove() })
                    .padding(3.dp),
            )
        }
    }
}

// ── Slash palette ───────────────────────────────────────────────────────────

/** One entry in the palette: a command of the page's own, or one Hermes answers itself. */
internal data class SlashEntry(val name: String, val args: String, val desc: String, val group: String, val local: Boolean)

/** The modes, by kind: each brain's own Agent, Plan and Ask (HermesViewModel.setModeKind). */
internal val ModeCommands = mapOf("agent" to "write", "plan" to "read", "ask" to "ask")

internal val LocalSlash = listOf(
    SlashEntry("agent", "", "Make changes on its own (the default)", "Mode", true),
    SlashEntry("plan", "", "Read and plan; describe changes, don't make them", "Mode", true),
    SlashEntry("ask", "", "Ask before changing anything", "Mode", true),
    SlashEntry("new", "", "Start a new chat", "This app", true),
    SlashEntry("model", "", "Pick the model and its depth", "This app", true),
    SlashEntry("title", "name", "Rename this chat", "This app", true),
    SlashEntry("clear", "", "Empty this chat; Hermes forgets it too", "This app", true),
    SlashEntry("stop", "", "Stop the answer in progress", "This app", true),
)

internal fun slashEntries(commands: List<HermesCommand>, typed: String): List<SlashEntry> {
    val q = typed.removePrefix("/").lowercase()
    // skills are not here: `$` lists them (skillEntries), as in T3 Code
    val all = LocalSlash + commands.filter { it.group != "Skills" }.map { SlashEntry(it.name, it.args, it.desc, it.group, false) }
        .filter { c -> LocalSlash.none { it.name == c.name } }
    return all.filter { q.isEmpty() || it.name.startsWith(q) }
        .ifEmpty { all.filter { q in it.name || q in it.desc.lowercase() } }
        .take(40)
}

/** The skills a `$` has been typed for: those that start with what follows it, then those that mention it, the fleet's first. */
internal fun skillEntries(skills: List<com.macrotracker.data.hermes.HermesSkill>, query: String): List<com.macrotracker.data.hermes.HermesSkill> {
    val q = query.lowercase()
    if (q.isEmpty()) return skills
    return skills.filter { it.name.startsWith(q) } + skills.filter { !it.name.startsWith(q) && (q in it.name || q in it.desc.lowercase()) }
}

/** A message that names a skill with `$` becomes Hermes' own command for it (`/ship fix it`); a long one asks for the skill in words. */
internal fun skillCommand(text: String, skills: List<com.macrotracker.data.hermes.HermesSkill>): String {
    val known = skills.mapTo(HashSet()) { it.name }
    val hit = Regex("(^|\\s)\\$([\\w-]+)").findAll(text).firstOrNull { it.groupValues[2] in known } ?: return text
    val name = hit.groupValues[2]
    val start = hit.range.first + hit.groupValues[1].length
    val rest = text.removeRange(start, hit.range.last + 1)
    return if ('\n' in text) "Use your \"$name\" skill (the /$name command) for this:\n\n${rest.trim()}" else "/$name ${rest.replace(Regex("\\s+"), " ").trim()}".trim()
}

@Composable
internal fun SkillPalette(entries: List<com.macrotracker.data.hermes.HermesSkill>, onPick: (com.macrotracker.data.hermes.HermesSkill) -> Unit) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    if (entries.isEmpty()) return
    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AiRoom),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        var group: Boolean? = null
        entries.forEachIndexed { i, e ->
            if (e.fleet != group) {
                group = e.fleet
                item(key = "g-$i") {
                    Text(
                        if (e.fleet) "Your fleet" else "Hermes",
                        color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = HealthDisplay,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
            }
            item(key = "s-${e.name}") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { haptics.tick(); onPick(e) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$" + e.name, color = AiAccent, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text(e.desc, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SlashPalette(entries: List<SlashEntry>, onPick: (SlashEntry) -> Unit) {
    val haptics = com.macrotracker.ui.util.rememberHaptics()
    if (entries.isEmpty()) return
    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AiRoom),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        var group: String? = null
        // Indexed keys: a group or command that repeats can't crash the list with a duplicate key.
        entries.forEachIndexed { i, e ->
            if (e.group != group) {
                group = e.group
                val heading = e.group
                item(key = "g-$i-$heading") {
                    Text(
                        heading,
        style = HealthDisplay,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
            }
            item(key = "c-$i-${e.local}-${e.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptics.tick(); onPick(e) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("/${e.name}", color = AiAccent, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    if (e.args.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(e.args, color = TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(e.desc, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Rename ──────────────────────────────────────────────────────────────────

@Composable
internal fun RenameThreadDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AiSheet,
        title = { Text("Rename chat", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                singleLine = true,
                colors = roomFieldColors(), shape = FieldShape)
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }, enabled = text.isNotBlank()) {
                Text("Rename", color = if (text.isNotBlank()) AiAccent else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

/** Deleting cannot be undone, and clearing makes Hermes forget; both ask first. */
@Composable
internal fun ConfirmThreadDialog(
    title: String,
    body: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AiSheet,
        title = { Text(title, color = TextPrimary) },
        text = { Text(body, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/** The key came from a real keyboard (a phone's own sends its Enter as text, with a virtual device). */
internal fun KeyEvent.isPhysicalKeyboard(): Boolean {
    val device = android.view.InputDevice.getDevice(nativeKeyEvent.deviceId) ?: return false
    return !device.isVirtual && device.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
}

/**
 * Send, as T3 Code's composer has it. Idle it is an arrow. While Hermes works and something is typed it says what it will do,
 * Steer (stop the turn and say it now) or Queue (after the turn), whichever is chosen, and a hold offers the other; with
 * nothing typed it is Stop. One light-on-dark shape for all of them, so the state is the icon and the word, not a colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SendControl(
    busy: Boolean,
    hasText: Boolean,
    canSend: Boolean,
    followUp: String,
    onSend: () -> Unit,
    onAlternate: () -> Unit,
    onStop: () -> Unit,
    shape: Shape = RoundedCornerShape(50),
) {
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    val mode = when {
        busy && hasText -> if (followUp == "steer") SendMode.STEER else SendMode.QUEUE
        busy -> SendMode.STOP
        else -> SendMode.SEND
    }
    val lit = mode == SendMode.STOP || canSend
    val bg by animateColorAsState(if (lit) AiAccent else ComposerTileFill, MacroMotion.colorTween(), label = "sendBg")
    val fg = if (lit) Color(0xFF1A120C) else TextTertiary
    val label = when (mode) { SendMode.STEER -> "Steer"; SendMode.QUEUE -> "Queue"; else -> null }
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box {
        Row(
            modifier = Modifier
                .pressSink(press)
                .height(ComposerTileSize)
                .widthIn(min = ComposerTileSize)
                .clip(shape)
                .background(bg)
                .animateContentSize(MacroMotion.entranceSpring())
                .combinedClickable(
                    interactionSource = press,
                    indication = androidx.compose.material3.ripple(),
                    enabled = lit,
                    onClick = {
                        when (mode) {
                            SendMode.STOP -> { haptics.tick(); onStop() }
                            SendMode.STEER -> { haptics.confirm(); onSend() }
                            SendMode.QUEUE -> { haptics.tick(); onSend() }
                            SendMode.SEND -> { haptics.click(); onSend() }
                        }
                    },
                    onLongClick = if (busy && hasText) ({ haptics.gestureStart(); menu = true }) else null,
                    onLongClickLabel = "Choose how to send",
                )
                .padding(horizontal = if (label != null) 15.dp else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                SendMode.STOP -> Box(Modifier.size(13.dp).background(fg, RoundedCornerShape(3.dp)))
                SendMode.SEND -> Icon(AppIcons.ArrowUp, "Send", tint = fg, modifier = Modifier.size(20.dp))
                SendMode.STEER -> Icon(AppIcons.Bolt, null, tint = fg, modifier = Modifier.size(16.dp))
                SendMode.QUEUE -> Icon(AppIcons.ListChecks, null, tint = fg, modifier = Modifier.size(16.dp))
            }
            if (label != null) {
                Spacer(Modifier.width(7.dp))
                Text(label, style = HealthDisplay, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        TileMenu(expanded = menu, onDismissRequest = { menu = false }, color = AiMenu, minWidth = 264.dp) {
            TileMenuGroup {
                listOf(SendMode.STEER, SendMode.QUEUE).forEach { m ->
                    val current = m == mode
                    TileMenuItem(
                        label = if (m == SendMode.STEER) "Steer now" else "Queue",
                        icon = if (m == SendMode.STEER) AppIcons.Bolt else AppIcons.ListChecks,
                        summary = if (m == SendMode.STEER) "Stop what Hermes is doing and say this now" else "Say this when the turn is done",
                        checked = current,
                    ) {
                        menu = false
                        // the one it already does is the main button; the other is the alternate
                        if (current) onSend() else onAlternate()
                    }
                }
            }
        }
    }
}

private enum class SendMode { SEND, STEER, QUEUE, STOP }
