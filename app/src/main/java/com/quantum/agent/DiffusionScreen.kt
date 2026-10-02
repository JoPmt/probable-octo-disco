package com.quantum.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiffusionScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val diffusionEngine = remember { DiffusionEngine(context) }
    val diffState by diffusionEngine.state.collectAsStateWithLifecycle()
    val diffConfig = config.diffusionConfig

    // Inpainting brush strokes state
    val inpaintStrokes = remember { mutableStateListOf<BrushStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Gallery Picker for input image
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    diffusionEngine.setInputSourceBitmap(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Modal dialog / metadata viewer state
    var selectedInspectImage by remember { mutableStateOf<GeneratedDiffusionImage?>(null) }
    var showAdvancedEngineSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
    ) {
        // Mode Selector Bar (TXT2IMG, IMG2IMG, INPAINT, OUTPAINT, CONTROLNET, UPSCALE)
        DiffusionModeSelectorBar(
            activeMode = diffConfig.activeMode,
            onSelectMode = { mode ->
                val newConfig = diffConfig.copy(activeMode = mode)
                onConfigChange(config.copy(diffusionConfig = newConfig))
            }
        )

        // Main Scrollable Studio Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Viewport & Interactive Latent / Mask Canvas
            item {
                DiffusionViewportSection(
                    diffState = diffState,
                    diffConfig = diffConfig,
                    inpaintStrokes = inpaintStrokes,
                    currentStrokePoints = currentStrokePoints,
                    onStrokePointAdded = { currentStrokePoints = currentStrokePoints + it },
                    onStrokeFinished = {
                        if (currentStrokePoints.isNotEmpty()) {
                            inpaintStrokes.add(BrushStroke(currentStrokePoints, diffConfig.inpaintBrushSize))
                            currentStrokePoints = emptyList()
                            // Sync bitmap mask to engine
                            val maskBmp = renderInpaintMaskToBitmap(512, 512, inpaintStrokes)
                            diffusionEngine.setInpaintMaskBitmap(maskBmp)
                        }
                    },
                    onClearMask = {
                        inpaintStrokes.clear()
                        currentStrokePoints = emptyList()
                        diffusionEngine.setInpaintMaskBitmap(null)
                    },
                    onGenerate = { diffusionEngine.generate(diffConfig) },
                    onCancel = { diffusionEngine.cancelGeneration() },
                    onPickImage = { galleryLauncher.launch("image/*") },
                    onToggleCompare = { diffusionEngine.toggleCompareOriginal() },
                    onShareCurrent = {
                        diffState.activeResultImage?.let { diffusionEngine.shareImage(it) }
                    }
                )
            }

            // Prompt & Conditioning Studio
            item {
                DiffusionPromptStudioCard(
                    diffConfig = diffConfig,
                    onUpdateConfig = { updated ->
                        onConfigChange(config.copy(diffusionConfig = updated))
                    },
                    onApplyPreset = { preset ->
                        val updated = diffConfig.copy(
                            prompt = preset.prompt,
                            negativePrompt = preset.negativePrompt,
                            sampler = preset.recommendedSampler,
                            cfgScale = preset.recommendedCfg,
                            inferenceSteps = preset.recommendedSteps
                        )
                        onConfigChange(config.copy(diffusionConfig = updated))
                    }
                )
            }

            // Generation Hyperparameters (Architecture, Quant, Sampler, Steps, CFG, Seed)
            item {
                DiffusionHyperparametersCard(
                    diffConfig = diffConfig,
                    onUpdateConfig = { updated ->
                        onConfigChange(config.copy(diffusionConfig = updated))
                    }
                )
            }

            // Mode Specific Controls (ControlNet, Outpaint, Inpaint, Upscale)
            when (diffConfig.activeMode) {
                DiffusionMode.CONTROLNET -> {
                    item {
                        ControlNetConditioningCard(
                            diffConfig = diffConfig,
                            conditionBitmap = diffState.controlNetConditionBitmap,
                            onUpdateConfig = { updated ->
                                onConfigChange(config.copy(diffusionConfig = updated))
                            }
                        )
                    }
                }
                DiffusionMode.OUTPAINT -> {
                    item {
                        OutpaintingSettingsCard(
                            diffConfig = diffConfig,
                            onUpdateConfig = { updated ->
                                onConfigChange(config.copy(diffusionConfig = updated))
                            }
                        )
                    }
                }
                DiffusionMode.INPAINT -> {
                    item {
                        InpaintingBrushSettingsCard(
                            diffConfig = diffConfig,
                            strokeCount = inpaintStrokes.size,
                            onUpdateConfig = { updated ->
                                onConfigChange(config.copy(diffusionConfig = updated))
                            },
                            onClearMask = {
                                inpaintStrokes.clear()
                                currentStrokePoints = emptyList()
                                diffusionEngine.setInpaintMaskBitmap(null)
                            }
                        )
                    }
                }
                DiffusionMode.UPSCALE -> {
                    item {
                        UpscalingSettingsCard(
                            diffConfig = diffConfig,
                            onUpdateConfig = { updated ->
                                onConfigChange(config.copy(diffusionConfig = updated))
                            }
                        )
                    }
                }
                else -> {}
            }

            // stable-diffusion.cpp Hardware & Backend Optimization
            item {
                DiffusionHardwareBackendCard(
                    diffConfig = diffConfig,
                    peakVramMb = diffState.peakVramMb,
                    onUpdateConfig = { updated ->
                        onConfigChange(config.copy(diffusionConfig = updated))
                    }
                )
            }

            // Telemetry & GGML Log Terminal
            item {
                DiffusionLogTerminalCard(
                    logs = diffState.logs,
                    statusMessage = diffState.statusMessage
                )
            }

            // Output Gallery & Stored Renderings
            item {
                DiffusionHistoryGalleryCard(
                    history = diffState.generatedHistory,
                    onSelect = { item ->
                        diffusionEngine.selectActiveHistoryItem(item)
                        selectedInspectImage = item
                    },
                    onShare = { item -> diffusionEngine.shareImage(item) },
                    onDelete = { item -> diffusionEngine.deleteImage(item) }
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// VIEWPORT SECTION & LIVE LATENT CANVAS
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionViewportSection(
    diffState: DiffusionEngineState,
    diffConfig: DiffusionConfig,
    inpaintStrokes: List<BrushStroke>,
    currentStrokePoints: List<Offset>,
    onStrokePointAdded: (Offset) -> Unit,
    onStrokeFinished: () -> Unit,
    onClearMask: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onPickImage: () -> Unit,
    onToggleCompare: () -> Unit,
    onShareCurrent: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Viewport Top Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (diffState.isGenerating) Color(0xFF00FF66) else Color(0xFF00E5FF))
                    )
                    Text(
                        text = if (diffState.isGenerating) "INFERENCING" else "DIFFUSION VIEWPORT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (diffState.isGenerating) Color(0xFF00FF66) else Color(0xFF00E5FF)
                    )
                    if (diffState.isGenerating) {
                        Text(
                            text = "• Step ${diffState.currentStep}/${diffState.totalSteps}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFD600)
                        )
                        Text(
                            text = "• ${diffState.currentStepLatencyMs}ms/step",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8E95A5)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF1F2430),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${diffConfig.modelArchitecture.baseFamily} [${diffConfig.quantization.typeCode}]",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Viewport Screen Display (Latent Preview, Inpaint Canvas, Result or Empty State)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        when {
                            diffConfig.width > diffConfig.height -> (diffConfig.width.toFloat() / diffConfig.height.toFloat()).coerceIn(1.2f, 1.8f)
                            diffConfig.height > diffConfig.width -> (diffConfig.width.toFloat() / diffConfig.height.toFloat()).coerceIn(0.6f, 0.9f)
                            else -> 1.0f
                        }
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Determine what bitmap to show in viewport
                val displayBitmap: Bitmap? = when {
                    diffState.isGenerating && diffState.currentLatentPreview != null -> diffState.currentLatentPreview
                    diffState.isComparingOriginal && diffState.inputSourceBitmap != null -> diffState.inputSourceBitmap
                    diffState.activeResultImage?.bitmap != null -> diffState.activeResultImage?.bitmap
                    diffState.inputSourceBitmap != null -> diffState.inputSourceBitmap
                    else -> null
                }

                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Diffusion Viewport Output",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Empty Standby Canvas
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF2E3440),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "stable-diffusion.cpp Engine Standby",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E95A5)
                        )
                        Text(
                            text = "Press 'GENERATE' to launch neural diffusion pipeline",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Interactive Inpainting Brush Touch Overlay (Only in INPAINT mode)
                if (diffConfig.activeMode == DiffusionMode.INPAINT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(diffConfig.inpaintBrushSize) {
                                detectDragGestures(
                                    onDragStart = { offset -> onStrokePointAdded(offset) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        onStrokePointAdded(change.position)
                                    },
                                    onDragEnd = { onStrokeFinished() }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw completed inpainting mask strokes
                            inpaintStrokes.forEach { stroke ->
                                for (i in 0 until stroke.points.size - 1) {
                                    drawLine(
                                        color = Color(0x99FF5252),
                                        start = stroke.points[i],
                                        end = stroke.points[i + 1],
                                        strokeWidth = stroke.strokeWidth,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                            // Draw active currently dragged stroke
                            if (currentStrokePoints.size > 1) {
                                for (i in 0 until currentStrokePoints.size - 1) {
                                    drawLine(
                                        color = Color(0xCC00FF66),
                                        start = currentStrokePoints[i],
                                        end = currentStrokePoints[i + 1],
                                        strokeWidth = diffConfig.inpaintBrushSize,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                // Viewport HUD Overlays (Top / Bottom)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top HUD Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = Color(0xCC0D0E11),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${diffConfig.activeMode.title.uppercase()} • ${diffConfig.width}x${diffConfig.height}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF66),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        if (diffState.isGenerating) {
                            Surface(
                                color = Color(0xCC0D0E11),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "ETA: ${String.format(Locale.US, "%.1f", diffState.currentEtaSeconds)}s",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD600),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Bottom HUD Status Line & Progress
                    if (diffState.isGenerating) {
                        Column {
                            LinearProgressIndicator(
                                progress = { diffState.currentProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF00FF66),
                                trackColor = Color(0xFF1F2430)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diffState.statusMessage,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color(0x99000000), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Generate / Cancel, Pick Source Image, Compare, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action Button (GENERATE or CANCEL)
                if (diffState.isGenerating) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A1B1B),
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_diffusion_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Cancel Diffusion",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STOP",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onGenerate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF66),
                            contentColor = Color(0xFF0D0E11)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_diffusion_generate")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generate",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GENERATE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Pick input image (for img2img, inpaint, outpaint, controlnet, upscale)
                if (diffConfig.activeMode != DiffusionMode.TXT2IMG) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier
                            .background(Color(0xFF1F2430), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Pick Input Image",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Compare original button (for img2img, inpaint, upscale)
                if (diffState.inputSourceBitmap != null && diffState.activeResultImage != null) {
                    IconButton(
                        onClick = onToggleCompare,
                        modifier = Modifier
                            .background(if (diffState.isComparingOriginal) Color(0xFF1B2A1E) else Color(0xFF1F2430), RoundedCornerShape(6.dp))
                            .border(1.dp, if (diffState.isComparingOriginal) Color(0xFF00FF66) else Color(0xFF2E3440), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare Original",
                            tint = if (diffState.isComparingOriginal) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Inpainting Clear Mask button
                if (diffConfig.activeMode == DiffusionMode.INPAINT && inpaintStrokes.isNotEmpty()) {
                    IconButton(
                        onClick = onClearMask,
                        modifier = Modifier
                            .background(Color(0xFF1F2430), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Inpaint Mask",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Share active image
                if (diffState.activeResultImage != null) {
                    IconButton(
                        onClick = onShareCurrent,
                        modifier = Modifier
                            .background(Color(0xFF1F2430), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Generated Image",
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// MODE SELECTOR BAR
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionModeSelectorBar(
    activeMode: DiffusionMode,
    onSelectMode: (DiffusionMode) -> Unit
) {
    val modes = DiffusionMode.values()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14171E))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(modes) { mode ->
            val isSelected = activeMode == mode
            Surface(
                color = if (isSelected) Color(0xFF00FF66) else Color(0xFF1A1E26),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00FF66) else Color(0xFF2A303C)),
                modifier = Modifier
                    .clickable { onSelectMode(mode) }
                    .testTag("diffusion_mode_${mode.name}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = mode.iconEmoji,
                        fontSize = 12.sp
                    )
                    Text(
                        text = mode.title,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF0D0E11) else Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// PROMPT STUDIO & PRESETS
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionPromptStudioCard(
    diffConfig: DiffusionConfig,
    onUpdateConfig: (DiffusionConfig) -> Unit,
    onApplyPreset: (PromptPresetTemplate) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color(0xFF00FF66),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PROMPT CONDITIONING STUDIO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }

                Text(
                    text = "CLIP-L/G Encoder",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Positive Prompt Input
            OutlinedTextField(
                value = diffConfig.prompt,
                onValueChange = { onUpdateConfig(diffConfig.copy(prompt = it)) },
                label = { Text("Positive Prompt (Subject, Style, Lighting, Modifiers)", fontSize = 11.sp, color = Color(0xFF00FF66)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF2E3440),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCBD5E1)
                ),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tf_diffusion_prompt")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Negative Prompt Input
            OutlinedTextField(
                value = diffConfig.negativePrompt,
                onValueChange = { onUpdateConfig(diffConfig.copy(negativePrompt = it)) },
                label = { Text("Negative Prompt (Artifacts, Deformities, Quality Exclusions)", fontSize = 11.sp, color = Color(0xFFFF5252)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF5252),
                    unfocusedBorderColor = Color(0xFF2E3440),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCBD5E1)
                ),
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tf_diffusion_negative_prompt")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Prompt Preset Template Pills
            Text(
                text = "INSPIRATION PRESETS & STYLE INJECTIONS:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(DIFFUSION_PROMPT_PRESETS) { preset ->
                    Surface(
                        color = Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A303C)),
                        modifier = Modifier.clickable { onApplyPreset(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(preset.emoji, fontSize = 11.sp)
                            Text(
                                text = preset.title,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HYPERPARAMETERS & DIFFUSION CONTROLS
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionHyperparametersCard(
    diffConfig: DiffusionConfig,
    onUpdateConfig: (DiffusionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DIFFUSION HYPERPARAMETERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Model Architecture Selector
            Text(
                text = "FOUNDATION ARCHITECTURE (${diffConfig.modelArchitecture.baseFamily}):",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionModelArchitecture.values()) { arch ->
                    val isSel = diffConfig.modelArchitecture == arch
                    Surface(
                        color = if (isSel) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00FF66) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable {
                            onUpdateConfig(
                                diffConfig.copy(
                                    modelArchitecture = arch,
                                    width = arch.defaultWidth,
                                    height = arch.defaultHeight,
                                    inferenceSteps = arch.recommendedSteps,
                                    cfgScale = arch.recommendedCfg
                                )
                            )
                        }
                    ) {
                        Text(
                            text = arch.displayName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. GGML Quantization Format Selector
            Text(
                text = "GGML QUANTIZATION FORMAT (${diffConfig.quantization.typeCode}):",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionQuantization.values()) { q ->
                    val isSel = diffConfig.quantization == q
                    Surface(
                        color = if (isSel) Color(0xFF1A2230) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable {
                            onUpdateConfig(diffConfig.copy(quantization = q))
                        }
                    ) {
                        Text(
                            text = "${q.typeCode} (~${q.ramEstimateMb}MB)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Sampler Selector
            Text(
                text = "SOLVER & SAMPLING METHOD (${diffConfig.sampler.displayName}):",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionSampler.values()) { smp ->
                    val isSel = diffConfig.sampler == smp
                    Surface(
                        color = if (isSel) Color(0xFF2A2810) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFFFD600) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable {
                            onUpdateConfig(diffConfig.copy(sampler = smp))
                        }
                    ) {
                        Text(
                            text = smp.displayName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFFFFD600) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Aspect Ratio & Dimensions
            Text(
                text = "ASPECT RATIO & RESOLUTION (${diffConfig.width}x${diffConfig.height}):",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DIFFUSION_ASPECT_RATIOS) { ar ->
                    val isSel = diffConfig.width == ar.width && diffConfig.height == ar.height
                    Surface(
                        color = if (isSel) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00FF66) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable {
                            onUpdateConfig(diffConfig.copy(width = ar.width, height = ar.height))
                        }
                    ) {
                        Text(
                            text = ar.label,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Sliders: Steps & CFG Scale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Steps Slider
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("STEPS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                        Text("${diffConfig.inferenceSteps}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66))
                    }
                    Slider(
                        value = diffConfig.inferenceSteps.toFloat(),
                        onValueChange = { onUpdateConfig(diffConfig.copy(inferenceSteps = it.toInt())) },
                        valueRange = 1f..60f,
                        steps = 59,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00FF66), activeTrackColor = Color(0xFF00FF66))
                    )
                }

                // CFG Scale Slider
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CFG SCALE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                        Text(String.format(Locale.US, "%.1f", diffConfig.cfgScale), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                    Slider(
                        value = diffConfig.cfgScale,
                        onValueChange = { onUpdateConfig(diffConfig.copy(cfgScale = it)) },
                        valueRange = 1.0f..20.0f,
                        steps = 38,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )
                }
            }

            // Denoising Strength Slider (for img2img / inpaint)
            if (diffConfig.activeMode == DiffusionMode.IMG2IMG || diffConfig.activeMode == DiffusionMode.INPAINT) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DENOISING STRENGTH (IMG2IMG)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                    Text(String.format(Locale.US, "%.2f", diffConfig.denoisingStrength), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
                }
                Slider(
                    value = diffConfig.denoisingStrength,
                    onValueChange = { onUpdateConfig(diffConfig.copy(denoisingStrength = it)) },
                    valueRange = 0.05f..1.00f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9100), activeTrackColor = Color(0xFFFF9100))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Seed Selector & Randomizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (diffConfig.seed == -1L) "" else diffConfig.seed.toString(),
                    onValueChange = {
                        val parsed = it.toLongOrNull() ?: -1L
                        onUpdateConfig(diffConfig.copy(seed = parsed))
                    },
                    label = { Text("Seed (-1 for Random)", fontSize = 10.sp) },
                    placeholder = { Text("Random (-1)", fontSize = 10.sp, color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF2E3440),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        val randSeed = (Math.random() * 100000000).toLong()
                        onUpdateConfig(diffConfig.copy(seed = randSeed))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2430), contentColor = Color(0xFF00FF66)),
                    border = BorderStroke(1.dp, Color(0xFF2E3440)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.Casino, contentDescription = "Randomize Seed", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// CONTROLNET PANEL
// ------------------------------------------------------------------------------------------------

@Composable
fun ControlNetConditioningCard(
    diffConfig: DiffusionConfig,
    conditionBitmap: Bitmap?,
    onUpdateConfig: (DiffusionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
                    Text(
                        text = "CONTROLNET STRUCTURAL CONDITIONING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ControlNet Preprocessor type selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionControlNetType.values()) { type ->
                    val isSel = diffConfig.controlNetType == type
                    Surface(
                        color = if (isSel) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00FF66) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable { onUpdateConfig(diffConfig.copy(controlNetType = type)) }
                    ) {
                        Text(
                            text = type.displayName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conditioning Strength Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CONDITIONING STRENGTH", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.2f", diffConfig.controlNetStrength), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66))
            }
            Slider(
                value = diffConfig.controlNetStrength,
                onValueChange = { onUpdateConfig(diffConfig.copy(controlNetStrength = it)) },
                valueRange = 0.1f..2.0f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00FF66), activeTrackColor = Color(0xFF00FF66))
            )

            // Extracted Condition Map Preview
            if (conditionBitmap != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("EXTRACTED CONDITION TENSOR:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Spacer(modifier = Modifier.height(4.dp))
                Image(
                    bitmap = conditionBitmap.asImageBitmap(),
                    contentDescription = "ControlNet Condition Map",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// OUTPAINTING PANEL
// ------------------------------------------------------------------------------------------------

@Composable
fun OutpaintingSettingsCard(
    diffConfig: DiffusionConfig,
    onUpdateConfig: (DiffusionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Expand, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                Text(
                    text = "CANVAS OUTPAINTING & EXPANSION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direction Selector
            Text("EXPANSION AXIS DIRECTION:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionOutpaintDirection.values()) { dir ->
                    val isSel = diffConfig.outpaintDirection == dir
                    Surface(
                        color = if (isSel) Color(0xFF1A2230) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable { onUpdateConfig(diffConfig.copy(outpaintDirection = dir)) }
                    ) {
                        Text(
                            text = "${dir.axisEmoji} ${dir.displayName}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pixel Expansion Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("EXPANSION PIXELS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text("${diffConfig.outpaintExpandPixels}px", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            }
            Slider(
                value = diffConfig.outpaintExpandPixels.toFloat(),
                onValueChange = { onUpdateConfig(diffConfig.copy(outpaintExpandPixels = it.toInt())) },
                valueRange = 64f..512f,
                steps = 7,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// INPAINTING BRUSH PANEL
// ------------------------------------------------------------------------------------------------

@Composable
fun InpaintingBrushSettingsCard(
    diffConfig: DiffusionConfig,
    strokeCount: Int,
    onUpdateConfig: (DiffusionConfig) -> Unit,
    onClearMask: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Brush, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    Text(
                        text = "INPAINTING BRUSH & MASK BLEND",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFF5252)
                    )
                }

                if (strokeCount > 0) {
                    OutlinedButton(
                        onClick = onClearMask,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("CLEAR ($strokeCount)", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Brush Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BRUSH RADIUS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text("${diffConfig.inpaintBrushSize.toInt()}px", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
            }
            Slider(
                value = diffConfig.inpaintBrushSize,
                onValueChange = { onUpdateConfig(diffConfig.copy(inpaintBrushSize = it)) },
                valueRange = 8f..96f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5252), activeTrackColor = Color(0xFFFF5252))
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// UPSCALING PANEL
// ------------------------------------------------------------------------------------------------

@Composable
fun UpscalingSettingsCard(
    diffConfig: DiffusionConfig,
    onUpdateConfig: (DiffusionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(18.dp))
                Text(
                    text = "NEURAL SUPER-RESOLUTION & UPSCALE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFD600)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4).forEach { scale ->
                    val isSel = diffConfig.upscaleFactor == scale
                    Surface(
                        color = if (isSel) Color(0xFF2A2810) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFFFD600) else Color(0xFF2A303C)),
                        modifier = Modifier
                            .clickable { onUpdateConfig(diffConfig.copy(upscaleFactor = scale)) }
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${scale}X ULTRA-SCALE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFFFFD600) else Color.White)
                            Text("Output: ${diffConfig.width * scale}x${diffConfig.height * scale}px", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HARDWARE & BACKEND SETTINGS
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionHardwareBackendCard(
    diffConfig: DiffusionConfig,
    peakVramMb: Int,
    onUpdateConfig: (DiffusionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Hardware, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                    Text(
                        text = "STABLE-DIFFUSION.CPP HARDWARE BACKEND",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                }

                Surface(
                    color = Color(0xFF1F2430),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Peak VRAM: ${peakVramMb}MB",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compute Device Selector
            Text("COMPUTE ACCELERATOR:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionComputeBackend.values()) { be ->
                    val isSel = diffConfig.computeBackend == be
                    Surface(
                        color = if (isSel) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00FF66) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable { onUpdateConfig(diffConfig.copy(computeBackend = be)) }
                    ) {
                        Text(
                            text = be.displayName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // VAE Decoder Selector
            Text("VAE AUTOENCODER (DECODER):", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiffusionVaeType.values()) { vae ->
                    val isSel = diffConfig.vaeType == vae
                    Surface(
                        color = if (isSel) Color(0xFF1A2230) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF2A303C)),
                        modifier = Modifier.clickable { onUpdateConfig(diffConfig.copy(vaeType = vae)) }
                    ) {
                        Text(
                            text = vae.displayName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle Flags: Flash Attention & Live Latent Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Flash Attention (xFormers)", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                Switch(
                    checked = diffConfig.enableFlashAttention,
                    onCheckedChange = { onUpdateConfig(diffConfig.copy(enableFlashAttention = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF66), checkedTrackColor = Color(0xFF1B2A1E))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live TAESD Latent Preview", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                Switch(
                    checked = diffConfig.enableLiveLatentPreview,
                    onCheckedChange = { onUpdateConfig(diffConfig.copy(enableLiveLatentPreview = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF1A2230))
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// LOG TERMINAL
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionLogTerminalCard(
    logs: List<DiffusionConsoleLog>,
    statusMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1116)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00FF66)))
                    Text(
                        text = "GGML DIFFUSION TELEMETRY TERMINAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }
                Text(
                    text = "${logs.size} EVENTS",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8E95A5)
                    )
                } else {
                    logs.forEach { log ->
                        val color = when (log.level) {
                            "PERF" -> Color(0xFF00FF66)
                            "WARN" -> Color(0xFFFF5252)
                            "DEBUG" -> Color(0xFF00E5FF)
                            else -> Color(0xFFCBD5E1)
                        }
                        Text(
                            text = "[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))}] [${log.tag}] ${log.message}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HISTORY GALLERY
// ------------------------------------------------------------------------------------------------

@Composable
fun DiffusionHistoryGalleryCard(
    history: List<GeneratedDiffusionImage>,
    onSelect: (GeneratedDiffusionImage) -> Unit,
    onShare: (GeneratedDiffusionImage) -> Unit,
    onDelete: (GeneratedDiffusionImage) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
                    Text(
                        text = "GENERATED MASTERPIECES (${history.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (history.isEmpty()) {
                Text(
                    text = "No saved diffusion renderings yet. Click 'GENERATE' above.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { item ->
                        Surface(
                            color = Color(0xFF1A1E26),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF2A303C)),
                            modifier = Modifier
                                .width(160.dp)
                                .clickable { onSelect(item) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (item.bitmap != null) {
                                    Image(
                                        bitmap = item.bitmap!!.asImageBitmap(),
                                        contentDescription = item.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color(0xFF0D0E11), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color(0xFF2E3440))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.width}x${item.height} • ${item.inferenceTimeMs}ms",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00FF66)
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { onShare(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF00FF66), modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { onDelete(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HELPER: RENDER INPAINT TOUCH MASK TO BITMAP
// ------------------------------------------------------------------------------------------------

fun renderInpaintMaskToBitmap(width: Int, height: Int, strokes: List<BrushStroke>): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Black background
    canvas.drawColor(android.graphics.Color.BLACK)

    strokes.forEach { stroke ->
        paint.strokeWidth = stroke.strokeWidth
        for (i in 0 until stroke.points.size - 1) {
            canvas.drawLine(
                stroke.points[i].x,
                stroke.points[i].y,
                stroke.points[i + 1].x,
                stroke.points[i + 1].y,
                paint
            )
        }
    }

    return bitmap
}
