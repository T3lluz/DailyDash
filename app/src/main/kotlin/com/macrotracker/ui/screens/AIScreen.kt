package com.macrotracker.ui.screens

import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.SurfaceElevated
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.remote.NutritionEstimate
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.PillButton
import com.macrotracker.data.chat.ChatBot
import com.macrotracker.ui.components.SegmentedTab
import com.macrotracker.ui.components.SegmentedTabs
import com.macrotracker.ui.screens.ai.BotBubble
import com.macrotracker.ui.screens.ai.BotIdentity
import com.macrotracker.ui.screens.ai.ChatComposer
import com.macrotracker.ui.screens.ai.ChatPaneHeader
import com.macrotracker.ui.screens.ai.ChatPillShape
import com.macrotracker.ui.screens.ai.FollowChatOnKeyboard
import com.macrotracker.ui.screens.ai.followChatBottom
import com.macrotracker.ui.screens.ai.rememberNearChatBottom
import com.macrotracker.ui.screens.ai.DishSuggestion
import com.macrotracker.ui.screens.ai.SmallActionChip
import com.macrotracker.ui.screens.ai.SysopChatPane
import com.macrotracker.ui.screens.ai.HermesChatPane
import com.macrotracker.ui.viewmodel.HermesViewModel
import com.macrotracker.ui.screens.ai.TypingBubble
import com.macrotracker.ui.screens.ai.UserBubble
import com.macrotracker.ui.viewmodel.ChatViewModel
import com.macrotracker.ui.screens.ai.refineDraft
import com.macrotracker.ui.screens.ai.suggestionStripTitle
import com.macrotracker.ui.screens.ai.suggestionsForDraft
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AiViewModel
import com.macrotracker.ui.viewmodel.NutritionChatMessage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val PortionOptions = listOf(0.5f, 1f, 1.5f, 2f)

/** Clanker's identity; Sysop's lives next to its pane. Both feed the same ChatKit. */
private val ClankerIdentity = BotIdentity(
    name = "Clanker",
    accent = Primary,
    avatarIcon = AppIcons.Bot,
    composerHint = "Describe a meal…",
)

/**
 * The AI tab: two bots behind one switcher.
 *
 * Both panes render through `ChatKit`, so the bubbles, composer and typing indicator
 * are literally the same composables — the tabs differ in identity, accent and what
 * they know, not in how they look.
 */
@Composable
fun AIScreen(
    onNavigateToCameraScan: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    initialTab: String? = null,
    serverHandoffId: String? = null,
    viewModel: AiViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    hermesViewModel: HermesViewModel = hiltViewModel(),
) {
    val haptics = rememberHaptics()
    var selectedTab by rememberSaveable(initialTab) {
        mutableStateOf(initialTab ?: ChatBot.MACROS.id)
    }
    val hermesChosen by hermesViewModel.usesHermes.collectAsState()
    // A hand-off Hermes could not take goes to Sysop, and Sysop is then what is on screen.
    var sysopForHandoff by rememberSaveable { mutableStateOf(false) }
    val usesHermes = hermesChosen && !sysopForHandoff

    // A hand-off from a server card opens a thread on it in Tech support. Hermes gets it
    // when it can be reached, since it can look at the server itself; otherwise Sysop.
    LaunchedEffect(serverHandoffId) {
        if (serverHandoffId != null) {
            selectedTab = ChatBot.SYSOP.id
            if (hermesViewModel.usesHermes.value && hermesViewModel.awaitReady()) {
                sysopForHandoff = false
                hermesViewModel.openServerHandoff(serverHandoffId)
            } else {
                sysopForHandoff = hermesViewModel.usesHermes.value
                chatViewModel.openServerHandoff(serverHandoffId)
            }
        }
    }

    // The navbar's Hermes tab or a Hermes notification was tapped: that chat, in Tech support.
    val hermesOpenRequest by hermesViewModel.openRequest.collectAsState()
    LaunchedEffect(hermesOpenRequest) {
        val threadId = hermesOpenRequest ?: return@LaunchedEffect
        selectedTab = ChatBot.SYSOP.id
        sysopForHandoff = false
        hermesViewModel.openRequested(threadId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // One slim row instead of a page title: the chats need the height more than the
        // tab needs its name, which the nav pill already shows.
        SegmentedTabs(
            tabs = listOf(
                SegmentedTab(ChatBot.MACROS.id, "Macros", AppIcons.Restaurant, Primary),
                SegmentedTab(
                    ChatBot.SYSOP.id,
                    if (usesHermes) "Hermes" else "Tech support",
                    AppIcons.Terminal,
                    ServerBrand,
                ),
            ),
            selectedKey = selectedTab,
            onSelect = {
                haptics.tick()
                selectedTab = it
            },
            compact = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == ChatBot.SYSOP.id && usesHermes) {
                HermesChatPane(
                    viewModel = hermesViewModel,
                    onUsePhoneAi = { hermesViewModel.setUsesHermes(false) },
                )
            } else if (selectedTab == ChatBot.SYSOP.id) {
                SysopChatPane(
                    viewModel = chatViewModel,
                    onNavigateToAiSettings = onNavigateToAiSettings,
                    onUseHermes = {
                        sysopForHandoff = false
                        hermesViewModel.setUsesHermes(true)
                    },
                )
            } else {
                MacrosChatPane(
                    onNavigateToCameraScan = onNavigateToCameraScan,
                    onNavigateToAiSettings = onNavigateToAiSettings,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun MacrosChatPane(
    onNavigateToCameraScan: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    viewModel: AiViewModel,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loggedCount by viewModel.loggedCount.collectAsState()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    var draft by rememberSaveable { mutableStateOf("") }
    val liveSuggestions = remember(draft) { suggestionsForDraft(draft) }
    val showSuggestions = !loading && liveSuggestions.isNotEmpty()
    var forceFollow by remember { mutableStateOf(true) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun submitMealPhoto(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(context, "Couldn't read that image.", Toast.LENGTH_SHORT).show()
            return
        }
        forceFollow = true
        viewModel.sendMealPhoto(mealPhotoToBase64(bitmap))
    }

    fun decodeUri(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        submitMealPhoto(decodeUri(uri))
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (success && uri != null) {
            submitMealPhoto(decodeUri(uri))
        }
        file?.delete()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchMealCamera(
                context = context,
                onReady = { uri, file ->
                    pendingCameraUri = uri
                    pendingCameraFile = file
                },
                launcher = takePictureLauncher::launch,
            )
        } else {
            Toast.makeText(context, "Camera permission is needed to take a meal photo.", Toast.LENGTH_SHORT).show()
        }
    }

    fun onTakeMealPhoto() {
        haptics.click()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchMealCamera(
                context = context,
                onReady = { uri, file ->
                    pendingCameraUri = uri
                    pendingCameraFile = file
                },
                launcher = takePictureLauncher::launch,
            )
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onAddMealPhoto() {
        haptics.click()
        galleryLauncher.launch("image/*")
    }

    val nearBottom by rememberNearChatBottom(listState)

    val lastMessageId = messages.lastOrNull()?.id
    val lastEstimateKey = (messages.lastOrNull() as? NutritionChatMessage.Doctor)
        ?.estimate
        ?.let { "${it.calories}:${it.protein}:${it.confidence}" }

    // Keep the conversation pinned to the newest bubble when the user is following along.
    LaunchedEffect(lastMessageId, loading, lastEstimateKey, showSuggestions) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!(forceFollow || nearBottom)) return@LaunchedEffect
        // Two passes: once for the new bubble, again after tall estimate cards measure.
        delay(16)
        listState.followChatBottom()
        delay(48)
        listState.followChatBottom()
        forceFollow = false
    }

    FollowChatOnKeyboard(listState) { messages.isNotEmpty() && (forceFollow || nearBottom) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || loading) return
        haptics.click()
        draft = ""
        forceFollow = true
        viewModel.sendFoodQuery(text)
    }

    fun onSuggestion(suggestion: DishSuggestion) {
        haptics.tick()
        if (suggestion.replacesDraft || draft.isBlank()) {
            draft = ""
            forceFollow = true
            viewModel.sendFoodQuery(suggestion.query)
        } else {
            draft = refineDraft(draft, suggestion)
        }
    }

    // The composer is glass over the conversation, so the chat is its own haze
    // source — the app-level one belongs to the nav pill and would feed back.
    val chatHaze = rememberHazeState()
    // The composer floats over the list, so reserve exactly its height at the
    // bottom of the chat instead of guessing a constant.
    var composerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiChatHeader(
                loggedCount = loggedCount,
                loading = loading,
                onCameraScan = {
                    haptics.click()
                    onNavigateToCameraScan()
                },
                onClear = {
                    haptics.tick()
                    viewModel.clearChat()
                    draft = ""
                    forceFollow = true
                },
                onCancel = {
                    haptics.tick()
                    forceFollow = true
                    viewModel.cancelEstimate()
                },
                canClear = messages.size > 1 && !loading,
            )

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
                items(messages, key = { it.id }) { message ->
                    when (message) {
                        is NutritionChatMessage.Doctor -> BotBubble(
                            identity = ClankerIdentity,
                            text = message.text,
                            isError = message.isError,
                            actions = {
                                if (message.isError) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        message.retryQuery?.let { query ->
                                            SmallActionChip(
                                                icon = AppIcons.Refresh,
                                                label = "Retry",
                                                onClick = {
                                                    haptics.click()
                                                    forceFollow = true
                                                    viewModel.retryQuery(query)
                                                },
                                            )
                                        }
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
                                }
                            },
                            // The per-message slot: Clanker's estimate card hangs here,
                            // and it is the same hook any future bot card would use.
                            attachment = message.estimate?.let { estimate ->
                                {
                                    EstimateCard(
                                        estimate = estimate,
                                        logged = message.estimateLogged,
                                        onLog = {
                                            haptics.confirm()
                                            forceFollow = true
                                            viewModel.logEstimate(message.id, it)
                                        },
                                    )
                                }
                            },
                        )
                        is NutritionChatMessage.User -> UserBubble(text = message.text)
                        is NutritionChatMessage.Typing -> TypingBubble(
                            identity = ClankerIdentity,
                            label = "Estimating…",
                            onCancel = {
                                haptics.tick()
                                forceFollow = true
                                viewModel.cancelEstimate()
                            },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Fade the conversation out under the floating bar so chips and
                // bubbles never collide.
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Background.copy(alpha = 0.92f),
                        1f to Background,
                    ),
                )
                .onSizeChanged { size ->
                    composerHeight = with(density) { size.height.toDp() }
                },
        ) {
            AnimatedVisibility(
                visible = showSuggestions,
                enter = fadeIn(MacroMotion.fadeTween()) + slideInVertically(
                    animationSpec = MacroMotion.slideTween(),
                    initialOffsetY = { it / 4 },
                ),
                exit = fadeOut(MacroMotion.fadeTween()),
            ) {
                SuggestionStrip(
                    title = suggestionStripTitle(draft),
                    suggestions = liveSuggestions,
                    refining = draft.isNotBlank(),
                    onPick = ::onSuggestion,
                )
            }

            ChatComposer(
                value = draft,
                onValueChange = { draft = it },
                onSend = { send() },
                enabled = !loading,
                hint = ClankerIdentity.composerHint,
                accent = Primary,
                hazeState = chatHaze,
                leading = {
                    MealPhotoButton(
                        enabled = !loading,
                        onTakePhoto = ::onTakeMealPhoto,
                        onAddPhoto = ::onAddMealPhoto,
                    )
                },
            )
        }
    }
}

private fun launchMealCamera(
    context: Context,
    onReady: (Uri, File) -> Unit,
    launcher: (Uri) -> Unit,
) {
    try {
        val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
        val file = File.createTempFile("meal_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onReady(uri, file)
        launcher(uri)
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "Couldn't open camera.", Toast.LENGTH_SHORT).show()
    }
}

private fun mealPhotoToBase64(bitmap: Bitmap): String {
    val maxSide = 1280
    val scaled = if (bitmap.width <= maxSide && bitmap.height <= maxSide) {
        bitmap
    } else {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
    if (scaled !== bitmap) scaled.recycle()
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

// ── Chrome ───────────────────────────────────────────────────────────────────

@Composable
private fun AiChatHeader(
    loggedCount: Int,
    loading: Boolean,
    onCameraScan: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    canClear: Boolean,
) {
    val status = when {
        loading -> "Estimating macros…"
        loggedCount == 1 -> "1 meal logged this chat"
        loggedCount > 1 -> "$loggedCount meals logged this chat"
        else -> "Describe a meal, snap it, or scan the label"
    }
    ChatPaneHeader(status = status, active = loading, accent = Primary) {
        PillButton(
            icon = AppIcons.Camera,
            label = "Scan label",
            emphasized = true,
            onClick = onCameraScan,
        )
        if (loading) {
            PillButton(icon = AppIcons.Close, label = "Stop", onClick = onCancel)
        } else if (canClear) {
            PillButton(icon = AppIcons.NewChat, label = "New chat", onClick = onClear)
        }
    }
}

@Composable
private fun SuggestionStrip(
    title: String,
    suggestions: List<DishSuggestion>,
    refining: Boolean,
    onPick: (DishSuggestion) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = TextTertiary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(suggestions, key = { it.label }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(PillShape)
                        .border(1.dp, Border, PillShape)
                        .background(Surface)
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (suggestion.iconRes != null) {
                        Image(
                            painter = painterResource(suggestion.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (refining) {
                        Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        suggestion.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = ChatPillShape

// ── Estimate card ────────────────────────────────────────────────────────────

@Composable
private fun EstimateCard(
    estimate: NutritionEstimate,
    logged: Boolean,
    onLog: (NutritionEstimate) -> Unit,
) {
    var portion by remember(estimate.foodName, estimate.calories, estimate.protein) {
        mutableFloatStateOf(1f)
    }
    var editMode by remember(estimate.foodName) { mutableStateOf(false) }
    var caloriesText by remember(estimate.calories) { mutableStateOf(estimate.calories.toString()) }
    var proteinText by remember(estimate.protein) { mutableStateOf(estimate.protein.toString()) }

    val baseCalories = caloriesText.toIntOrNull()?.coerceAtLeast(0) ?: estimate.calories
    val baseProtein = proteinText.toIntOrNull()?.coerceAtLeast(0) ?: estimate.protein
    val finalCalories = (baseCalories * portion).roundToInt()
    val finalProtein = (baseProtein * portion).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, Border, CardShape)
            .background(Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    estimate.foodName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 19.sp,
                )
                Text(
                    estimate.servingDescription,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ConfidenceChip(estimate.confidence)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatBox(
                label = "CALORIES",
                value = finalCalories.toString(),
                unit = "kcal",
                accent = Primary,
                modifier = Modifier.weight(1f),
            )
            StatBox(
                label = "PROTEIN",
                value = finalProtein.toString(),
                unit = "g",
                accent = Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        if (!logged) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PORTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (editMode) "Done" else "Edit values",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable { editMode = !editMode }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            // One segmented track reads cleaner than four loose buttons.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(Background)
                    .border(1.dp, Border, PillShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                PortionOptions.forEach { option ->
                    val selected = portion == option
                    val label = if (option % 1f == 0f) "${option.toInt()}×" else "${option}×"
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = if (selected) TextPrimary else TextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(PillShape)
                            .background(if (selected) SurfaceElevated else Color.Transparent)
                            .border(1.dp, if (selected) BorderStrong else Color.Transparent, PillShape)
                            .clickable { portion = option }
                            .padding(vertical = 7.dp),
                    )
                }
            }

            AnimatedVisibility(visible = editMode) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    MacroTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it.filter { c -> c.isDigit() } },
                        placeholder = "Calories",
                        keyboardType = KeyboardType.Number,
                    )
                    MacroTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it.filter { c -> c.isDigit() } },
                        placeholder = "Protein (g)",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            MacroButton(
                text = "Log $finalCalories kcal · ${finalProtein}g",
                onClick = {
                    onLog(
                        estimate.copy(
                            calories = finalCalories,
                            protein = finalProtein,
                            servingDescription = if (portion == 1f) {
                                estimate.servingDescription
                            } else {
                                "${estimate.servingDescription} × $portion"
                            },
                        ),
                    )
                },
                variant = ButtonVariant.SECONDARY,
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Secondary.copy(alpha = 0.12f))
                    .border(1.dp, Secondary.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    AppIcons.CheckCircle,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Logged to today",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Secondary,
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = accent.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                unit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** Three ticks, because the old bar showed a percentage nobody computed. */
@Composable
private fun ConfidenceChip(confidence: String) {
    val level = when (confidence.lowercase()) {
        "high" -> 3
        "low" -> 1
        else -> 2
    }
    val label = when (level) {
        3 -> "High"
        1 -> "Rough"
        else -> "Medium"
    }
    val color = when (level) {
        3 -> Secondary
        1 -> Error
        else -> Primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(width = 4.dp, height = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < level) color else color.copy(alpha = 0.22f)),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Clanker's composer slot: the photo attach menu. Sysop leaves this slot empty. */
@Composable
private fun MealPhotoButton(
    enabled: Boolean,
    onTakePhoto: () -> Unit,
    onAddPhoto: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = "Add meal photo",
                tint = if (enabled) TextPrimary else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Surface),
        ) {
            DropdownMenuItem(
                text = { Text("Take photo of meal", color = TextPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    onTakePhoto()
                },
                leadingIcon = {
                    Icon(AppIcons.Camera, contentDescription = null, tint = Primary)
                },
            )
            DropdownMenuItem(
                text = { Text("Add photo of meal", color = TextPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    onAddPhoto()
                },
                leadingIcon = {
                    Icon(AppIcons.Images, contentDescription = null, tint = Primary)
                },
            )
        }
    }
}
