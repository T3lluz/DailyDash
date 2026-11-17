package com.macrotracker.ui.screens.link

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.link.LABEL_MAX
import com.macrotracker.data.link.LinkApp
import com.macrotracker.data.link.LinkBind
import com.macrotracker.data.link.LinkKeys
import com.macrotracker.data.link.LinkSession
import com.macrotracker.data.link.LinkShortcut
import com.macrotracker.data.link.LinkShortcutKind
import com.macrotracker.data.link.LinkState
import com.macrotracker.data.link.ShortcutAnswer
import com.macrotracker.data.link.bindName
import com.macrotracker.data.link.normalizeShortcutUrl
import com.macrotracker.data.link.siteName
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.HealthSection
import com.macrotracker.ui.components.horizontalEdgeFade
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.LinkShortcutTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.LinkViewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.link.LinkShortcutStore
import com.macrotracker.data.link.ShortcutTurn
import com.macrotracker.data.link.describeShortcut
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.components.AiPickerSheet
import com.macrotracker.ui.components.AiPill
import com.macrotracker.ui.components.AiProviderLogo
import com.macrotracker.ui.screens.ai.ShimmerText
import com.macrotracker.ui.util.tookShort
import com.macrotracker.ui.viewmodel.AiSetupViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val Well = Color.White.copy(alpha = 0.06f)
private val WellShape = RoundedCornerShape(18.dp)

/** Things to ask for before anything has been asked: small, everyday, one step each. */
private val STARTERS = listOf("Workspace 2", "Close the window", "Open youtube.com", "Take a screenshot", "Open a terminal", "Next song")

/** One ask in the sheet: what was said, and what came back (null while the AI works on it). */
internal data class AskTurn(
    val ask: String,
    val provider: AiProvider?,
    val reply: String? = null,
    val made: Boolean = false,
    val stopped: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val at: Long = System.currentTimeMillis(),
)

/**
 * What the sheet holds, kept by the view-model rather than the sheet: setting up an AI from the
 * picker leaves the remote for Settings, and the sheet comes back as it was left.
 */
@Stable
internal class ShortcutSheetState(val initial: LinkShortcut?, canList: Boolean) {
    var draft by mutableStateOf(initial ?: LinkShortcut(kind = if (canList) LinkShortcutKind.APP else LinkShortcutKind.SITE, label = ""))
    // a name typed by hand is kept; until then each pick names the shortcut
    var named by mutableStateOf(initial != null)
    var urlTyped by mutableStateOf(initial?.url?.removePrefix("https://").orEmpty())
    // the binding picked, by what it does: two of the laptop's can share the same keys
    var pickedBind by mutableStateOf<String?>(null)
    // bumps with each AI answer, so the pickers start from what it made
    var answered by mutableIntStateOf(0)
    var ask by mutableStateOf("")
    val turns = mutableStateListOf<AskTurn>()
}

/**
 * The sheet holds still under a scroll. What the page can't scroll any further is dropped here
 * instead of handed to the sheet, which would drag down and spring back at the top, or overshoot
 * on its bouncy spring after a fling at the bottom. The sheet still closes from its handle and
 * title, a tap above it, or back.
 */
private object KeepSheetStill : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(0f, available.y)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(0f, available.y)
}

/**
 * Making a shortcut, or changing one. At the top, the shortcut as it will stand in the row, its
 * name to change in place and what it does in words. Under it, ask the AI (it reads the laptop's
 * own apps and bindings, says what it is doing as it works and what it made, and takes a
 * follow-up: "make it workspace 4"), or pick it by hand: one of the laptop's apps, a web page, a
 * key or words to type. Try it runs it on the laptop before it is kept.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LinkShortcutSheet(
    viewModel: LinkViewModel,
    state: LinkState,
    laptopName: String,
    sheet: ShortcutSheetState,
    onDismiss: () -> Unit,
    onAiSettings: () -> Unit,
    aiSetup: AiSetupViewModel = hiltViewModel(),
) {
    val session = viewModel.session
    val catalog by session.catalog.collectAsState()
    val ai by aiSetup.state.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val canList = LinkSession.CATALOG in state.caps
    LaunchedEffect(Unit) { session.requestCatalog() }

    val initial = sheet.initial
    var draft by sheet::draft
    var named by sheet::named
    var urlTyped by sheet::urlTyped
    var pickedBind by sheet::pickedBind
    var answered by sheet::answered
    var ask by sheet::ask
    val turns = sheet.turns
    val fx = remember { TileFx() }

    var askJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { MutableStateFlow<String?>(null) }
    val line by progress.collectAsState()
    var pickingAi by remember { mutableStateOf(false) }
    val aiReady = ai.current?.ready ?: viewModel.aiReady()
    // Try it needs only what the shortcut does, not its name
    val doable = draft.copy(label = draft.label.ifBlank { "-" }).complete

    fun lightUp() {
        scope.launch { fx.flash.snapTo(1f); fx.flash.animateTo(0f, tween(MacroMotion.Link.FLASH_MS)) }
        scope.launch { fx.hop.snapTo(0.78f); fx.hop.animateTo(1f, MacroMotion.Link.press()) }
    }

    /** The name a pick gives a shortcut: the app's, the site's, the binding's, the words' first line. */
    fun autoName(x: LinkShortcut): String = when (x.kind) {
        LinkShortcutKind.APP -> catalog?.apps?.firstOrNull { it.id == x.app }?.name.orEmpty()
        LinkShortcutKind.SITE -> if (x.url.isEmpty()) "" else siteName(x.url)
        LinkShortcutKind.KEYS -> if (x.key.isBlank()) "" else (pickedBind ?: bindFor(catalog?.binds, x)?.label)?.let(::bindName) ?: LinkKeys.combo(x.mods, x.cap)
        LinkShortcutKind.TEXT -> x.text.lineSequence().firstOrNull().orEmpty()
    }.take(LABEL_MAX)

    /** Whether the name still follows the picks: nothing typed for it, or still the name the last pick gave. */
    fun follows() = !named || draft.label.isBlank() || draft.label.equals(autoName(draft), ignoreCase = true)

    fun askNow(text: String) {
        val q = text.trim()
        if (q.isEmpty() || askJob != null) return
        if (!aiReady) {
            pickingAi = true
            return
        }
        ask = ""
        val earlier = turns.filter { it.reply != null && !it.stopped }.map { ShortcutTurn(it.ask, it.reply.orEmpty()) }
        turns.add(AskTurn(q, ai.current?.provider))
        val i = turns.lastIndex
        progress.value = null
        askJob = scope.launch {
            try {
                when (val answer = viewModel.askShortcut(q, laptopName, draft.takeIf { it.complete }, earlier) { progress.value = it }) {
                    is ShortcutAnswer.Made -> {
                        haptics.click()
                        // the AI's own pick lights up, not one made by hand before it
                        pickedBind = null
                        // a name typed here before there was anything to send is kept; the AI never saw it
                        val typedName = draft.label.takeIf { named && it.isNotBlank() && !draft.complete }
                        val made = answer.shortcut.copy(id = draft.id, label = typedName ?: answer.shortcut.label)
                        // a name the pick would give anyway ("Workspace 3") follows the next pick; one of its own
                        // ("Desk 3", asked for, or typed here) is kept like a typed one
                        named = typedName != null || !made.label.equals(autoName(made), ignoreCase = true)
                        draft = made
                        if (made.kind == LinkShortcutKind.SITE) urlTyped = made.url.removePrefix("https://")
                        answered++
                        lightUp()
                        val said = answer.reply.ifBlank {
                            describeShortcut(made, laptopName, catalog?.apps?.firstOrNull { it.id == made.app }?.name)?.plus(".").orEmpty()
                        }
                        turns[i] = turns[i].copy(reply = said, made = true)
                    }
                    is ShortcutAnswer.Refused -> {
                        haptics.tick()
                        turns[i] = turns[i].copy(reply = answer.reason, suggestions = answer.suggestions)
                    }
                }
            } finally {
                // stopped, or the sheet closed: the turn says so and isn't told to the AI again
                if (turns.getOrNull(i)?.reply == null) turns[i] = turns[i].copy(reply = "Stopped.", stopped = true)
                progress.value = null
                askJob = null
            }
        }
    }

    fun pick(next: LinkShortcut, name: String, follow: Boolean = follows()) {
        draft = if (follow) next.copy(label = name.take(LABEL_MAX)) else next
    }

    // a site's own picture and name while its address is typed, once the typing stops
    LaunchedEffect(draft.kind, draft.url) {
        if (draft.kind != LinkShortcutKind.SITE || draft.url.isEmpty() || draft.url == initial?.url) return@LaunchedEffect
        delay(650)
        val own = viewModel.previewSite(draft.url) ?: return@LaunchedEffect
        // "Github" becomes "GitHub", the name the site gives itself, only while the name is the
        // address's: one typed here or given by the AI ("Notifications") stays
        if (draft.kind == LinkShortcutKind.SITE && !named && (draft.label.isBlank() || draft.label.equals(siteName(draft.url), ignoreCase = true))) {
            draft = draft.copy(label = own.take(LABEL_MAX))
            named = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    /** Slides the sheet down, then lets it go. */
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial == null) "New shortcut" else "Change shortcut", style = HealthDisplay, fontSize = 28.sp, color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (initial != null) {
                    Box(
                        Modifier.pressable("Remove this shortcut") { viewModel.removeShortcut(initial.id); close() }
                            .size(42.dp).clip(CircleShape).background(Well),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Delete, "Remove this shortcut", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).nestedScroll(KeepSheetStill).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
                Hero(
                    s = draft, apps = catalog?.apps, binds = catalog?.binds, store = viewModel.shortcutStore, laptopName = laptopName, fx = fx,
                    onName = { draft = draft.copy(label = it.take(LABEL_MAX)); named = it.isNotBlank() },
                )

                // say it
                HealthSection(tone = LinkShortcutTone) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoomTitle(AppIcons.Sparkles, "Ask AI", LinkShortcutTone, Modifier.weight(1f).padding(top = 6.dp))
                        if (ai.providers.isNotEmpty()) {
                            AiPill(ai.current?.takeIf { it.ready }, onClick = { pickingAi = true }, open = pickingAi)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (turns.isEmpty()) {
                        if (initial != null) {
                            // a shortcut that already does something is changed with words, not started over
                            Spacer(Modifier.height(2.dp))
                        } else if (!aiReady) {
                            Text("Pick an AI to make shortcuts from a few words.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                        } else {
                            val chips = rememberScrollState()
                            Row(
                                Modifier.fillMaxWidth().horizontalEdgeFade(chips).horizontalScroll(chips).padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                STARTERS.forEach { t -> Chip(t, false) { askNow(t) } }
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth().animateContentSize(MacroMotion.Link.panel()).padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // the last few: the sheet is for one shortcut, not a long chat
                            turns.takeLast(3).forEach { t -> key(t.at, t.ask) { Turn(t, line) { askNow(it) } } }
                        }
                    }
                    // while it has the keyboard, the ask field stays in view as turns grow above it
                    val askView = remember { BringIntoViewRequester() }
                    var askFocused by remember { mutableStateOf(false) }
                    LaunchedEffect(turns.size, turns.lastOrNull()?.reply, askFocused) {
                        if (askFocused && turns.isNotEmpty()) {
                            // the turns grow with a spring: follow them until it has settled
                            repeat(4) {
                                delay(180)
                                askView.bringIntoView()
                            }
                        }
                    }
                    Row(
                        Modifier.bringIntoViewRequester(askView).onFocusChanged { askFocused = it.hasFocus },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkField(
                            ask, { ask = it },
                            if (initial != null || turns.any { it.made }) "Change it: \"make it 4\"" else "\"Workspace 3\", \"open youtube.com\"",
                            modifier = Modifier.weight(1f), imeAction = ImeAction.Send,
                            onIme = { askNow(ask) },
                        )
                        Spacer(Modifier.width(8.dp))
                        AskButton(working = askJob != null, ready = ask.isNotBlank()) {
                            if (askJob != null) askJob?.cancel() else askNow(ask)
                        }
                    }
                }

                // or pick it
                HealthSection(tone = LinkShortcutTone) {
                    RoomTitle(AppIcons.Bolt, "Or pick it", LinkShortcutTone)
                    Segmented(
                        listOf(LinkShortcutKind.APP to "App", LinkShortcutKind.SITE to "Site", LinkShortcutKind.KEYS to "Keys", LinkShortcutKind.TEXT to "Text"),
                        picked = draft.kind, tone = LinkShortcutTone,
                    ) { kind ->
                        val next = draft.copy(kind = kind, url = if (kind == LinkShortcutKind.SITE) normalizeShortcutUrl(urlTyped).orEmpty() else draft.url)
                        draft = if (follows()) next.copy(label = autoName(next)) else next
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(
                        targetState = draft.kind,
                        transitionSpec = { fadeIn(MacroMotion.fadeTween()) togetherWith fadeOut(MacroMotion.fadeTween()) },
                        label = "kind",
                    ) { kind ->
                        key(answered) { when (kind) {
                            LinkShortcutKind.APP -> AppPicker(
                                apps = catalog?.apps, canList = canList, laptopName = laptopName, picked = draft.app,
                            ) { app -> pick(draft.copy(app = app.id), app.name) }
                            LinkShortcutKind.SITE -> Column {
                                LinkField(
                                    urlTyped,
                                    { typed ->
                                        urlTyped = typed
                                        val url = normalizeShortcutUrl(typed).orEmpty()
                                        // the name the site gave itself ("YouTube", "Google Maps") stays while the address
                                        // stays on that site; a new site starts from its address
                                        val sameSite = !named && draft.label.isNotBlank() && hostOf(draft.url) != null && hostOf(draft.url) == hostOf(url)
                                        val name = when {
                                            url.isEmpty() -> ""
                                            sameSite -> draft.label
                                            else -> siteName(url).let { n -> if (n.equals(draft.label, ignoreCase = true)) draft.label else n }.take(LABEL_MAX)
                                        }
                                        draft = if (follows()) draft.copy(url = url, label = name) else draft.copy(url = url)
                                    },
                                    "youtube.com", keyboardType = KeyboardType.Uri, leading = AppIcons.Globe,
                                )
                                if (urlTyped.isNotBlank() && draft.url.isEmpty()) {
                                    Text("That isn't a web address.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                                }
                            }
                            LinkShortcutKind.KEYS -> KeysPicker(catalog?.binds, laptopName, draft, pickedBind) { next, name, bind ->
                                // asked before the binding changes: the old name is the old binding's
                                val follow = follows()
                                pickedBind = bind
                                pick(next, name, follow)
                            }
                            LinkShortcutKind.TEXT -> LinkField(
                                draft.text,
                                { typed ->
                                    // one line, as the AI's words are: a line break is Enter, and in a terminal it runs what came before it
                                    val t = typed.replace("\r", "").replace('\n', ' ')
                                    draft = if (follows()) draft.copy(text = t, label = t.take(LABEL_MAX)) else draft.copy(text = t)
                                },
                                "Words to type on $laptopName",
                            )
                        } }
                    }
                }
            }
            // the foot stays put: run it on the laptop to see, then keep it
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 12.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinkGhostButton("Try it", AppIcons.Play, LinkShortcutTone, enabled = doable) {
                    session.run(draft)
                    lightUp()
                }
                LinkPrimaryButton(if (initial == null) "Add" else "Save", draft.complete, Modifier.weight(1f)) {
                    viewModel.saveShortcut(draft)
                    close()
                }
            }
        }
    }

    if (pickingAi) {
        AiPickerSheet(viewModel = aiSetup, onDismiss = { pickingAi = false }, onManage = onAiSettings, note = "Makes shortcuts from a few words")
    }
}

/**
 * The shortcut as it will stand in the row, a size up, with its name to change in place and
 * what it does in words (keys as keycaps). A new face springs in; it lights up when tried.
 */
@Composable
private fun Hero(
    s: LinkShortcut,
    apps: List<LinkApp>?,
    binds: List<LinkBind>?,
    store: LinkShortcutStore,
    laptopName: String,
    fx: TileFx,
    onName: (String) -> Unit,
) {
    val icons by store.icons.collectAsState()
    fun appOf(x: LinkShortcut) = if (x.kind == LinkShortcutKind.APP) apps?.firstOrNull { it.id == x.app } else null
    HealthSection(tone = LinkShortcutTone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(SHORTCUT_W * 1.2f, SHORTCUT_H * 1.2f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = s,
                    // a site's face changes in place as its address is typed (its icon lands when fetched)
                    contentKey = { listOf(it.kind, it.app, it.key, it.mods) },
                    transitionSpec = {
                        (fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.Link.press(), initialScale = 0.82f)) togetherWith
                            (fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.9f))
                    },
                    label = "face",
                ) { shown ->
                    // each face its own picture: the one leaving keeps the icon it had
                    val bitmap = appOf(shown)?.bitmap
                    val picture = remember(bitmap) { bitmap?.asImageBitmap() }
                    Box(Modifier.size(SHORTCUT_W, SHORTCUT_H).graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }) {
                        ShortcutTile(shown, store, icons, fx, picture = picture)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                NameField(s.label, onName)
                Spacer(Modifier.height(6.dp))
                WhatItDoes(s, appOf(s)?.name, binds, laptopName)
            }
        }
    }
}

/** The shortcut's name, typed straight into the card in the display face. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Name it", style = HealthDisplay, fontSize = 22.sp, color = TextTertiary, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = HealthDisplay.copy(fontSize = 22.sp, color = TextPrimary),
                cursorBrush = SolidColor(LinkShortcutTone),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name" },
            )
        }
        Icon(AppIcons.Edit, null, tint = TextTertiary, modifier = Modifier.padding(start = 6.dp).size(15.dp))
    }
}

/** "Opens Spotify on t3lluz"; keys as their keycaps, with the laptop's own words for them when it has some. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatItDoes(s: LinkShortcut, appName: String?, binds: List<LinkBind>?, laptopName: String) {
    if (s.kind == LinkShortcutKind.KEYS && s.key.isNotBlank()) {
        // wraps on a narrow phone: four modifiers and a key are wider than the card there
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Presses", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
            s.mods.forEach { Keycap(LinkKeys.modCap(it), TextSecondary, Modifier.align(Alignment.CenterVertically)) }
            Keycap(s.cap.ifBlank { LinkKeys.cap(s.key) }, LinkShortcutTone, Modifier.align(Alignment.CenterVertically))
        }
        bindFor(binds, s)?.takeIf { !it.label.equals(s.label.trim(), ignoreCase = true) }?.let { b ->
            Text(b.label, color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    } else {
        val said = describeShortcut(s, laptopName, appName)
        Text(
            said ?: "Ask the AI, or pick it below",
            color = if (said == null) TextTertiary else TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One ask and the AI's answer: what it is doing while it works, then what it made or why not. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Turn(t: AskTurn, line: String?, onSuggest: (String) -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, MacroMotion.Link.panel()) }
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * 10.dp.toPx()
        },
    ) {
        Text(
            t.ask, color = TextPrimary, fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 5.dp, bottomStart = 16.dp))
                .background(Well)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 1.dp)) {
                if (t.provider != null) AiProviderLogo(t.provider, 18.dp) else Icon(AppIcons.Sparkles, null, tint = LinkShortcutTone, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                targetState = t.reply,
                contentKey = { it == null },
                transitionSpec = { fadeIn(MacroMotion.fadeTween()) togetherWith fadeOut(MacroMotion.fadeTween()) },
                label = "reply",
                // one live node through working and answered, its words merged, so a screen reader
                // hears the step and then the reply (the clock and the chips keep out of it)
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) { reply ->
                if (reply == null) {
                    Working(line ?: "Thinking", t.at)
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            if (t.made) Icon(AppIcons.Check, null, tint = LinkShortcutTone, modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(15.dp))
                            Text(reply, color = if (t.made) TextPrimary else TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
                        }
                        if (t.suggestions.isNotEmpty()) {
                            FlowRow(
                                Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                t.suggestions.forEach { idea -> Chip(idea, false, small = true) { onSuggest(idea) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the AI is on now, shimmering, and how long it has been at it. */
@Composable
private fun Working(line: String, since: Long) {
    val took by produceState(System.currentTimeMillis() - since, since) {
        while (true) {
            value = System.currentTimeMillis() - since
            delay(1_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                (fadeIn(MacroMotion.fadeTween()) + slideInVertically(MacroMotion.slideTween()) { it / 2 }) togetherWith
                    (fadeOut(MacroMotion.fadeTween()) + slideOutVertically(MacroMotion.slideTween()) { -it / 2 })
            },
            label = "line",
            modifier = Modifier.weight(1f, fill = false),
        ) { l -> ShimmerText(l, TextSecondary, 14.sp, fontWeight = FontWeight.Medium) }
        if (took >= 1_000) {
            Spacer(Modifier.width(8.dp))
            // a screen reader hears the step, not every second of the clock
            Text(tookShort(took), color = TextTertiary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** Send, or Stop while the AI works. */
@Composable
private fun AskButton(working: Boolean, ready: Boolean, onClick: () -> Unit) {
    val lit = working || ready
    val fill = animateColorAsState(if (lit) LinkShortcutTone else Well, MacroMotion.colorTween(), label = "ask")
    Box(
        Modifier
            .pressable(if (working) "Stop" else "Ask", enabled = lit, onClick = onClick)
            .size(50.dp)
            .clip(CircleShape)
            .drawBehind { drawRect(fill.value) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            working,
            transitionSpec = {
                (fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.Link.press(), initialScale = 0.6f)) togetherWith
                    (fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.6f))
            },
            label = "askIcon",
        ) { w ->
            if (w) {
                Icon(AppIcons.Stop, "Stop", tint = Color.Black, modifier = Modifier.size(16.dp))
            } else {
                Icon(AppIcons.ArrowUp, "Ask", tint = if (ready) Color.Black else TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The laptop's apps as its launcher has them, to search and pick one. */
@Composable
private fun AppPicker(apps: List<LinkApp>?, canList: Boolean, laptopName: String, picked: String, onPick: (LinkApp) -> Unit) {
    var find by remember { mutableStateOf("") }
    Column {
        when {
            !canList -> Text("Update tandem on $laptopName to pick from its apps.", color = TextSecondary, fontSize = 14.sp)
            apps == null -> Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = LinkShortcutTone, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Reading $laptopName's apps", color = TextSecondary, fontSize = 14.sp)
            }
            apps.isEmpty() -> Text("Couldn't read $laptopName's apps.", color = TextSecondary, fontSize = 14.sp)
            else -> {
                LinkField(find, { find = it }, "Find an app", leading = AppIcons.Search)
                Spacer(Modifier.height(10.dp))
                val shown = remember(apps, find) {
                    val q = find.trim()
                    if (q.isEmpty()) apps else apps.filter { it.name.contains(q, ignoreCase = true) || it.id.contains(q, ignoreCase = true) }
                }
                if (shown.isEmpty()) {
                    Text("No app called that", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(shown, key = { it.id }) { app -> AppCell(app, app.id == picked) { onPick(app) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCell(app: LinkApp, picked: Boolean, onClick: () -> Unit) {
    val fill = animateColorAsState(if (picked) LinkShortcutTone.copy(alpha = 0.24f) else Color.Transparent, MacroMotion.colorTween(), label = "app")
    val picture = remember(app) { app.bitmap?.asImageBitmap() }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).drawBehind { drawRect(fill.value) }.pressable(app.name, onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (picture != null) {
            Image(picture, null, Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Well), contentAlignment = Alignment.Center) {
                Text(app.name.take(1).uppercase(), style = HealthDisplay, fontSize = 16.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(app.name, color = if (picked) TextPrimary else TextSecondary, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A web address's host, or null for anything that isn't one. */
private fun hostOf(url: String): String? = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()

/** The laptop's binding that [s] presses, if it is one. */
private fun bindFor(binds: List<LinkBind>?, s: LinkShortcut): LinkBind? =
    binds?.firstOrNull { it.key == s.key && it.mods.toSet() == s.mods.toSet() }

/**
 * A key to press: one of the laptop's own bindings, found by what it does, or one put together
 * here from the modifiers and a key.
 */
@Composable
private fun KeysPicker(
    binds: List<LinkBind>?,
    laptopName: String,
    draft: LinkShortcut,
    pickedBind: String?,
    onPick: (next: LinkShortcut, name: String, bind: String?) -> Unit,
) {
    val has = !binds.isNullOrEmpty()
    // a key that isn't one of the laptop's bindings opens on the builder; worked out again once the bindings are here
    var own by remember(binds == null) { mutableStateOf(!has || (draft.key.isNotBlank() && bindFor(binds, draft) == null)) }
    Column {
        if (has) {
            Segmented(listOf(false to "$laptopName's", true to "Your own"), picked = own, tone = LinkShortcutTone) { own = it }
            Spacer(Modifier.height(12.dp))
        }
        if (!own && binds != null) {
            var find by remember { mutableStateOf("") }
            LinkField(find, { find = it }, "Find: workspace, close, screenshot", leading = AppIcons.Search)
            Spacer(Modifier.height(6.dp))
            val shown = remember(binds, find) {
                val words = find.trim().split(' ').filter { it.isNotBlank() }
                if (words.isEmpty()) binds else binds.filter { b -> words.all { w -> b.label.contains(w, ignoreCase = true) } }
            }
            // opens at the binding the shortcut presses, with one above it to show there are more
            val at = remember(binds) {
                binds.indexOfFirst { b ->
                    draft.kind == LinkShortcutKind.KEYS && draft.key == b.key && draft.mods.toSet() == b.mods.toSet() && (pickedBind == null || pickedBind == b.label)
                }
            }
            val list = rememberLazyListState(initialFirstVisibleItemIndex = (at - 1).coerceAtLeast(0))
            LazyColumn(Modifier.fillMaxWidth().height(320.dp), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
                items(shown, key = { "${it.label}|${it.key}|${it.mods}" }) { b ->
                    val sameKeys = draft.kind == LinkShortcutKind.KEYS && draft.key == b.key && draft.mods.toSet() == b.mods.toSet()
                    val picked = sameKeys && (pickedBind == null && bindFor(binds, draft) == b || pickedBind == b.label)
                    BindRow(b, picked) { onPick(draft.copy(key = b.key, mods = b.mods, cap = b.cap), bindName(b.label), b.label) }
                }
            }
        } else {
            ComboBuilder(draft) { mods, key ->
                val cap = LinkKeys.cap(key)
                onPick(draft.copy(key = key, mods = mods, cap = cap), LinkKeys.combo(mods, cap), null)
            }
        }
    }
}

@Composable
private fun BindRow(b: LinkBind, picked: Boolean, onClick: () -> Unit) {
    val fill = animateColorAsState(if (picked) LinkShortcutTone.copy(alpha = 0.2f) else Color.Transparent, MacroMotion.colorTween(), label = "bind")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).drawBehind { drawRect(fill.value) }.pressable(b.label, onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(b.label, color = TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            b.mods.forEach { Keycap(LinkKeys.modCap(it), TextSecondary) }
            Keycap(b.cap, if (picked) LinkShortcutTone else TextPrimary)
        }
    }
}

/** Modifiers to hold and a key to press with them. */
@Composable
private fun ComboBuilder(draft: LinkShortcut, onChange: (List<String>, String) -> Unit) {
    // follows the shortcut when it changes from outside (an AI answer, a binding picked)
    var mods by remember(draft.key, draft.mods) { mutableStateOf(draft.mods) }
    var key by remember(draft.key) { mutableStateOf(draft.key.takeIf { LinkKeys.pressable(it) }.orEmpty()) }
    var typed by remember(draft.key) { mutableStateOf(key.takeIf { it.length == 1 }?.uppercase().orEmpty()) }
    fun changed() { if (key.isNotEmpty()) onChange(LinkKeys.ordered(mods), key) }
    Column {
        Text("Hold", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LinkKeys.MODS.forEach { m ->
                Chip(LinkKeys.modCap(m), m in mods, Modifier.weight(1f)) {
                    mods = if (m in mods) mods - m else mods + m
                    changed()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Then press", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // a letter or a digit, typed
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(if (key.length == 1) LinkShortcutTone.copy(alpha = 0.28f) else Well),
                contentAlignment = Alignment.Center,
            ) {
                if (typed.isEmpty()) Text("A", style = HealthDisplay, fontSize = 20.sp, color = TextTertiary)
                BasicTextField(
                    value = typed,
                    onValueChange = { t ->
                        val k = LinkKeys.fromTyped(t)
                        if (k != null) {
                            key = k
                            typed = k.uppercase()
                            changed()
                        } else if (t.isEmpty()) {
                            typed = ""
                        }
                    },
                    singleLine = true,
                    textStyle = HealthDisplay.copy(fontSize = 20.sp, color = TextPrimary, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(LinkShortcutTone),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    modifier = Modifier.width(40.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            val chips = rememberScrollState()
            Row(
                Modifier.weight(1f).horizontalEdgeFade(chips).horizontalScroll(chips),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinkKeys.SPECIAL.forEach { k ->
                    Chip(LinkKeys.cap(k), key == k) {
                        key = k
                        typed = ""
                        changed()
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val fill = animateColorAsState(if (on) LinkShortcutTone.copy(alpha = 0.3f) else Well, MacroMotion.colorTween(), label = "chip")
    Box(
        modifier.pressable(text, onClick).height(if (small) 36.dp else 44.dp).clip(RoundedCornerShape(14.dp)).drawBehind { drawRect(fill.value) }.padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) TextPrimary else TextSecondary, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** A line to type into, on a quiet tile: the hint in grey until something is there. */
@Composable
internal fun LinkField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Done,
    /** What the keyboard's action key does; without one it does what it always does (Done hides the keyboard). */
    onIme: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(WellShape).background(Well).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = TextTertiary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 5,
                textStyle = TextStyle(color = if (enabled) TextPrimary else TextSecondary, fontSize = 16.sp),
                cursorBrush = SolidColor(LinkShortcutTone),
                keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = keyboardType == KeyboardType.Text),
                keyboardActions = onIme?.let { act -> KeyboardActions(onAny = { act() }) } ?: KeyboardActions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The one thing the sheet is for: a full-width yellow button, its words in the display face. */
@Composable
private fun LinkPrimaryButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fill = animateColorAsState(if (enabled) LinkShortcutTone else Well, MacroMotion.colorTween(), label = "primary")
    Box(
        modifier.pressable(text, enabled = enabled, onClick = onClick).height(54.dp).clip(WellShape).drawBehind { drawRect(fill.value) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HealthDisplay, fontSize = 17.sp, color = if (enabled) Color.Black else TextTertiary)
    }
}

@Composable
private fun LinkGhostButton(text: String, icon: ImageVector, tone: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val shown = if (enabled) tone else TextTertiary
    Row(
        Modifier.pressable(text, enabled = enabled, onClick = onClick).height(54.dp).clip(WellShape).background(Well).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = shown, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = shown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
