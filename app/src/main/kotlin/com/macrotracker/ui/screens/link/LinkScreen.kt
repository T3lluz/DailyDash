package com.macrotracker.ui.screens.link

import android.content.ClipData
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.link.LinkConfig
import com.macrotracker.data.link.LinkEvent
import com.macrotracker.data.link.LinkState
import com.macrotracker.data.link.LinkStatus
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.screens.ai.MenuRow
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.LinkHandsTone
import com.macrotracker.ui.theme.LinkPhoneTone
import com.macrotracker.ui.theme.LinkScreenTone
import com.macrotracker.ui.theme.LinkSoundTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.findActivity
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.viewmodel.LinkViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The laptop, from the phone, in Health's look. Not connected, it lists the laptops that
 * announced themselves to the dashboard ([LinkFinder]): a tap connects. Upright: the laptop's
 * screen in the top third, a trackpad room under it with a glass dock over its foot, and the
 * phone's own keyboard for typing (it rises by itself when a click lands in a text field). On
 * its side: the laptop's screen fills the phone, with the controls in a glass panel that folds
 * away. When the pointer crosses to another monitor the picture swipes over to it ([LinkPreview]).
 * The laptop runs `tandem` (its own repo); screen, sound and touches go straight between the
 * two over the tailnet. Reached from Settings and `EXTRA_OPEN` `laptop`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()
    val laptops by viewModel.laptops.collectAsState()
    val session = viewModel.session
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // a link that dropped stays on the remote while it comes back
    val online = state.status == LinkStatus.Online || state.status == LinkStatus.Reconnecting
    val imeVisible = WindowInsets.isImeVisible
    val laptopName = state.laptop.ifBlank { config.name.ifBlank { config.host.substringBefore(',') } }
    // the laptop's picture is what the glass over it blurs
    val hazeState = rememberHazeState()

    var keyView by remember { mutableStateOf<LinkKeyView?>(null) }
    var mods by remember { mutableStateOf(setOf<String>()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    // the keyboard came up by itself, so it may go down by itself
    var raisedForField by remember { mutableStateOf(false) }
    val geometry = remember { PreviewGeometry() }

    BackHandler {
        if (landscape && viewModel.orientation == 1) viewModel.orientation = 2 else onNavigateBack()
    }

    // Connected while the screen is in front; a rotation keeps the connection.
    val resumed = rememberIsResumed()
    DisposableEffect(resumed) {
        if (resumed) viewModel.start()
        onDispose { if (!activity.isChangingConfigurations) viewModel.stop() }
    }
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(viewModel.orientation) {
        activity.requestedOrientation = when (viewModel.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val immersive = landscape && online
    LaunchedEffect(immersive, resumed) {
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersive) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // asked again once the rotated window has settled: the first ask can land before it has the bars
            repeat(3) {
                bars.hide(WindowInsetsCompat.Type.systemBars())
                delay(350)
            }
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Looking for laptops while there is none to show; once connected, one look keeps the saved ticket current.
    LaunchedWhileResumed(online) {
        viewModel.findLaptops()
        while (true) {
            delay(if (online) 3_000 else 4_000)
            if (online) session.ping() else viewModel.findLaptops()
        }
    }

    val autoKeyboard by rememberUpdatedState(config.autoKeyboard)
    val imeUp by rememberUpdatedState(imeVisible)
    LaunchedEffect(session) {
        viewModel.events.collect { event ->
            when (event) {
                is LinkEvent.Toast -> toast = event.message
                is LinkEvent.TextFocus -> if (autoKeyboard) {
                    if (event.focused && !imeUp) {
                        raisedForField = true
                        keyView?.showKeyboard()
                    } else if (!event.focused && raisedForField) {
                        raisedForField = false
                        keyView?.hideKeyboard()
                    }
                }
                is LinkEvent.Clipboard -> if (event.text.isEmpty()) {
                    toast = "Nothing is copied on $laptopName"
                } else {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Laptop", event.text)))
                    toast = "Copied from $laptopName"
                }
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(MacroMotion.Link.TOAST_MS)
            toast = null
        }
    }
    LaunchedEffect(imeVisible) { if (!imeVisible) raisedForField = false }
    // The keyboard belongs to the state it was raised in: the list's address field, or the laptop.
    LaunchedEffect(online) {
        if (!online) sheet = false
        raisedForField = false
        keyView?.hideKeyboard()
    }

    fun typed(text: String) {
        val chord = if (mods.isEmpty()) null else chordKey(text)
        if (chord != null) {
            session.key(chord, mods)
            mods = emptySet()
        } else {
            session.text(text)
        }
    }

    fun pressed(name: String) {
        session.key(name, mods)
        if (mods.isNotEmpty()) mods = emptySet()
    }

    fun toggleKeyboard() {
        raisedForField = false
        if (imeVisible) keyView?.hideKeyboard() else keyView?.showKeyboard()
    }

    // Each switch says what it did: the two sounds are easy to mix up.
    val actions = remember(session, laptopName) {
        LinkActions(
            laptopSound = { on ->
                session.setLaptopSound(on)
                toast = if (on) "$laptopName's sound plays here now" else "$laptopName's sound stays on $laptopName"
            },
            phoneAudio = { on ->
                session.setPhoneAudio(on)
                toast = if (on) "This phone's sound plays on $laptopName now" else "This phone's sound is back here"
            },
            mirror = { on ->
                session.setMirror(on)
                toast = if (on) "Opening this phone in a window on $laptopName" else "Closed this phone's window on $laptopName"
            },
            sendClipboard = {
                scope.launch {
                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                    if (text.isEmpty()) toast = "Nothing is copied on this phone" else session.copyToLaptop(text)
                }
            },
            fetchClipboard = { session.askClipboard() },
        )
    }

    Box(Modifier.fillMaxSize().background(if (immersive) Color.Black else Background)) {
        AnimatedContent(
            targetState = online,
            transitionSpec = {
                (fadeIn(MacroMotion.Link.picture()) + scaleIn(MacroMotion.Link.swipe(), initialScale = 0.96f)) togetherWith fadeOut(MacroMotion.fadeTween())
            },
            label = "link",
        ) { connected ->
            when {
                !connected -> LinkFinder(
                    state = state, config = config, laptops = laptops,
                    onPick = viewModel::connectTo, onAddress = viewModel::connectTo,
                    onRetry = { session.reconnect() }, onForget = viewModel::disconnect,
                    onNavigateBack = onNavigateBack,
                )
                landscape -> Sideways(
                    viewModel = viewModel, state = state, config = config, geometry = geometry, hazeState = hazeState,
                    imeVisible = imeVisible, mods = mods, onMods = { mods = it }, onKey = ::pressed,
                    actions = actions, onKeyboard = ::toggleKeyboard, onUpright = { viewModel.orientation = 2 },
                )
                else -> Upright(
                    viewModel = viewModel, state = state, config = config, geometry = geometry, hazeState = hazeState,
                    laptopName = laptopName, imeVisible = imeVisible, mods = mods, onMods = { mods = it }, onKey = ::pressed,
                    actions = actions, onKeyboard = ::toggleKeyboard, onMore = { sheet = true },
                    onSideways = { viewModel.orientation = 1 }, onNavigateBack = onNavigateBack,
                )
            }
        }
        // The phone's keyboard types into this; it is never seen.
        AndroidView(
            factory = { LinkKeyView(it).also { view -> keyView = view } },
            update = { view ->
                view.onText = ::typed
                view.onKey = ::pressed
            },
            modifier = Modifier.size(1.dp).alpha(0f),
        )
        LinkToast(toast, hazeState, Modifier.align(Alignment.TopCenter))
    }

    if (sheet && online) {
        LinkSheet(
            viewModel = viewModel, state = state, config = config, laptopName = laptopName, actions = actions,
            onDismiss = { sheet = false },
        )
    }
}

/** The switches that tell you what they did, shared by the dock, the glass panel and the sheet. */
internal class LinkActions(
    val laptopSound: (Boolean) -> Unit,
    val phoneAudio: (Boolean) -> Unit,
    val mirror: (Boolean) -> Unit,
    val sendClipboard: () -> Unit,
    val fetchClipboard: () -> Unit,
)

// ── upright: screen on top, trackpad under it ──────────────────────────────────

@Composable
private fun Upright(
    viewModel: LinkViewModel,
    state: LinkState,
    config: LinkConfig,
    geometry: PreviewGeometry,
    hazeState: HazeState,
    laptopName: String,
    imeVisible: Boolean,
    mods: Set<String>,
    onMods: (Set<String>) -> Unit,
    onKey: (String) -> Unit,
    actions: LinkActions,
    onKeyboard: () -> Unit,
    onMore: () -> Unit,
    onSideways: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val session = viewModel.session
    val haptics = rememberHaptics()
    var clipMenu by remember { mutableStateOf(false) }
    val reconnecting = state.status == LinkStatus.Reconnecting
    var padDown by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Zoomed in (or set to start that way), the picture takes its full share of the screen; at its
        // whole size it is only as tall as the monitor's shape needs, so an ultrawide leaves no black
        // bands and the trackpad gets the room. The shape is the monitor the picture shows (it changes
        // when a swipe lands), and the box's height moves in the layout pass (the picture inside it
        // lays itself out again for that short while).
        val most = maxHeight * 0.29f
        val mon = state.shownMonitor
        val aspect = if (mon != null && mon.width > 0 && mon.height > 0) mon.width.toFloat() / mon.height else 16f / 10f
        val zoomedIn = viewModel.zoom?.let { it > 1.01f } ?: config.fillPreview
        // A monitor of another shape changes the box once its slide is over, never during it, and
        // never while a finger is on the pad (its top edge would move under the finger). Zoom acts at once.
        val shape = minOf(most, maxWidth / aspect)
        var settledShape by remember { mutableStateOf(shape) }
        LaunchedEffect(shape, padDown) {
            if (settledShape != shape && !padDown) {
                delay(MacroMotion.Link.SWIPE_SETTLE_MS)
                settledShape = shape
            }
        }
        val pictureHeight = animateDpAsState(if (zoomedIn) most else settledShape, MacroMotion.Link.panel(), label = "picture")
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            // The header: back, which laptop and screen (opens the sheet), and zoom. Nothing sits on the picture.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(AppIcons.ArrowBack, "Back") { onNavigateBack() }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    StatusPill(state, laptopName, onClick = onMore)
                }
                Spacer(Modifier.width(8.dp))
                val zoomed = geometry.zoomState.floatValue > 1.01f
                RoundButton(if (zoomed) AppIcons.ZoomOut else AppIcons.ZoomIn, if (zoomed) "Show the whole screen" else "Zoom in") {
                    viewModel.zoom = if (zoomed) 1f else max(geometry.fillZoom, 2f)
                }
            }
            // The laptop's screen, edge to edge for the most picture.
            Box(
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val h = pictureHeight.value.roundToPx()
                        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    }
                    .background(Color.Black),
            ) {
                LinkPreview(
                    session = session, geometry = geometry,
                    zoom = viewModel.zoom, fill = config.fillPreview,
                    error = state.videoError, note = if (reconnecting) "Reconnecting" else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .pointerInput(session) {
                            detectTapGestures(onTap = { at ->
                                geometry.toScreen(at)?.let { p ->
                                    haptics.tick()
                                    session.pointAt(p.x, p.y, click = true)
                                }
                            })
                        }
                        .pointerInput(session) {
                            detectTransformGestures { _, pan, change, _ ->
                                if (change != 1f) viewModel.zoom = (geometry.zoom * change).coerceIn(1f, 8f)
                                geometry.nudge(pan)
                            }
                        },
                )
            }

            LinkTrackpad(
                viewModel = viewModel, config = config,
                hint = !viewModel.touched && !imeVisible,
                buttons = !imeVisible,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = 12.dp),
                onTouch = { padDown = it },
            )
            AnimatedVisibility(
                visible = imeVisible,
                enter = expandVertically(MacroMotion.slideTween()) + fadeIn(MacroMotion.fadeTween()),
                exit = shrinkVertically(MacroMotion.slideTween()) + fadeOut(MacroMotion.fadeTween()),
            ) {
                KeyRow(mods, LinkHandsTone, onMods, onKey)
            }
            // The dock: Health's meter tiles, each in its colour; the end tiles take the trackpad's corners.
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val first = RoundedCornerShape(topStart = 18.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 28.dp)
                val middle = RoundedCornerShape(14.dp)
                val last = RoundedCornerShape(topStart = 14.dp, topEnd = 18.dp, bottomEnd = 28.dp, bottomStart = 14.dp)
                DockTile(AppIcons.Keyboard, "Keyboard", LinkHandsTone, first, Modifier.weight(1f), on = imeVisible) { onKeyboard() }
                DockTile(AppIcons.Fullscreen, "Full screen", LinkScreenTone, middle, Modifier.weight(1f)) { onSideways() }
                DockTile(
                    if (state.laptopSound) AppIcons.LaptopSound else AppIcons.LaptopSoundOff,
                    if (state.laptopSound) "Laptop's sound plays here" else "Play the laptop's sound here", LinkSoundTone, middle, Modifier.weight(1f),
                    on = state.laptopSound,
                ) { actions.laptopSound(!state.laptopSound) }
                Box(Modifier.weight(1f)) {
                    DockTile(AppIcons.ClipboardSwap, "Clipboard", LinkPhoneTone, middle, Modifier.fillMaxWidth()) { clipMenu = true }
                    DropdownMenu(expanded = clipMenu, onDismissRequest = { clipMenu = false }, modifier = Modifier.background(Surface)) {
                        MenuRow("Send what's copied here to $laptopName", AppIcons.ClipboardCopy) { clipMenu = false; actions.sendClipboard() }
                        MenuRow("Copy what's copied on $laptopName", AppIcons.ClipboardPaste) { clipMenu = false; actions.fetchClipboard() }
                    }
                }
                DockTile(AppIcons.Ellipsis, "More", TextSecondary, last, Modifier.weight(1f)) { onMore() }
            }
        }
    }
}

// ── on its side: the laptop's screen fills the phone ────────────────────────────

@Composable
private fun Sideways(
    viewModel: LinkViewModel,
    state: LinkState,
    config: LinkConfig,
    geometry: PreviewGeometry,
    hazeState: HazeState,
    imeVisible: Boolean,
    mods: Set<String>,
    onMods: (Set<String>) -> Unit,
    onKey: (String) -> Unit,
    actions: LinkActions,
    onKeyboard: () -> Unit,
    onUpright: () -> Unit,
) {
    val session = viewModel.session
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(true) }
    // every touch of a control starts the wait again
    var used by remember { mutableIntStateOf(0) }
    LaunchedEffect(open, used) {
        if (open) {
            delay(MacroMotion.Link.CONTROLS_STAY_MS)
            open = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LinkPreview(
            session = session, geometry = geometry, zoom = 1f, fill = false, error = state.videoError,
            note = if (state.status == LinkStatus.Reconnecting) "Reconnecting" else null,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .then(
                    if (viewModel.touchMode) {
                        Modifier.touchscreen(session, geometry::toScreen, onTick = haptics::tick, onHold = haptics::click)
                    } else {
                        Modifier.trackpad(session, config.speed, config.naturalScroll, onTick = haptics::tick, onHold = haptics::click)
                    },
                ),
        )

        // The controls: one glass panel that unfolds from its handle.
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .chromeGlass(hazeState, shape)
                .animateContentSize(MacroMotion.Link.panel())
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open) {
                val touch = viewModel.touchMode
                PanelButton(0, if (touch) AppIcons.Scan else AppIcons.MousePointer, if (touch) "Touch" else "Trackpad", LinkHandsTone, on = touch) {
                    used++
                    viewModel.touchMode = !touch
                }
                PanelButton(1, AppIcons.Keyboard, "Keyboard", LinkHandsTone, on = imeVisible) { used++; onKeyboard() }
                PanelButton(2, if (state.laptopSound) AppIcons.LaptopSound else AppIcons.LaptopSoundOff, "Sound", LinkSoundTone, on = state.laptopSound) {
                    used++
                    actions.laptopSound(!state.laptopSound)
                }
                PanelDivider()
                PanelButton(3, AppIcons.Play, "Play") { used++; session.media("play") }
                PanelButton(4, AppIcons.VolumeLow, "Quieter") { used++; session.media("voldown") }
                PanelButton(5, AppIcons.Volume, "Louder") { used++; session.media("volup") }
                PanelDivider()
                PanelButton(6, AppIcons.Minimize, "Exit") { onUpright() }
            }
            PanelHandle(open) { open = !open }
        }

        AnimatedVisibility(
            visible = imeVisible,
            enter = slideInVertically(MacroMotion.slideTween()) { it } + fadeIn(MacroMotion.fadeTween()),
            exit = slideOutVertically(MacroMotion.slideTween()) { it } + fadeOut(MacroMotion.fadeTween()),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).chromeGlass(hazeState, RoundedCornerShape(18.dp))) {
                KeyRow(mods, LinkHandsTone, onMods, onKey)
            }
        }
    }
}
