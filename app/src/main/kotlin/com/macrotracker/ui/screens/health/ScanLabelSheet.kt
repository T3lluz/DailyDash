package com.macrotracker.ui.screens.health

import com.macrotracker.ui.components.joinedRowShape
import com.macrotracker.ui.components.TilePlace
import com.macrotracker.ui.components.joinedGridShape
import com.macrotracker.ui.components.Joined
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.preparePhoto
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CameraScanViewModel
import com.macrotracker.ui.viewmodel.LogSummary
import com.macrotracker.ui.viewmodel.ScanPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Scanning a nutrition label, as a card that slides up over Food. It opens on the camera in
 * a rounded window with a frame to fit the label in; the shot is shown to check, then read
 * by the AI, and what it read comes back as numbers to set an amount against and log. Every
 * step is the same card, so a swipe down leaves from any of them.
 */
@Composable
fun ScanLabelSheet(
    onDismiss: () -> Unit,
    onAiSettings: () -> Unit,
    viewModel: CameraScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val error by viewModel.error.collectAsState()
    val loggedEvent by viewModel.loggedEvent.collectAsState()
    val hasApiKey = viewModel.hasApiKey
    val scope = rememberCoroutineScope()

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // The card is the app's one scanner, so it starts clean each time it slides up.
    LaunchedEffect(Unit) { viewModel.resetForNewScan() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Scaled and turned upright off the main thread; a full-size decode could run out of memory.
        scope.launch {
            val picked = withContext(Dispatchers.Default) { preparePhoto(context, uri) }
            if (picked != null) {
                photo = picked.bitmap
                photoBase64 = picked.base64
                viewModel.setPhase(ScanPhase.PREVIEW)
            } else {
                Toast.makeText(context, "Couldn't read that image.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) { if (hasApiKey && !hasCamera) permission.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(error) { error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    LaunchedEffect(loggedEvent) {
        if (loggedEvent) {
            viewModel.consumeLoggedEvent()
            onDismiss()
        }
    }
    fun retake() {
        viewModel.cancelAnalyze()
        photo = null
        photoBase64 = null
        viewModel.resetForNewScan()
    }

    val step = when {
        !hasApiKey -> "no_key"
        // A gallery pick reaches the check and the result without the camera.
        phase == ScanPhase.PREVIEW || phase == ScanPhase.RESULT -> phase.name
        !hasCamera -> "permission"
        cameraError != null -> "camera_error"
        else -> phase.name
    }
    FoodSheet(
        title = "Scan a label",
        subtitle = when (step) {
            ScanPhase.CAMERA.name -> "Fit the nutrition facts in the frame"
            ScanPhase.PREVIEW.name -> if (scanning) "Reading the label…" else "Sharp and all of it in view?"
            ScanPhase.RESULT.name -> "Set how much you had, then log it"
            else -> null
        },
        onDismiss = onDismiss,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(MacroMotion.fadeTween(200)) togetherWith fadeOut(MacroMotion.fadeTween(120)) },
            label = "scanStep",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { shown ->
            when (shown) {
                "no_key" -> Gate(
                    icon = AppIcons.Sparkles,
                    title = "An AI has to read it",
                    line = "Reading a label uses the AI picked in Settings. Sign in to one, or add a key, and come back.",
                ) {
                    FoodPrimaryButton("Open AI settings", onClick = onAiSettings)
                }
                "permission" -> Gate(
                    icon = AppIcons.Camera,
                    title = "The camera is off for DailyDash",
                    line = "Allow it to point at a label, or pick a photo of one you already took.",
                ) {
                    FoodPrimaryButton("Allow the camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
                    FoodGhostButton("Pick a photo", onClick = { gallery.launch("image/*") }, icon = AppIcons.Images, modifier = Modifier.fillMaxWidth())
                }
                "camera_error" -> Gate(
                    icon = AppIcons.Camera,
                    title = "The camera didn't open",
                    line = cameraError ?: "Something else may be using it.",
                ) {
                    FoodPrimaryButton("Try again", onClick = { cameraError = null })
                    FoodGhostButton("Pick a photo", onClick = { gallery.launch("image/*") }, icon = AppIcons.Images, modifier = Modifier.fillMaxWidth())
                }
                ScanPhase.CAMERA.name -> Viewfinder(
                    onShot = { bitmap, base64 ->
                        photo = bitmap
                        photoBase64 = base64
                        viewModel.setPhase(ScanPhase.PREVIEW)
                    },
                    onGallery = { gallery.launch("image/*") },
                    onCameraError = { cameraError = it },
                )
                ScanPhase.PREVIEW.name -> Check(
                    photo = photo,
                    scanning = scanning,
                    onRead = { photoBase64?.let { viewModel.analyzeImage(it) } },
                    onRetake = { retake() },
                    onStop = { viewModel.cancelAnalyze() },
                )
                else -> LabelResult(viewModel = viewModel, photo = photo, onLog = { viewModel.saveScannedFood(it) }, onAgain = { retake() })
            }
        }
    }
}

/** A step that can't go on by itself: what is missing, in a sentence, and the way past it. */
@Composable
private fun Gate(icon: ImageVector, title: String, line: String, actions: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(HealthNutritionTone.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(title, style = HealthDisplay, fontSize = 22.sp, lineHeight = 27.sp, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(line, fontSize = 14.sp, lineHeight = 20.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/**
 * The camera in a rounded window, with a frame's four corners to fit the label in. Under it
 * the gallery, the shutter and the torch. The camera is let go as soon as the card moves on.
 */
@Composable
private fun Viewfinder(onShot: (Bitmap, String) -> Unit, onGallery: () -> Unit, onCameraError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val capture = remember { ImageCapture.Builder().setJpegQuality(45).build() }
    // A TextureView, so the picture can be clipped to the window's round corners.
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(lifecycleOwner) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val feed = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, feed, capture)
        } catch (e: Exception) {
            onCameraError(e.message ?: "Could not open the camera on this device.")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            // Leaving the viewfinder releases the camera, which otherwise stayed bound, hot
            // and draining, until the whole card closed.
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
    LaunchedEffect(torch, camera) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) runCatching { cam.cameraControl.enableTorch(torch) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black),
        ) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            // The frame: four corners, wide as a label is.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width * 0.8f
                val h = minOf(size.height * 0.5f, w * 0.72f)
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                val arm = 26.dp.toPx()
                val stroke = 4.dp.toPx()
                listOf(
                    Offset(left, top) to (1f to 1f),
                    Offset(left + w, top) to (-1f to 1f),
                    Offset(left, top + h) to (1f to -1f),
                    Offset(left + w, top + h) to (-1f to -1f),
                ).forEach { (corner, dir) ->
                    drawLine(HealthNutritionTone, corner, Offset(corner.x + arm * dir.first, corner.y), stroke, cap = StrokeCap.Round)
                    drawLine(HealthNutritionTone, corner, Offset(corner.x, corner.y + arm * dir.second), stroke, cap = StrokeCap.Round)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // A Box, not a spaced Row: the shutter sits dead centre whatever stands beside it.
        Box(modifier = Modifier.fillMaxWidth().height(78.dp)) {
            FoodGhostButton(
                "Gallery",
                onClick = {
                    haptics.tick()
                    onGallery()
                },
                icon = AppIcons.Images,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(HealthNutritionTone.copy(alpha = 0.25f))
                    .clickable(enabled = !busy, onClickLabel = "Take photo") {
                        haptics.confirm()
                        busy = true
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)
                                    val rotation = image.imageInfo.rotationDegrees
                                    image.close()
                                    scope.launch {
                                        val shot = withContext(Dispatchers.Default) { preparePhoto(bytes, rotation) }
                                        busy = false
                                        if (shot == null) {
                                            Toast.makeText(context, "Couldn't process that photo. Try again.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onShot(shot.bitmap, shot.base64)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    Toast.makeText(context, exception.message ?: "Capture failed. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(HealthNutritionTone))
            }
            FoodRoundButton(
                icon = if (torch) AppIcons.Bolt else AppIcons.BoltOff,
                label = if (torch) "Turn the torch off" else "Turn the torch on",
                onClick = {
                    haptics.tick()
                    torch = !torch
                },
                lit = torch,
                enabled = hasFlash,
                size = 48.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** The shot, to check before it is read; then the wait while it is. */
@Composable
private fun Check(photo: Bitmap?, scanning: Boolean, onRead: () -> Unit, onRetake: () -> Unit, onStop: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black),
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "The photo of the label",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (scanning) {
            RoomLoading(tone = HealthNutritionTone, bars = 2)
            FoodGhostButton("Stop", onClick = onStop, icon = AppIcons.Close, modifier = Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap), verticalAlignment = Alignment.CenterVertically) {
                FoodGhostButton("Retake", onClick = onRetake, icon = AppIcons.Refresh, shape = joinedRowShape(TilePlace.FIRST, outer = 22.dp))
                FoodPrimaryButton("Read this label", onClick = onRead, modifier = Modifier.weight(1f), icon = AppIcons.Sparkles, shape = joinedRowShape(TilePlace.LAST, outer = 22.dp))
            }
        }
    }
}

private fun String.asDecimal(): Double? = replace(',', '.').toDoubleOrNull()

private fun trim(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

/**
 * What the label said: the product, a serving's calories and protein, how much was had (a
 * number to step or type, in the unit picked under it) and what that comes to, set large.
 * "Fix what it read" opens the label's own numbers to correct.
 */
@Composable
private fun LabelResult(viewModel: CameraScanViewModel, photo: Bitmap?, onLog: (LogSummary) -> Unit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val name by viewModel.foodNameOverride.collectAsState()
    val calories by viewModel.caloriesOverride.collectAsState()
    val protein by viewModel.proteinOverride.collectAsState()
    val servings by viewModel.servingsOverride.collectAsState()
    val servingSize by viewModel.servingSizeOverride.collectAsState()
    val packageWeight by viewModel.packageWeightOverride.collectAsState()
    val amount by viewModel.amountEaten.collectAsState()
    val unit by viewModel.unitEaten.collectAsState()
    val summary = viewModel.getLogSummary(
        foodNameStr = name,
        calsStr = calories,
        protStr = protein,
        servsStr = servings,
        servSizeStr = servingSize,
        pkgWeightStr = packageWeight,
        amtStr = amount,
        unitStr = unit,
    ) ?: return

    val byWeight = unit in listOf("g", "kg", "ml", "dl", "L")
    val byPackage = unit == "packages"
    // What has to be there before it can be logged.
    val missing = buildList {
        if (summary.foodName.isBlank() || summary.foodName.lowercase() == "scanned food") add("the product's name")
        if (summary.caloriesPerServing <= 0) add("calories per serving")
        if (byWeight && summary.servingSizeGrams <= 0) add("the serving size")
        if (byPackage && summary.servingsPerContainer <= 0) add("servings in the package")
    }
    var fixing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.foodName.ifBlank { "Unnamed" }, style = HealthDisplay, fontSize = 22.sp, lineHeight = 26.sp, color = TextPrimary, maxLines = 2)
                Text(
                    buildList {
                        add("${summary.caloriesPerServing} kcal and ${trim(summary.proteinPerServing)} g protein a serving")
                        if (summary.servingSizeGrams > 0) add("${summary.servingSizeGrams} g")
                    }.joinToString(" · "),
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text("How much did you have?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val step = if (unit in listOf("g", "ml")) 10.0 else 0.5
            Stepper("−", "Less") {
                haptics.tick()
                viewModel.setAmountEaten(trim(maxOf(0.0, (amount.asDecimal() ?: 1.0) - step)))
            }
            FoodNumberTile(
                label = "Amount",
                unit = unit,
                tone = HealthNutritionTone,
                value = amount,
                onValueChange = { viewModel.setAmountEaten(it) },
                modifier = Modifier.weight(1f),
            )
            Stepper("+", "More") {
                haptics.tick()
                viewModel.setAmountEaten(trim((amount.asDecimal() ?: 1.0) + step))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            viewModel.units.forEach { option ->
                val on = option == unit
                FoodChip(
                    text = option,
                    onClick = {
                        if (!on) haptics.tick()
                        viewModel.setUnitEaten(option)
                    },
                    tone = if (on) Color.Black else TextPrimary,
                    fill = if (on) HealthNutritionTone else FoodWell,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(FoodWell)
                .padding(18.dp),
        ) {
            Total(summary.loggedCalories, "kcal", "Calories", NutritionCalories, Modifier.weight(1f))
            Total(summary.loggedProtein, "g", "Protein", NutritionProtein, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
        val mustFix = name.isBlank() || (calories.asDecimal() ?: 0.0) <= 0
        FoodGhostButton(
            if (fixing) "Done fixing" else "Fix what it read",
            onClick = {
                haptics.tick()
                fixing = !fixing
            },
            modifier = Modifier.fillMaxWidth(),
            tone = TextSecondary,
        )
        AnimatedVisibility(visible = fixing || mustFix, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                FoodField(value = name, onValueChange = { viewModel.setFoodNameOverride(it) }, hint = "The product's name", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                // the label's numbers as one grid of joined tiles: two by two, the weight across the foot
                val g = 22.dp
                Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                    FoodNumberTile("Calories a serving", "kcal", NutritionCalories, calories, { viewModel.setCaloriesOverride(it) }, Modifier.weight(1f), shape = joinedGridShape(0, 3, 0, 2, outer = g))
                    FoodNumberTile("Protein a serving", "g", NutritionProtein, protein, { viewModel.setProteinOverride(it) }, Modifier.weight(1f), shape = joinedGridShape(0, 3, 1, 2, outer = g))
                }
                Spacer(Modifier.height(Joined.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                    FoodNumberTile("Serving size", "g", TextSecondary, servingSize, { viewModel.setServingSizeOverride(it) }, Modifier.weight(1f), shape = joinedGridShape(1, 3, 0, 2, outer = g))
                    FoodNumberTile("In the package", "servings", TextSecondary, servings, { viewModel.setServingsOverride(it) }, Modifier.weight(1f), shape = joinedGridShape(1, 3, 1, 2, outer = g))
                }
                Spacer(Modifier.height(Joined.Gap))
                FoodNumberTile("Package weight", "g", TextSecondary, packageWeight, { viewModel.setPackageWeightOverride(it) }, Modifier.fillMaxWidth(), shape = joinedGridShape(2, 3, 0, 1, outer = g))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (missing.isNotEmpty()) {
            Text(
                "Still needs ${missing.joinToString(", ")}.",
                fontSize = 13.sp,
                color = TextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Joined.Gap), verticalAlignment = Alignment.CenterVertically) {
            FoodGhostButton("Scan again", onClick = onAgain, icon = AppIcons.Refresh, shape = joinedRowShape(TilePlace.FIRST, outer = 22.dp))
            FoodPrimaryButton(
                text = "Log ${summary.loggedCalories} kcal",
                onClick = {
                    if (summary.multiplier <= 0.0) {
                        // An amount of 0 (or one that didn't read) would log 0 kcal.
                        Toast.makeText(context, "Enter how much you had", Toast.LENGTH_SHORT).show()
                    } else {
                        haptics.confirm()
                        onLog(summary)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = missing.isEmpty(),
                icon = AppIcons.Check,
                shape = joinedRowShape(TilePlace.LAST, outer = 22.dp),
            )
        }
    }
}

/** A square minus or plus beside the amount. */
@Composable
private fun Stepper(sign: String, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(FoodTile)
            .background(FoodWell)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, style = HealthDisplay, fontSize = 26.sp, color = HealthNutritionTone)
    }
}

/** One of the two totals: the number rolls up or down to its new value as the amount changes. */
@Composable
private fun Total(value: Int, unit: String, label: String, tone: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        AnimatedContent(
            targetState = value,
            transitionSpec = { MacroMotion.numberTick(up = targetState > initialState).using(SizeTransform(clip = false)) },
            label = "scanTotal",
        ) { shown ->
            HealthValueText(value = "$shown", unit = unit, valueSize = 40.sp, unitSize = 14.sp, color = tone)
        }
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}
