package com.macrotracker.ui.screens.link

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.link.LinkConfig
import com.macrotracker.data.link.LinkDirectoryState
import com.macrotracker.data.link.LinkEvent
import com.macrotracker.data.link.LinkHost
import com.macrotracker.data.link.LinkSession
import com.macrotracker.data.link.LinkState
import com.macrotracker.data.link.LinkStatus
import com.macrotracker.data.link.LinkVideo
import com.macrotracker.ui.components.Hairline
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.PillButton
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.screens.ai.MenuRow
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
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
import kotlin.math.min

/**
 * The laptop, from the phone. Not connected, it lists the laptops that announced themselves
 * to the dashboard: a tap connects. Upright: the laptop's screen in the top third, a trackpad
 * under it, and the phone's own keyboard for typing (it rises by itself when a click lands in
 * a text field). On its side: the laptop's screen fills the phone, for watching what plays
 * there, with the controls in a glass panel that folds away.
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
    val online = state.status == LinkStatus.Online
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
                    toast = "The laptop's clipboard is empty"
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
                toast = if (on) "$laptopName's sound plays on this phone" else "$laptopName's sound stays there"
            },
            phoneAudio = { on ->
                session.setPhoneAudio(on)
                toast = if (on) "This phone's sound plays on $laptopName" else "This phone's sound is back here"
            },
            mirror = { on ->
                session.setMirror(on)
                toast = if (on) "Opening this phone in a window on $laptopName" else "Closed the window on $laptopName"
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
            transitionSpec = { fadeIn(MacroMotion.fadeTween()) togetherWith fadeOut(MacroMotion.fadeTween()) },
            label = "link",
        ) { connected ->
            when {
                !connected -> Finder(
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
        Toast(toast, Modifier.align(Alignment.TopCenter))
    }

    if (sheet && online) {
        LinkSheet(
            viewModel = viewModel, state = state, config = config, laptopName = laptopName, actions = actions,
            onDismiss = { sheet = false },
        )
    }
}

/** The switches that tell you what they did, shared by the dock, the glass panel and the sheet. */
private class LinkActions(
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val third = maxHeight / 3
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            // The laptop's screen: the top third of the phone.
            Box(Modifier.fillMaxWidth().height(third).background(Color.Black).statusBarsPadding()) {
                Preview(
                    session = session, geometry = geometry,
                    zoom = viewModel.zoom, fill = config.fillPreview,
                    error = state.videoError,
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
                GlassButton(AppIcons.ArrowBack, "Back", hazeState, Modifier.align(Alignment.TopStart).padding(10.dp)) { onNavigateBack() }
                StatusPill(state, laptopName, hazeState, Modifier.align(Alignment.TopEnd).padding(10.dp), onClick = onMore)
                val zoomed = geometry.zoomState.floatValue > 1.01f
                GlassButton(
                    if (zoomed) AppIcons.Minimize else AppIcons.Maximize, if (zoomed) "Show the whole screen" else "Zoom in",
                    hazeState, Modifier.align(Alignment.BottomEnd).padding(10.dp),
                ) { viewModel.zoom = if (zoomed) 1f else max(geometry.fillZoom, 2f) }
            }

            // The trackpad.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Surface)
                    .trackpad(
                        session, config.speed, config.naturalScroll,
                        onTick = haptics::tick, onHold = haptics::click, onTouched = { viewModel.touched = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // what the pad does, until a finger has been on it
                androidx.compose.animation.AnimatedVisibility(
                    visible = !viewModel.touched && !imeVisible,
                    enter = fadeIn(MacroMotion.fadeTween()), exit = fadeOut(MacroMotion.revealTween()),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        Gesture("Tap", "click")
                        Gesture("Hold", "drag")
                        Gesture("Two fingers", "scroll")
                    }
                }
            }
            // With the keyboard up the room goes to the trackpad; a tap and a held finger still click and drag.
            androidx.compose.animation.AnimatedVisibility(
                visible = !imeVisible,
                enter = expandVertically(MacroMotion.slideTween()) + fadeIn(MacroMotion.fadeTween()),
                exit = shrinkVertically(MacroMotion.slideTween()) + fadeOut(MacroMotion.fadeTween()),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MouseButton(session, "left", "Left", RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 20.dp, bottomEnd = 8.dp), Modifier.weight(1f))
                    MouseButton(session, "right", "Right", RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 20.dp), Modifier.weight(1f))
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = imeVisible,
                enter = expandVertically(MacroMotion.slideTween()) + fadeIn(MacroMotion.fadeTween()),
                exit = shrinkVertically(MacroMotion.slideTween()) + fadeOut(MacroMotion.fadeTween()),
            ) { KeyRow(mods, onMods, onKey) }

            // The dock: every switch with its name under it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(26.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockButton(AppIcons.Keyboard, "Keyboard", Modifier.weight(1f), on = imeVisible) { onKeyboard() }
                DockButton(AppIcons.RotateCw, "Full screen", Modifier.weight(1f)) { onSideways() }
                DockButton(
                    if (state.laptopSound) AppIcons.Volume else AppIcons.VolumeOff, "Hear laptop", Modifier.weight(1f), on = state.laptopSound,
                ) { actions.laptopSound(!state.laptopSound) }
                DockButton(AppIcons.Headphones, "Phone audio", Modifier.weight(1f), on = state.phoneAudio) { actions.phoneAudio(!state.phoneAudio) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockButton(AppIcons.ClipboardCopy, "Clipboard") { clipMenu = true }
                    DropdownMenu(expanded = clipMenu, onDismissRequest = { clipMenu = false }, modifier = Modifier.background(Surface)) {
                        MenuRow("Send what's copied here to $laptopName", AppIcons.ClipboardCopy) { clipMenu = false; actions.sendClipboard() }
                        MenuRow("Bring what's copied on $laptopName here", AppIcons.ClipboardPaste) { clipMenu = false; actions.fetchClipboard() }
                    }
                }
                DockButton(AppIcons.Ellipsis, "More", Modifier.weight(1f)) { onMore() }
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
        Preview(
            session = session, geometry = geometry, zoom = 1f, fill = false, error = state.videoError,
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
                PanelButton(0, if (touch) AppIcons.Scan else AppIcons.MousePointer, if (touch) "Touch" else "Trackpad", on = touch) {
                    used++
                    viewModel.touchMode = !touch
                }
                PanelButton(1, AppIcons.Keyboard, "Keyboard", on = imeVisible) { used++; onKeyboard() }
                PanelButton(2, if (state.laptopSound) AppIcons.Volume else AppIcons.VolumeOff, "Sound", on = state.laptopSound) {
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

        androidx.compose.animation.AnimatedVisibility(
            visible = imeVisible,
            enter = slideInVertically(MacroMotion.slideTween()) { it } + fadeIn(MacroMotion.fadeTween()),
            exit = slideOutVertically(MacroMotion.slideTween()) { it } + fadeOut(MacroMotion.fadeTween()),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).chromeGlass(hazeState, RoundedCornerShape(18.dp))) {
                KeyRow(mods, onMods, onKey)
            }
        }
    }
}

// ── the laptop's screen ────────────────────────────────────────────────────────

@Composable
private fun Preview(
    session: LinkSession,
    geometry: PreviewGeometry,
    zoom: Float?,
    fill: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val size by session.video.size.collectAsState()
    val live by session.video.live.collectAsState()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds()) {
        val aspect = if (size.first > 0 && size.second > 0) size.first.toFloat() / size.second else 16f / 9f
        geometry.layout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), aspect, zoom, fill)
        LaunchedEffect(geometry, session) {
            while (true) withFrameNanos { geometry.follow(session.cursorX, session.cursorY) }
        }
        val fitW = geometry.fitW
        val fitH = geometry.fitH
        AndroidView(
            factory = { LinkTextureView(it, session.video) },
            modifier = Modifier
                .align(Alignment.Center)
                .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                .graphicsLayer {
                    val z = geometry.zoomState.floatValue
                    scaleX = z
                    scaleY = z
                    translationX = (0.5f - geometry.centerX.floatValue) * fitW * z
                    translationY = (0.5f - geometry.centerY.floatValue) * fitH * z
                },
        )
        // the cover lifts when the first frame is decoded
        AnimatedVisibility(visible = !live, enter = fadeIn(MacroMotion.Link.leave()), exit = fadeOut(MacroMotion.Link.picture())) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(error, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                } else {
                    LoadingSpinner(color = Primary)
                }
            }
        }
    }
}

/** Shows the decoder's own SurfaceTexture, and leaves it alive when this view goes away. */
@SuppressLint("ViewConstructor")
private class LinkTextureView(context: Context, private val video: LinkVideo) : TextureView(context), TextureView.SurfaceTextureListener {
    init {
        surfaceTextureListener = this
        isOpaque = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (surfaceTexture !== video.texture) {
            try {
                setSurfaceTexture(video.texture)
            } catch (_: RuntimeException) {
                // still held by a view that is on its way out; the next attach takes it
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** False keeps the texture: the decoder draws into it across views. */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = surface !== video.texture
}

// ── not connected: pick a laptop ───────────────────────────────────────────────

/**
 * The laptops the dashboard knows about, each a card: a tap connects. The one being
 * connected to says how that is going, in the card. A laptop the dashboard doesn't list
 * can be reached by its address.
 */
@Composable
private fun Finder(
    state: LinkState,
    config: LinkConfig,
    laptops: LinkDirectoryState,
    onPick: (LinkHost) -> Unit,
    onAddress: (String) -> Unit,
    onRetry: () -> Unit,
    onForget: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val picked = config.host.isNotBlank()
    val pickedName = config.name.ifBlank { config.host.substringBefore(',') }
    // a laptop reached by its address is not in the dashboard's list, but it is still the one picked
    val listed = laptops.hosts.any { it.name == pickedName }

    fun go() {
        if (address.isBlank()) return
        keyboard?.hide()
        typing = false
        onAddress(address)
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        SubScreenHeader(
            title = "Laptop",
            subtitle = "Its screen, a trackpad and this keyboard",
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (picked && !listed) {
                LaptopCard(pickedName, online = null, busy = false, status = state.status, failure = state.failure, picked = true, onClick = onRetry, onRetry = onRetry, onForget = onForget)
            }
            laptops.hosts.forEach { laptop ->
                val mine = picked && laptop.name == pickedName
                LaptopCard(
                    laptop.name, online = laptop.online, busy = laptop.busy,
                    status = if (mine) state.status else LinkStatus.Idle, failure = if (mine) state.failure else null, picked = mine,
                    onClick = { onPick(laptop) }, onRetry = onRetry, onForget = onForget,
                )
            }
            when {
                !laptops.loaded && !picked -> Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    LoadingSpinner(color = Primary, size = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Looking for laptops", color = TextSecondary, fontSize = 14.sp)
                }
                laptops.loaded && laptops.hosts.isEmpty() && !picked -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(AppIcons.Laptop, null, tint = TextTertiary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("No laptop yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        laptops.problem ?: "Run tandem on the laptop. It announces itself to the dashboard and shows up here.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            if (laptops.problem != null && (laptops.hosts.isNotEmpty() || picked)) {
                Text(laptops.problem, color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Spacer(Modifier.height(4.dp))
            androidx.compose.animation.AnimatedVisibility(
                visible = typing,
                enter = expandVertically(MacroMotion.slideTween()) + fadeIn(MacroMotion.fadeTween()),
                exit = shrinkVertically(MacroMotion.slideTween()) + fadeOut(MacroMotion.fadeTween()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false, imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        decorationBox = { inner ->
                            if (address.isEmpty()) Text("Tailscale name or 100.x.y.z", color = TextTertiary, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                            inner()
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton(icon = AppIcons.Link, label = "Connect", accent = Primary, emphasized = true, onClick = { go() })
                }
            }
            if (!typing) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PillButton(icon = AppIcons.Edit, label = "Enter an address", accent = TextSecondary, onClick = { typing = true })
                }
            }
        }
    }
}

@Composable
private fun LaptopCard(
    name: String,
    /** null for a laptop reached by its address: the dashboard says nothing about it. */
    online: Boolean?,
    busy: Boolean,
    status: LinkStatus,
    failure: String?,
    picked: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onForget: () -> Unit,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(22.dp)
    val trying = picked && (status == LinkStatus.Connecting || status == LinkStatus.Idle)
    val waiting = picked && status == LinkStatus.Pending
    val stuck = picked && (status == LinkStatus.Failed || status == LinkStatus.Denied)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, if (picked) Primary.copy(alpha = 0.45f) else Border, shape)
            .clickable(role = Role.Button, enabled = !trying && !waiting) { haptics.click(); onClick() }
            .animateContentSize(MacroMotion.entranceSpring())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Laptop, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (online == true && !stuck) {
                        LivePulseDot(color = Success)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        when {
                            trying -> "Connecting"
                            waiting -> "Waiting for a yes on $name"
                            picked && status == LinkStatus.Denied -> "$name said no"
                            picked && status == LinkStatus.Failed -> "Couldn't connect"
                            online == null -> "By address"
                            online && busy -> "Online · a phone is using it"
                            online -> "Online · tap to connect"
                            else -> "Away"
                        },
                        color = if (stuck) Error else TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trying || waiting) LoadingSpinner(color = Primary, size = 18.dp) else Icon(AppIcons.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        }
        if (waiting || stuck) {
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    waiting -> "Click the notification on $name, or allow it on the dashboard's phone page."
                    status == LinkStatus.Denied -> "Ask again, and answer yes on $name this time."
                    else -> failure ?: "Is it on, and Tailscale up on both?"
                },
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        if (stuck) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(icon = AppIcons.Refresh, label = "Try again", accent = Primary, emphasized = true, onClick = onRetry)
                PillButton(icon = AppIcons.Close, label = "Forget", accent = TextSecondary, onClick = onForget)
            }
        }
    }
}

// ── more: everything else, in a sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(
    viewModel: LinkViewModel,
    state: LinkState,
    config: LinkConfig,
    laptopName: String,
    actions: LinkActions,
    onDismiss: () -> Unit,
) {
    val session = viewModel.session
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Laptop, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(laptopName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LivePulseDot(color = Success)
                        Spacer(Modifier.width(7.dp))
                        Text(listOfNotNull("Connected", state.pingMs?.let { "$it ms" }).joinToString(" · "), color = TextSecondary, fontSize = 13.sp)
                    }
                }
                PillButton(icon = AppIcons.LinkOff, label = "Disconnect", accent = TextSecondary, onClick = { onDismiss(); viewModel.disconnect() })
            }

            SheetGroup("Playing on $laptopName") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        Triple(AppIcons.SkipBack, "Previous", "prev"),
                        Triple(AppIcons.Play, "Play", "play"),
                        Triple(AppIcons.SkipForward, "Next", "next"),
                        Triple(AppIcons.VolumeOff, "Mute", "mute"),
                        Triple(AppIcons.VolumeLow, "Quieter", "voldown"),
                        Triple(AppIcons.Volume, "Louder", "volup"),
                    ).forEach { (icon, label, command) -> DockButton(icon, label) { session.media(command) } }
                }
            }

            SheetGroup("Between the two") {
                SheetRow(AppIcons.MonitorSmartphone, "This phone in a window on $laptopName", "Driven with its keyboard and mouse", on = state.mirror) { actions.mirror(!state.mirror) }
                Hairline()
                SheetRow(AppIcons.Headphones, "This phone's sound on $laptopName", "The phone's own speaker goes quiet meanwhile", on = state.phoneAudio) { actions.phoneAudio(!state.phoneAudio) }
                Hairline()
                SheetRow(AppIcons.Volume, "$laptopName's sound on this phone", "It keeps playing there too", on = state.laptopSound) { actions.laptopSound(!state.laptopSound) }
                Hairline()
                SheetRow(AppIcons.VolumeLow, "Bring quiet sound up", "A laptop mixed for headphones is a whisper from a phone", on = config.levelSound) { session.setLevelSound(!config.levelSound) }
                Hairline()
                SheetRow(AppIcons.ClipboardPaste, "Bring over what's copied on $laptopName", null) { onDismiss(); actions.fetchClipboard() }
                Hairline()
                SheetRow(AppIcons.Lock, "Lock $laptopName", null) { onDismiss(); session.lock() }
            }

            SheetGroup("Screen shown") {
                ChipRow {
                    Chip("Follow the pointer", state.followPointer) { session.showMonitor(null) }
                    state.monitors.forEach { m ->
                        Chip(m.label.ifBlank { m.name }, !state.followPointer && state.monitor == m.name) { session.showMonitor(m.name) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ChipRow {
                    listOf("low" to "Light, for mobile data", "mid" to "Balanced", "high" to "Sharp").forEach { (id, label) ->
                        Chip(label, config.quality == id) { session.setQuality(id) }
                    }
                }
            }

            SheetGroup("Trackpad") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pointer speed", color = TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.width(14.dp))
                    Slider(
                        value = config.speed,
                        onValueChange = { v -> viewModel.change { it.copy(speed = v) } },
                        valueRange = 0.6f..4f,
                        colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = Border),
                        modifier = Modifier.weight(1f),
                    )
                }
                Hairline()
                SheetRow(AppIcons.SwapVertical, "Scroll like a phone", "Two fingers move the page with them", on = config.naturalScroll) {
                    viewModel.change { it.copy(naturalScroll = !it.naturalScroll) }
                }
                Hairline()
                SheetRow(AppIcons.Keyboard, "Keyboard on text fields", "Rises when a click lands where you type", on = config.autoKeyboard) {
                    viewModel.change { it.copy(autoKeyboard = !it.autoKeyboard) }
                }
                Hairline()
                SheetRow(AppIcons.Maximize, "Start zoomed in", "The preview fills its height and follows the pointer", on = config.fillPreview) {
                    viewModel.zoom = null
                    viewModel.change { it.copy(fillPreview = !it.fillPreview) }
                }
            }
        }
    }
}

@Composable
private fun SheetGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** A row of the sheet. With [on] it is a switch; without, something that happens once. */
@Composable
private fun SheetRow(icon: ImageVector, title: String, sub: String?, on: Boolean? = null, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = if (on != null) Role.Switch else Role.Button) { haptics.tick(); onClick() }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on == true) Primary else TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            if (sub != null) Text(sub, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (on != null) {
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = on, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Primary, checkedThumbColor = Color.White, uncheckedTrackColor = SurfaceElevated, uncheckedBorderColor = Border, uncheckedThumbColor = TextTertiary),
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Text(
        label,
        color = if (selected) Primary else TextSecondary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Primary.copy(alpha = 0.16f) else SurfaceElevated)
            .clickable(role = Role.RadioButton) { haptics.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

// ── small parts ────────────────────────────────────────────────────────────────

/** Gives a little under the finger and springs back; the scale is read in the layer, never in composition. */
@Composable
private fun Modifier.pressable(label: String, onClick: () -> Unit): Modifier {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) MacroMotion.Link.PRESSED_SCALE else 1f, MacroMotion.Link.press(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label) { haptics.tick(); onClick() }
}

/** A round button of the app's glass, over the laptop's picture. */
@Composable
private fun GlassButton(icon: ImageVector, label: String, hazeState: HazeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.pressable(label, onClick).size(40.dp).chromeGlass(hazeState, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(18.dp))
    }
}

/** Which laptop, which of its monitors, and how far away it is. Opens the sheet. */
@Composable
private fun StatusPill(state: LinkState, laptopName: String, hazeState: HazeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.pressable("Laptop and screen", onClick).chromeGlass(hazeState, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LivePulseDot(color = Success)
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(laptopName.ifBlank { null }, state.monitor, state.pingMs?.let { "$it ms" }).joinToString(" · "),
            color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

/** A switch of the dock: its icon in a pill that fills when it is on, its name under it. */
@Composable
private fun DockButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, on: Boolean = false, onClick: () -> Unit) {
    Column(modifier.pressable(label, onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 46.dp, height = 32.dp).clip(RoundedCornerShape(50)).background(if (on) Primary.copy(alpha = 0.2f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (on) Primary else TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (on) Primary else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** A control of the full-screen panel. Each arrives a beat after the one before it. */
@Composable
private fun PanelButton(index: Int, icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    val arrived = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrived.animateTo(1f, MacroMotion.Link.arrive(index)) }
    Column(
        Modifier
            .graphicsLayer {
                alpha = arrived.value
                val s = 0.82f + 0.18f * arrived.value
                scaleX = s
                scaleY = s
            }
            .pressable(label, onClick)
            .width(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Primary.copy(alpha = 0.22f) else Color.Transparent)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) Primary else TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (on) Primary else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PanelDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 26.dp).background(Color.White.copy(alpha = 0.1f)))
}

/** Folds the panel away to this one button, and opens it again. */
@Composable
private fun PanelHandle(open: Boolean, onClick: () -> Unit) {
    val turn = animateFloatAsState(if (open) 0f else 1f, MacroMotion.Link.panel(), label = "handle")
    Box(Modifier.pressable(if (open) "Hide the controls" else "Show the controls", onClick).size(46.dp), contentAlignment = Alignment.Center) {
        // one of the two carries the name, so a screen reader (and a test) can find the handle
        Icon(AppIcons.Close, if (open) "Hide the controls" else "Show the controls", tint = TextPrimary, modifier = Modifier.size(18.dp).graphicsLayer { alpha = 1f - turn.value; rotationZ = -90f * turn.value })
        Icon(AppIcons.Ellipsis, null, tint = TextPrimary, modifier = Modifier.size(20.dp).graphicsLayer { alpha = turn.value; rotationZ = 90f * (1f - turn.value) })
    }
}

@Composable
private fun MouseButton(session: LinkSession, button: String, label: String, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var down by remember { mutableStateOf(false) }
    val lit = animateFloatAsState(if (down) 1f else 0f, MacroMotion.Link.press(), label = "mouse")
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(Surface)
            .graphicsLayer { }
            .mouseButton(session, button, onTick = haptics::tick, onPressed = { down = it }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = lit.value }.background(Primary.copy(alpha = 0.24f)))
        Text(label, color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** One line of the trackpad's hint: what the fingers do, and what that does. */
@Composable
private fun Gesture(what: String, does: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(what, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(does, color = TextTertiary, fontSize = 12.sp)
    }
}

/** A line that says what just happened, dropping in under the status bar. */
@Composable
private fun Toast(message: String?, modifier: Modifier = Modifier) {
    // the words stay while it leaves
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(MacroMotion.slideTween()) { -it } + fadeIn(MacroMotion.fadeTween()),
        exit = slideOutVertically(MacroMotion.slideTween()) { -it } + fadeOut(MacroMotion.fadeTween()),
        modifier = modifier.statusBarsPadding().padding(top = 58.dp, start = 24.dp, end = 24.dp),
    ) {
        Text(
            last,
            color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceElevated.copy(alpha = 0.96f))
                .border(1.dp, Border, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** The keys a phone keyboard lacks, over it. Ctrl, alt, super and shift wait for the next key. */
@Composable
private fun KeyRow(mods: Set<String>, onMods: (Set<String>) -> Unit, onKey: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("esc") { onKey("esc") }
        KeyChip("tab") { onKey("tab") }
        listOf("ctrl", "alt", "super", "shift").forEach { mod ->
            KeyChip(mod, on = mod in mods) { onMods(if (mod in mods) mods - mod else mods + mod) }
        }
        KeyChip("←") { onKey("left") }
        KeyChip("↓") { onKey("down") }
        KeyChip("↑") { onKey("up") }
        KeyChip("→") { onKey("right") }
        KeyChip("del") { onKey("delete") }
        KeyChip("home") { onKey("home") }
        KeyChip("end") { onKey("end") }
        KeyChip("pgup") { onKey("pageup") }
        KeyChip("pgdn") { onKey("pagedown") }
        (1..12).forEach { n -> KeyChip("F$n") { onKey("f$n") } }
    }
}

@Composable
private fun KeyChip(label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(label, onClick)
            .height(38.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) Primary else TextPrimary,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}
