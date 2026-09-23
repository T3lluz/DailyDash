package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.chat.BotPrompts
import com.macrotracker.data.chat.ChatBot
import com.macrotracker.data.chat.ChatRole
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.WidgetExpandSection
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ChatViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import com.macrotracker.ui.theme.AppIcons

val SysopIdentity = BotIdentity(
    name = "Sysop",
    accent = ServerBrand,
    avatarIcon = AppIcons.Terminal,
    composerHint = "Ask about a server…",
)

/**
 * The tech-support pane. Renders through [ChatKit] exactly as the macros pane does —
 * the only differences are [SysopIdentity], the starter chips, and the fact that this
 * one streams.
 */
@Composable
fun SysopChatPane(
    viewModel: ChatViewModel,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Switches Tech support to Hermes on the dashboard server; null hides the option. */
    onUseHermes: (() -> Unit)? = null,
) {
    val bot = ChatBot.SYSOP
    val state by viewModel.state(bot).collectAsState()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var draft by rememberSaveable { mutableStateOf("") }
    var forceFollow by remember { mutableStateOf(true) }
    var composerHeight by remember { mutableStateOf(0.dp) }
    var threadMenuOpen by remember { mutableStateOf(false) }

    val nearBottom by rememberNearChatBottom(listState)

    // Follow the stream unless the user has deliberately scrolled up to read.
    LaunchedEffect(state.messages.lastOrNull()?.id, state.streaming != null, state.loading) {
        if (state.messages.isEmpty() && state.streaming == null) return@LaunchedEffect
        if (!(forceFollow || nearBottom)) return@LaunchedEffect
        delay(16)
        listState.followChatBottom()
        forceFollow = false
    }

    FollowChatOnKeyboard(listState) { forceFollow || nearBottom }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || state.loading) return
        haptics.click()
        draft = ""
        forceFollow = true
        viewModel.send(bot, body)
    }

    val chatHaze = rememberHazeState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatPaneHeader(
                status = if (state.loading) "Thinking…" else viewModel.modelLabel(),
                active = state.loading,
                accent = ServerBrand,
            ) {
                if (state.loading) {
                    PillButton(
                        icon = AppIcons.Close,
                        label = "Stop",
                        accent = ServerBrand,
                        onClick = { viewModel.cancelStream(bot) },
                    )
                } else {
                    Box {
                        PillButton(
                            icon = AppIcons.History,
                            label = "Threads",
                            accent = ServerBrand,
                            onClick = { threadMenuOpen = true },
                        )
                        DropdownMenu(
                            expanded = threadMenuOpen,
                            onDismissRequest = { threadMenuOpen = false },
                            modifier = Modifier.background(Surface),
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat", color = TextPrimary, fontSize = 14.sp) },
                                onClick = {
                                    threadMenuOpen = false
                                    forceFollow = true
                                    viewModel.newThread(bot)
                                },
                            )
                            if (onUseHermes != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Ask Hermes on your server instead",
                                            color = ServerBrand,
                                            fontSize = 13.sp,
                                        )
                                    },
                                    onClick = {
                                        threadMenuOpen = false
                                        onUseHermes()
                                    },
                                )
                            }
                            state.threads.take(12).forEach { thread ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            thread.title,
                                            color = if (thread.id == state.threadId) ServerBrand else TextPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        threadMenuOpen = false
                                        forceFollow = true
                                        viewModel.selectThread(bot, thread.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .hazeSource(state = chatHaze),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = composerHeight + 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item(key = "greeting") {
                        BotBubble(
                            identity = SysopIdentity,
                            text = BotPrompts.greetingFor(bot),
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    if (message.role == ChatRole.USER) {
                        UserBubble(text = message.text)
                    } else {
                        BotBubble(
                            identity = SysopIdentity,
                            text = message.text,
                            isError = message.isError,
                            actions = {
                                if (message.isError) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        SmallActionChip(
                                            icon = AppIcons.Refresh,
                                            label = "Retry",
                                            onClick = {
                                                haptics.click()
                                                forceFollow = true
                                                viewModel.retry(bot)
                                            },
                                        )
                                        if (message.showSettingsCta) {
                                            SmallActionChip(
                                                icon = AppIcons.Settings,
                                                label = "AI settings",
                                                onClick = {
                                                    haptics.click()
                                                    onNavigateToAiSettings()
                                                },
                                            )
                                        }
                                    }
                                } else if (message.id == state.messages.lastOrNull()?.id && !state.loading) {
                                    Row(modifier = Modifier.padding(top = 6.dp)) {
                                        SmallActionChip(
                                            icon = AppIcons.Refresh,
                                            label = "Regenerate",
                                            onClick = {
                                                haptics.click()
                                                forceFollow = true
                                                viewModel.regenerate(bot)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                state.streaming?.let { partial ->
                    item(key = "streaming") {
                        if (partial.isEmpty()) {
                            TypingBubble(
                                identity = SysopIdentity,
                                label = "Thinking…",
                                onCancel = {
                                    haptics.tick()
                                    viewModel.cancelStream(bot)
                                },
                            )
                        } else {
                            BotBubble(identity = SysopIdentity, text = partial, streaming = true)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Background.copy(alpha = 0.92f),
                        1f to Background,
                    ),
                )
                .onSizeChanged { composerHeight = with(density) { it.height.toDp() } },
        ) {
            WidgetExpandSection(visible = state.messages.isEmpty() && !state.loading) {
                ChatStarters(BotPrompts.startersFor(bot)) { send(it) }
            }
            ChatComposer(
                value = draft,
                onValueChange = { draft = it },
                onSend = { send(draft) },
                enabled = !state.loading,
                hint = BotPrompts.composerHintFor(bot),
                accent = ServerBrand,
                hazeState = chatHaze,
            )
        }
    }
}
