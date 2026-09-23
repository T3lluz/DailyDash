package com.macrotracker.ui.screens

import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.theme.NutritionCalories
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.LoadingSpinner
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.subScreenBottomPadding
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.chipFill
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CameraScanViewModel
import com.macrotracker.ui.viewmodel.LogSummary
import com.macrotracker.ui.viewmodel.ScanPhase
import java.io.ByteArrayOutputStream
import com.macrotracker.ui.theme.AppIcons

@Composable
fun CameraScanScreen(
    onNavigateBack: () -> Unit,
    onLogged: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    openGalleryOnStart: Boolean = false,
    viewModel: CameraScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val error by viewModel.error.collectAsState()
    val loggedEvent by viewModel.loggedEvent.collectAsState()
    val hasApiKey = viewModel.hasApiKey

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBase64 by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var didAutoOpenGallery by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    capturedBitmap = bitmap
                    capturedBase64 = bitmapToBase64(bitmap)
                    viewModel.setPhase(ScanPhase.PREVIEW)
                } else {
                    Toast.makeText(context, "Couldn't read that image.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't open gallery image.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (hasApiKey && !hasCameraPermission && !openGalleryOnStart) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(openGalleryOnStart, hasApiKey) {
        if (openGalleryOnStart && hasApiKey && !didAutoOpenGallery) {
            didAutoOpenGallery = true
            galleryLauncher.launch("image/*")
        }
    }

    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(loggedEvent) {
        if (loggedEvent) {
            viewModel.consumeLoggedEvent()
            Toast.makeText(context, "Food logged successfully", Toast.LENGTH_SHORT).show()
            onLogged()
        }
    }

    // Phase-aware system / predictive back (don't dump the whole route from preview).
    BackHandler(
        enabled = hasApiKey && (
            hasCameraPermission ||
                phase == ScanPhase.PREVIEW ||
                phase == ScanPhase.RESULT
            ),
    ) {
        when (phase) {
            ScanPhase.PREVIEW -> {
                if (scanning) {
                    viewModel.cancelAnalyze()
                } else {
                    capturedBitmap = null
                    capturedBase64 = null
                    if (hasCameraPermission) {
                        viewModel.setPhase(ScanPhase.CAMERA)
                    } else {
                        onNavigateBack()
                    }
                }
            }
            ScanPhase.RESULT -> {
                capturedBitmap = null
                capturedBase64 = null
                viewModel.resetForNewScan()
            }
            ScanPhase.CAMERA -> onNavigateBack()
        }
    }

    AnimatedContent(
        targetState = when {
            !hasApiKey -> "no_key"
            // Gallery picks can reach preview/result without camera permission.
            phase == ScanPhase.PREVIEW || phase == ScanPhase.RESULT -> phase.name
            !hasCameraPermission -> "permission"
            cameraError != null -> "camera_error"
            else -> phase.name
        },
        transitionSpec = {
            (fadeIn(MacroMotion.fadeTween()) + slideInVertically(MacroMotion.slideTween()) { it / 12 })
                .togetherWith(fadeOut(MacroMotion.fadeTween(150)) + slideOutVertically(MacroMotion.slideTween(150)) { -it / 16 })
                .using(SizeTransform(clip = false))
        },
        label = "cameraPhase",
    ) { target ->
        when (target) {
            "no_key" -> ApiKeyGate(
                onOpenSettings = onNavigateToAiSettings,
                onGoBack = onNavigateBack,
            )
            "permission" -> PermissionGate(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onPickGallery = { galleryLauncher.launch("image/*") },
                onGoBack = onNavigateBack,
            )
            "camera_error" -> CameraUnavailableGate(
                message = cameraError ?: "Camera unavailable",
                onRetry = { cameraError = null },
                onPickGallery = { galleryLauncher.launch("image/*") },
                onGoBack = onNavigateBack,
            )
            ScanPhase.CAMERA.name -> CameraPhase(
                onPhotoCaptured = { bitmap, base64 ->
                    capturedBitmap = bitmap
                    capturedBase64 = base64
                    viewModel.setPhase(ScanPhase.PREVIEW)
                },
                onPickGallery = { galleryLauncher.launch("image/*") },
                onGoBack = onNavigateBack,
                onCameraError = { cameraError = it },
                onCaptureError = {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                },
            )
            ScanPhase.PREVIEW.name -> PreviewPhase(
                bitmap = capturedBitmap,
                scanning = scanning,
                onScan = {
                    capturedBase64?.let { viewModel.analyzeImage(it) }
                },
                onRetake = {
                    viewModel.cancelAnalyze()
                    capturedBitmap = null
                    capturedBase64 = null
                    viewModel.setPhase(ScanPhase.CAMERA)
                },
                onCancelScan = { viewModel.cancelAnalyze() },
            )
            else -> ResultPhase(
                viewModel = viewModel,
                bitmap = capturedBitmap,
                onLog = { summary ->
                    viewModel.saveScannedFood(summary)
                },
                onScanAgain = {
                    capturedBitmap = null
                    capturedBase64 = null
                    viewModel.resetForNewScan()
                },
                onCancel = onNavigateBack,
            )
        }
    }
}

@Composable
private fun ApiKeyGate(onOpenSettings: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClankerCoachCard(
            message = "Connect Claude or add an AI key first — then I can read nutrition labels for you.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "AI login needed",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Label scanning uses your selected provider in Settings → AI.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        MacroButton(text = "Open AI settings", onClick = onOpenSettings)
        MacroButton(text = "Go back", onClick = onGoBack, variant = ButtonVariant.SECONDARY)
    }
}

@Composable
private fun PermissionGate(
    onRequestPermission: () -> Unit,
    onPickGallery: () -> Unit,
    onGoBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClankerCoachCard(
            message = "I need camera access to scan labels — or pick a photo from your gallery.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Camera access needed",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Point at the nutrition facts panel and I'll pull calories and protein.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        MacroButton(text = "Grant camera access", onClick = onRequestPermission)
        MacroButton(text = "Pick from gallery", onClick = onPickGallery, variant = ButtonVariant.SECONDARY)
        MacroButton(text = "Go back", onClick = onGoBack, variant = ButtonVariant.SECONDARY)
    }
}

@Composable
private fun CameraUnavailableGate(
    message: String,
    onRetry: () -> Unit,
    onPickGallery: () -> Unit,
    onGoBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera unavailable", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, fontSize = 15.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        MacroButton(text = "Try again", onClick = onRetry)
        MacroButton(text = "Pick from gallery", onClick = onPickGallery, variant = ButtonVariant.SECONDARY)
        MacroButton(text = "Go back", onClick = onGoBack, variant = ButtonVariant.SECONDARY)
    }
}

@Composable
private fun CameraPhase(
    onPhotoCaptured: (Bitmap, String) -> Unit,
    onPickGallery: () -> Unit,
    onGoBack: () -> Unit,
    onCameraError: (String) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setJpegQuality(45).build() }
    val previewView = remember { PreviewView(context) }
    val haptics = rememberHaptics()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(lifecycleOwner) {
        try {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
            // Restore torch if the user had it on before a rebinding.
            if (torchEnabled) {
                camera?.cameraControl?.enableTorch(true)
            }
        } catch (e: Exception) {
            onCameraError(e.message ?: "Could not open the camera on this device.")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(torchEnabled, camera) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            try {
                cam.cameraControl.enableTorch(torchEnabled)
            } catch (_: Exception) { }
        }
    }

    val flashScale by animateFloatAsState(
        targetValue = if (torchEnabled) 1.08f else 1f,
        animationSpec = MacroMotion.pressSpring(),
        label = "flashScale",
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onGoBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    "Scan nutrition label",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                IconButton(
                    onClick = {
                        if (hasFlash) {
                            haptics.tick()
                            torchEnabled = !torchEnabled
                        }
                    },
                    enabled = hasFlash,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(flashScale)
                        .background(
                            if (torchEnabled) Primary.copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = if (torchEnabled) AppIcons.Bolt else AppIcons.BoltOff,
                        contentDescription = if (torchEnabled) "Turn flash off" else "Turn flash on",
                        tint = if (hasFlash) Color.White else Color.White.copy(alpha = 0.35f),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(300.dp, 200.dp)
                        .border(3.dp, Primary, RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.height(14.dp))
                ClankerCoachCard(
                    message = "Line up the nutrition facts in that frame — calories & protein are what I hunt for.",
                    compact = true,
                    onDark = true,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                )
            }

            // Box, not a spaced Row: the shutter must sit dead centre whatever the Gallery chip's width.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp, start = 28.dp, end = 28.dp),
            ) {
                Text(
                    "Gallery",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            haptics.tick()
                            onPickGallery()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(4.dp, Color.White, CircleShape)
                        .clickable(onClickLabel = "Take photo") {
                            haptics.confirm()
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        image.close()
                                        if (bitmap == null) {
                                            onCaptureError("Couldn't process that photo. Try again.")
                                            return
                                        }
                                        val base64 = bitmapToBase64(bitmap)
                                        onPhotoCaptured(bitmap, base64)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        onCaptureError(exception.message ?: "Capture failed. Try again.")
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPhase(
    bitmap: Bitmap?,
    scanning: Boolean,
    onScan: () -> Unit,
    onRetake: () -> Unit,
    onCancelScan: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Image preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black))
        }

        // Bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            if (scanning) {
                ClankerCoachCard(
                    message = "One sec — I'm reading the label… calories and protein incoming.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    LoadingSpinner()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Clanker is analysing…",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    MacroButton(text = "Cancel", onClick = onCancelScan, variant = ButtonVariant.SECONDARY)
                }
            } else {
                ClankerCoachCard(
                    message = "Looks sharp! Hit scan and I'll pull the macros from this label.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Looking good?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Make sure the nutrition label is clear and fully visible.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp, top = 4.dp),
                )
                MacroButton(text = "Scan this photo", onClick = onScan)
                MacroButton(text = "Retake", onClick = onRetake, variant = ButtonVariant.SECONDARY)
            }
        }
    }
}

@Composable
private fun ClankerCoachCard(
    message: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDark: Boolean = false,
) {
    val bubbleBg = if (onDark) Color.Black.copy(alpha = 0.62f) else Surface
    val bubbleBorder = if (onDark) Color.White.copy(alpha = 0.22f) else Border
    val textColor = if (onDark) Color.White else TextPrimary
    val avatar = if (compact) 44.dp else 56.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(avatar)
                .clip(CircleShape)
                .background(Primary.chipFill()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Bot,
                contentDescription = "Clanker",
                tint = Primary,
                modifier = Modifier.size(avatar * 0.6f),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 12.dp),
        ) {
            Column {
                Text(
                    "Dr. Clanker",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Text(
                    message,
                    fontSize = if (compact) 13.sp else 14.sp,
                    color = textColor,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultPhase(
    viewModel: CameraScanViewModel,
    bitmap: Bitmap?,
    onLog: (LogSummary) -> Unit,
    onScanAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()

    // Collect all overrides
    val foodNameOverride by viewModel.foodNameOverride.collectAsState()
    val caloriesOverride by viewModel.caloriesOverride.collectAsState()
    val proteinOverride by viewModel.proteinOverride.collectAsState()
    val servingsOverride by viewModel.servingsOverride.collectAsState()
    val servingSizeOverride by viewModel.servingSizeOverride.collectAsState()
    val packageWeightOverride by viewModel.packageWeightOverride.collectAsState()
    val amountEaten by viewModel.amountEaten.collectAsState()
    val unitEaten by viewModel.unitEaten.collectAsState()

    // Calculate dynamic totals based on state
    val summary = viewModel.getLogSummary(
        foodNameStr = foodNameOverride,
        calsStr = caloriesOverride,
        protStr = proteinOverride,
        servsStr = servingsOverride,
        servSizeStr = servingSizeOverride,
        pkgWeightStr = packageWeightOverride,
        amtStr = amountEaten,
        unitStr = unitEaten
    ) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .subScreenBottomPadding(),
    ) {
        // Thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Scanned photo",
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop,
            )
        }

        ClankerCoachCard(
            message = "Got it! Tweak anything that looks off, set how much you ate, then log it.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )

        // Result card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Nutrition scan results", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(summary.foodName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.padding(bottom = 20.dp))

                // Per serving
                Text("PER SERVING", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroPill("${summary.caloriesPerServing}", "kcal", NutritionCalories, Modifier.weight(1f))
                    MacroPill("${summary.proteinPerServing}g", "protein", NutritionProtein, Modifier.weight(1f))
                }

                // Package meta
                if (summary.packageWeightGrams > 0 || summary.servingSizeGrams > 0 || summary.servingsPerContainer > 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (summary.packageWeightGrams > 0) MetaChip("Package: ${summary.packageWeightGrams}g")
                        if (summary.servingSizeGrams > 0) MetaChip("Serving: ${summary.servingSizeGrams}g")
                        if (summary.servingsPerContainer > 0 && summary.packageWeightGrams <= 0) MetaChip("Servings: ${formatDoubleOrInt(summary.servingsPerContainer)}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Amount Eaten (Dynamic unit selection) ───
                Text("AMOUNT EATEN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .background(Surface, RoundedCornerShape(14.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                haptics.tick()
                                val v = amountEaten.toDoubleOrNull() ?: 1.0
                                val step = if (unitEaten in listOf("g", "ml")) 10.0 else 0.5
                                viewModel.setAmountEaten(formatDoubleOrInt(maxOf(0.0, v - step)))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("−", fontSize = 24.sp, color = Primary, fontWeight = FontWeight.Light)
                    }
                    
                    MacroTextField(
                        value = amountEaten,
                        onValueChange = { viewModel.setAmountEaten(it) },
                        placeholder = "1",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).padding(bottom = 0.dp),
                        textAlignment = TextAlign.Center,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Primary,
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .clickable {
                                haptics.tick()
                                val v = amountEaten.toDoubleOrNull() ?: 1.0
                                val step = if (unitEaten in listOf("g", "ml")) 10.0 else 0.5
                                viewModel.setAmountEaten(formatDoubleOrInt(v + step))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("+", fontSize = 24.sp, color = Primary, fontWeight = FontWeight.Light)
                    }
                    
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Border))
                    
                    var unitExpanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clickable { unitExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(unitEaten, fontSize = 15.sp, color = Primary, fontWeight = FontWeight.Medium)
                            Icon(AppIcons.ChevronDown, contentDescription = "Select unit", tint = Primary)
                        }
                        
                        DropdownMenu(
                            expanded = unitExpanded, 
                            onDismissRequest = { unitExpanded = false },
                            modifier = Modifier.background(Surface)
                        ) {
                            viewModel.units.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u, color = TextPrimary, fontSize = 15.sp) },
                                    onClick = {
                                        viewModel.setUnitEaten(u)
                                        unitExpanded = false
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = if (u == unitEaten) Primary else TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                // ─── Follow-up fields for overrides & missing data ───
                Spacer(modifier = Modifier.height(20.dp))

                val needsServingSize = unitEaten in listOf("g", "kg", "ml", "dl", "L")
                val needsServingsInPackage = unitEaten == "packages"
                var showLabelEdit by rememberSaveable { mutableStateOf(false) }

                Text(
                    if (showLabelEdit) "Hide label data" else "Edit label data",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable { showLabelEdit = !showLabelEdit },
                )

                if (showLabelEdit || foodNameOverride.isBlank() || (caloriesOverride.toDoubleOrNull() ?: 0.0) <= 0) {
                    FollowUpField("Product name", "Enter product name", foodNameOverride, { viewModel.setFoodNameOverride(it) })
                    FollowUpField("Calories per serving", "e.g. 180", caloriesOverride, { viewModel.setCaloriesOverride(it) }, KeyboardType.Decimal)
                    FollowUpField("Protein per serving (g)", "e.g. 12", proteinOverride, { viewModel.setProteinOverride(it) }, KeyboardType.Decimal)
                    FollowUpField(
                        if (needsServingSize && summary.servingSizeGrams <= 0) "Serving size (g) *" else "Serving size (g)",
                        "e.g. 85",
                        servingSizeOverride,
                        { viewModel.setServingSizeOverride(it) },
                        KeyboardType.Decimal,
                    )
                    FollowUpField(
                        if (needsServingsInPackage && summary.servingsPerContainer <= 0) "Servings in package *" else "Servings in package",
                        "e.g. 4",
                        servingsOverride,
                        { viewModel.setServingsOverride(it) },
                        KeyboardType.Decimal,
                    )
                    FollowUpField(
                        "Package weight (g)",
                        "e.g. 340",
                        packageWeightOverride,
                        { viewModel.setPackageWeightOverride(it) },
                        KeyboardType.Decimal,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Total to log (Animated) ───
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.09f)),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL TO LOG", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedContent(
                                    targetState = summary.loggedCalories,
                                    transitionSpec = {
                                        MacroMotion.numberTick(up = targetState > initialState)
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "caloriesAnimation"
                                ) { targetCount ->
                                    Text("$targetCount", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Primary)
                                }
                                Text("kcal", fontSize = 13.sp, color = TextSecondary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(50.dp).background(Border))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedContent(
                                    targetState = summary.loggedProtein,
                                    transitionSpec = {
                                        MacroMotion.numberTick(up = targetState > initialState)
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "proteinAnimation"
                                ) { targetCount ->
                                    Text("${targetCount}g", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Secondary)
                                }
                                Text("protein", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        Text(
                            "(${summary.caloriesPerServing} kcal × ${"%.2f".format(summary.multiplier)} factor)",
                            fontSize = 12.sp, color = TextSecondary, fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }

        // Action buttons
        MacroButton(
            text = "Log to today",
            onClick = {
                val missingRequired = mutableListOf<String>()
                if (summary.foodName.isBlank() || summary.foodName.lowercase() == "scanned food") missingRequired.add("product name")
                if (summary.caloriesPerServing <= 0) missingRequired.add("calories per serving")
                
                val needsServingSize = unitEaten in listOf("g", "kg", "ml", "dl", "L")
                if (needsServingSize && summary.servingSizeGrams <= 0) missingRequired.add("serving size")
                
                val needsServingsInPackage = unitEaten == "packages"
                if (needsServingsInPackage && summary.servingsPerContainer <= 0) missingRequired.add("servings in package")

                if (missingRequired.isNotEmpty()) {
                    haptics.reject()
                    Toast.makeText(context, "Please fill: ${missingRequired.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                } else {
                    haptics.confirm()
                    onLog(summary)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroButton(
                text = "Scan again",
                onClick = onScanAgain,
                variant = ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            MacroButton(
                text = "Cancel",
                onClick = onCancel,
                variant = ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MacroPill(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.chipFill())
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier
            .border(1.dp, Border, CircleShape)
            .background(Background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun FollowUpField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        MacroTextField(value = value, onValueChange = onValueChange, placeholder = placeholder, keyboardType = keyboardType)
    }
}

private fun formatDoubleOrInt(v: Double): String {
    return if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 45, stream)
    val bytes = stream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
