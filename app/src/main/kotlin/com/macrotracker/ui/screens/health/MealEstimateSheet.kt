package com.macrotracker.ui.screens.health

import androidx.compose.foundation.layout.heightIn
import com.macrotracker.ui.components.joinedRowShape
import com.macrotracker.ui.components.TilePlace
import com.macrotracker.ui.components.Joined
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.remote.NutritionEstimate
import com.macrotracker.ui.components.AiPickerSheet
import com.macrotracker.ui.components.AiPill
import com.macrotracker.ui.screens.ai.DishSuggestion
import com.macrotracker.ui.screens.ai.refineDraft
import com.macrotracker.ui.screens.ai.suggestionsForDraft
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.preparePhoto
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AiSetupViewModel
import com.macrotracker.ui.viewmodel.AiViewModel
import com.macrotracker.ui.viewmodel.NutritionChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** What the card lists, newest first: an estimate to log, something that went wrong, or a line of the AI's. */
private sealed interface MealItem {
    val id: String

    data class Result(override val id: String, val asked: String?, val estimate: NutritionEstimate, val logged: Boolean) : MealItem
    data class Failure(override val id: String, val text: String, val retry: String?, val settings: Boolean) : MealItem
    data class Note(override val id: String, val text: String) : MealItem
}

private fun mealItems(messages: List<NutritionChatMessage>): List<MealItem> {
    val out = ArrayList<MealItem>()
    var asked: String? = null
    messages.forEach { message ->
        when (message) {
            is NutritionChatMessage.User -> asked = message.text
            is NutritionChatMessage.Doctor -> {
                val estimate = message.estimate
                when {
                    estimate != null -> out += MealItem.Result(message.id, asked, estimate, message.estimateLogged)
                    message.isError -> out += MealItem.Failure(message.id, message.text, message.retryQuery, message.showSettingsCta)
                    // The opening line is the card's own empty state.
                    message.id != "welcome" -> out += MealItem.Note(message.id, message.text)
                }
                asked = null
            }
            is NutritionChatMessage.Typing -> Unit
        }
    }
    return out.asReversed()
}

/**
 * Estimating a meal with AI, as a card that slides up over Food. Say what it was (or take or
 * pick a photo of it) on the line at the foot; each answer lands at the top as a card with
 * the calories and protein set large, a portion to pick and one button to log it. Older
 * answers stay under it for as long as the app is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealEstimateSheet(
    onDismiss: () -> Unit,
    onScanLabel: () -> Unit,
    onAiSettings: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
    aiSetup: AiSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val ai by aiSetup.state.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loggedCount by viewModel.loggedCount.collectAsState()
    val items = remember(messages) { mealItems(messages) }
    var pickingAi by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(draft) { suggestionsForDraft(draft) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // The newest answer is at the top; bring it into view when it lands.
    LaunchedEffect(items.firstOrNull()?.id, loading) { listState.animateScrollToItem(0) }

    /** Scaled, turned upright and encoded off the main thread; a full-size decode could run out of memory. */
    fun submitPhoto(uri: Uri, file: File? = null) {
        scope.launch {
            val photo = withContext(Dispatchers.Default) { preparePhoto(context, uri, maxSide = 1280) }
            file?.delete()
            if (photo == null) {
                Toast.makeText(context, "Couldn't read that image.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.sendMealPhoto(photo.base64)
            photo.bitmap.recycle()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { submitPhoto(it) } }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingUri
        val file = pendingFile
        pendingUri = null
        pendingFile = null
        if (taken && uri != null) submitPhoto(uri, file) else file?.delete()
    }
    fun openCamera() = launchMealCamera(context, onReady = { uri, file -> pendingUri = uri; pendingFile = file }, launcher = takePicture::launch)
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else Toast.makeText(context, "Camera permission is needed to take a meal photo.", Toast.LENGTH_SHORT).show()
    }

    fun send(text: String = draft) {
        val query = text.trim()
        if (query.isEmpty() || loading) return
        haptics.click()
        draft = ""
        viewModel.sendFoodQuery(query)
    }
    fun pick(suggestion: DishSuggestion) {
        haptics.tick()
        if (suggestion.replacesDraft || draft.isBlank()) send(suggestion.query) else draft = refineDraft(draft, suggestion)
    }

    FoodSheet(
        title = "Estimate a meal",
        subtitle = when {
            loading -> "Estimating…"
            loggedCount == 1 -> "1 meal logged from here"
            loggedCount > 1 -> "$loggedCount meals logged from here"
            else -> "Say it, snap it, or scan its label"
        },
        onDismiss = onDismiss,
        actions = {
            // The same AI pill and sheet as Tilesmith; photos need one that sees pictures.
            AiPill(ai.current, onClick = { pickingAi = true }, compact = true, open = pickingAi)
            if (items.isNotEmpty() && !loading) {
                FoodRoundButton(AppIcons.NewChat, "Start over", onClick = { haptics.tick(); viewModel.clearChat(); draft = "" })
            }
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                item(key = "estimating") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(FoodWell)
                            .padding(16.dp),
                    ) {
                        RoomLoading(tone = HealthNutritionTone, bars = 3)
                        Spacer(modifier = Modifier.height(4.dp))
                        FoodGhostButton("Stop", onClick = { haptics.tick(); viewModel.cancelEstimate() }, icon = AppIcons.Close)
                    }
                }
            }
            if (items.isEmpty() && !loading) {
                item(key = "start") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                            StartTile(AppIcons.Camera, "Photo of it", Modifier.weight(1f), joinedRowShape(TilePlace.FIRST, outer = 22.dp)) {
                                haptics.click()
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (granted) openCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                            StartTile(AppIcons.Images, "From gallery", Modifier.weight(1f), joinedRowShape(TilePlace.MIDDLE, outer = 22.dp)) { haptics.click(); gallery.launch("image/*") }
                            StartTile(AppIcons.Search, "Scan a label", Modifier.weight(1f), joinedRowShape(TilePlace.LAST, outer = 22.dp)) { haptics.click(); onScanLabel() }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Quick bites", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { SuggestionChip(it, refining = false) { pick(it) } }
                        }
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                when (item) {
                    is MealItem.Result -> EstimateCard(
                        asked = item.asked,
                        estimate = item.estimate,
                        logged = item.logged,
                        onLog = { haptics.confirm(); viewModel.logEstimate(item.id, it) },
                    )
                    is MealItem.Failure -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Warning.copy(alpha = 0.10f))
                            .padding(16.dp),
                    ) {
                        Text(item.text, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary)
                        if (item.retry != null || item.settings) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                                val both = item.retry != null && item.settings
                                item.retry?.let { query ->
                                    FoodGhostButton("Try again", onClick = { haptics.click(); viewModel.retryQuery(query) }, icon = AppIcons.Refresh, shape = joinedRowShape(if (both) TilePlace.FIRST else TilePlace.ONLY, outer = 20.dp))
                                }
                                if (item.settings) {
                                    FoodGhostButton("AI settings", onClick = { haptics.click(); onAiSettings() }, icon = AppIcons.Settings, shape = joinedRowShape(if (both) TilePlace.LAST else TilePlace.ONLY, outer = 20.dp))
                                }
                            }
                        }
                    }
                    is MealItem.Note -> Text(
                        item.text,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // While something is typed, what could be added to it.
        AnimatedVisibility(visible = draft.isNotBlank() && suggestions.isNotEmpty() && !loading, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(suggestions, key = { it.label }) { SuggestionChip(it, refining = true) { pick(it) } }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Joined.Gap),
        ) {
            // A photo of the meal, while there is nothing typed to make room for. All one row of joined tiles.
            val photos = draft.isBlank() && items.isNotEmpty()
            if (photos) {
                FoodRoundButton(
                    icon = AppIcons.Camera,
                    label = "Take a photo of the meal",
                    onClick = {
                        haptics.click()
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) openCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !loading,
                    size = 52.dp,
                    shape = joinedRowShape(TilePlace.FIRST, outer = 22.dp),
                )
                FoodRoundButton(AppIcons.Images, "Pick a photo of the meal", onClick = { haptics.click(); gallery.launch("image/*") }, enabled = !loading, size = 52.dp, shape = joinedRowShape(TilePlace.MIDDLE, outer = 22.dp))
            }
            FoodField(
                value = draft,
                onValueChange = { draft = it },
                hint = "What did you eat?",
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = joinedRowShape(if (photos) TilePlace.MIDDLE else TilePlace.FIRST, outer = 22.dp),
                singleLine = false,
                imeAction = ImeAction.Send,
                onImeAction = { send() },
                enabled = !loading,
            )
            FoodRoundButton(
                icon = AppIcons.ArrowUp,
                label = "Estimate",
                onClick = { send() },
                lit = draft.isNotBlank() && !loading,
                enabled = draft.isNotBlank() && !loading,
                size = 52.dp,
                shape = joinedRowShape(TilePlace.LAST, outer = 22.dp),
            )
        }
    }

    if (pickingAi) {
        AiPickerSheet(
            viewModel = aiSetup,
            onDismiss = { pickingAi = false },
            onManage = onAiSettings,
            note = "Estimates your meals. Photos need Claude, Gemini or OpenAI",
        )
    }
}

/** One of the three ways in besides typing: a tall tile with its glyph over its name. */
@Composable
private fun StartTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp), onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(HealthNutritionTone.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HealthNutritionTone, maxLines = 1)
    }
}

@Composable
private fun SuggestionChip(suggestion: DishSuggestion, refining: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(FoodWell)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggestion.iconRes != null) {
            Image(painter = painterResource(suggestion.iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(7.dp))
        } else if (refining) {
            Icon(AppIcons.Add, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(suggestion.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
    }
}

private val Portions = listOf(0.5f to "½", 1f to "1", 1.5f to "1½", 2f to "2")

/**
 * One answer: what it took the meal to be, calories and protein set large in their colours,
 * how sure it is, a portion to pick (the numbers follow it) and the button that logs it.
 * "Adjust" turns the two numbers into fields.
 */
@Composable
private fun EstimateCard(asked: String?, estimate: NutritionEstimate, logged: Boolean, onLog: (NutritionEstimate) -> Unit) {
    val haptics = rememberHaptics()
    var portion by remember(estimate) { mutableFloatStateOf(1f) }
    var adjusting by remember(estimate) { mutableStateOf(false) }
    var caloriesText by remember(estimate) { mutableStateOf(estimate.calories.toString()) }
    var proteinText by remember(estimate) { mutableStateOf(estimate.protein.toString()) }
    val calories = ((caloriesText.toIntOrNull() ?: estimate.calories) * portion).roundToInt()
    val protein = ((proteinText.toIntOrNull() ?: estimate.protein) * portion).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(FoodWell)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(estimate.foodName, style = HealthDisplay, fontSize = 20.sp, lineHeight = 24.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(estimate.servingDescription.takeIf { it.isNotBlank() }, asked?.takeIf { it != estimate.foodName }?.let { "from \"$it\"" })
                        .joinToString(" · "),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Confidence(estimate.confidence)
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (adjusting && !logged) {
            Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                FoodNumberTile("Calories", "kcal", NutritionCalories, caloriesText, { caloriesText = it }, Modifier.weight(1f), decimal = false, shape = joinedRowShape(TilePlace.FIRST, outer = 22.dp))
                FoodNumberTile("Protein", "g", NutritionProtein, proteinText, { proteinText = it }, Modifier.weight(1f), decimal = false, shape = joinedRowShape(TilePlace.LAST, outer = 22.dp))
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    HealthValueText(value = "$calories", unit = "kcal", valueSize = 40.sp, unitSize = 14.sp, color = NutritionCalories)
                    Text("Calories", fontSize = 12.sp, color = TextSecondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    HealthValueText(value = "$protein", unit = "g", valueSize = 40.sp, unitSize = 14.sp, color = NutritionProtein)
                    Text("Protein", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        estimate.notes.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (logged) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FoodTile)
                    .background(HealthNutritionTone.copy(alpha = 0.14f))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logged to today", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HealthNutritionTone)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portion", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                HealthSegmented(
                    options = Portions,
                    selected = portion,
                    onSelect = {
                        if (it != portion) haptics.tick()
                        portion = it
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap), verticalAlignment = Alignment.CenterVertically) {
                FoodGhostButton(if (adjusting) "Done" else "Adjust", onClick = { haptics.tick(); adjusting = !adjusting }, shape = joinedRowShape(TilePlace.FIRST, outer = 22.dp))
                FoodPrimaryButton(
                    text = "Log $calories kcal",
                    onClick = {
                        onLog(
                            estimate.copy(
                                calories = calories,
                                protein = protein,
                                servingDescription = if (portion == 1f) estimate.servingDescription else "${estimate.servingDescription} × $portion",
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.Check,
                    shape = joinedRowShape(TilePlace.LAST, outer = 22.dp),
                )
            }
        }
    }
}

/** How sure the estimate is, as three bars and a word: no percentage nobody worked out. */
@Composable
private fun Confidence(confidence: String) {
    val level = when (confidence.lowercase()) {
        "high" -> 3
        "low" -> 1
        else -> 2
    }
    val tone = if (level == 1) Warning else HealthNutritionTone
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(width = 4.dp, height = (7 + i * 3).dp)
                    .clip(CircleShape)
                    .background(if (i < level) tone else tone.copy(alpha = 0.22f)),
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(listOf("Rough", "Fair", "Sure")[level - 1], fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tone)
    }
}

private fun launchMealCamera(context: Context, onReady: (Uri, File) -> Unit, launcher: (Uri) -> Unit) {
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
