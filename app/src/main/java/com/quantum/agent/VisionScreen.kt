package com.quantum.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VisionScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visionEngine = remember { VisionEngine(context) }
    val visionState by visionEngine.state.collectAsStateWithLifecycle()
    val vConfig = config.visionConfig

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            visionEngine.startStream(vConfig)
        }
    }

    // Photo capture / Gallery picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    visionEngine.analyzeCustomBitmap(bitmap, vConfig, "Gallery Image")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto start stream if configured
    LaunchedEffect(vConfig.isRealtimeStreaming) {
        if (vConfig.isRealtimeStreaming && !visionState.isStreaming) {
            visionEngine.startStream(vConfig)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            visionEngine.stopStream()
        }
    }

    // Interactive Tap to Inspect Point
    var inspectedPoint by remember { mutableStateOf<Offset?>(null) }
    var showSnapshotSavedToast by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
    ) {
        // Mode Selector Tab Row
        VisionModeSelectorBar(
            activeMode = vConfig.activeMode,
            onSelectMode = { mode ->
                val newConfig = vConfig.copy(activeMode = mode)
                onConfigChange(config.copy(visionConfig = newConfig))
                visionEngine.setVisionMode(mode, newConfig)
            }
        )

        // Main Scrollable Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Viewfinder & Canvas Area
            item {
                VisionViewfinderSection(
                    visionState = visionState,
                    vConfig = vConfig,
                    inspectedPoint = inspectedPoint,
                    onPointInspected = { inspectedPoint = it },
                    onToggleStream = {
                        if (visionState.isStreaming) {
                            visionEngine.stopStream()
                        } else {
                            visionEngine.startStream(vConfig)
                        }
                    },
                    onCaptureSnapshot = {
                        val snapshot = visionEngine.captureAndSaveSnapshot(vConfig)
                        if (snapshot != null) {
                            showSnapshotSavedToast = true
                        }
                    },
                    onOpenGallery = { galleryPicker.launch("image/*") },
                    onSelectScene = { idx ->
                        visionEngine.selectBenchmarkScene(idx, vConfig)
                    },
                    scenes = visionEngine.getBenchmarkScenes()
                )
            }

            // Interactive Point Inspector Pill if user tapped
            if (inspectedPoint != null) {
                item {
                    PointInspectionCard(
                        point = inspectedPoint!!,
                        visionState = visionState,
                        onDismiss = { inspectedPoint = null }
                    )
                }
            }

            // Mode-Specific Deep Analytics Panel
            when (vConfig.activeMode) {
                VisionMode.DEPTH_ESTIMATION -> {
                    item {
                        DepthEstimationCard(
                            stats = visionState.depthStats,
                            selectedColormap = vConfig.colormap,
                            onSelectColormap = { colormap ->
                                val newConfig = vConfig.copy(colormap = colormap)
                                onConfigChange(config.copy(visionConfig = newConfig))
                            },
                            onExportDepth = {
                                visionEngine.captureAndSaveSnapshot(vConfig, "Depth Map Analysis")
                            }
                        )
                    }
                }

                VisionMode.OBJECT_DETECTION -> {
                    item {
                        ObjectDetectionEventsCard(
                            detectedObjects = visionState.detectedObjects,
                            logs = visionState.logs,
                            filterCategory = vConfig.filterCategory,
                            onFilterChange = { cat ->
                                val newConfig = vConfig.copy(filterCategory = cat)
                                onConfigChange(config.copy(visionConfig = newConfig))
                            },
                            onExportJson = { visionEngine.shareDetectionLogJson() }
                        )
                    }
                }

                VisionMode.OBJECT_COUNTING -> {
                    item {
                        ObjectCountingCard(
                            totalCount = visionState.totalCount,
                            categoryCounts = visionState.categoryCounts,
                            lineCrossingCount = visionState.lineCrossingCount,
                            enableLineCrossing = vConfig.enableLineCrossingGate,
                            onToggleLineCrossing = { enabled ->
                                val newConfig = vConfig.copy(enableLineCrossingGate = enabled)
                                onConfigChange(config.copy(visionConfig = newConfig))
                            },
                            enableHeatmap = vConfig.enableDensityHeatmap,
                            onToggleHeatmap = { enabled ->
                                val newConfig = vConfig.copy(enableDensityHeatmap = enabled)
                                onConfigChange(config.copy(visionConfig = newConfig))
                            }
                        )
                    }
                }

                VisionMode.FLORA_FAUNA_IDENTIFIER -> {
                    item {
                        FloraFaunaBotanicalCard(
                            taxonomy = visionState.floraFaunaTaxonomy,
                            selectedFloraModel = vConfig.selectedFloraModel,
                            onShareSpecies = {
                                visionEngine.captureAndSaveSnapshot(vConfig, "Botanical Identification")
                            }
                        )
                    }
                }

                VisionMode.MULTI_MODAL_ANALYSIS -> {
                    item {
                        MultiModalSummaryCard(
                            visionState = visionState,
                            vConfig = vConfig,
                            onExportAll = { visionEngine.shareDetectionLogJson() }
                        )
                    }
                }
            }

            // Real-Time Event Telemetry Log Stream
            item {
                VisionTelemetryLogTerminal(
                    logs = visionState.logs,
                    onShareLog = { visionEngine.shareDetectionLogJson() }
                )
            }

            // Saved Snapshots & Analysis Gallery
            item {
                VisionSnapshotGalleryCard(
                    snapshots = visionState.savedSnapshots,
                    onShare = { snapshot -> visionEngine.shareSnapshot(snapshot) },
                    onDelete = { snapshot -> visionEngine.deleteSnapshot(snapshot) }
                )
            }

            // Engine & Hardware Neural Settings
            item {
                VisionModelSettingsCard(
                    vConfig = vConfig,
                    onConfigUpdate = { updated ->
                        onConfigChange(config.copy(visionConfig = updated))
                    }
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// VIEWFINDER & OVERLAY CANVAS
// ------------------------------------------------------------------------------------------------

@Composable
fun VisionViewfinderSection(
    visionState: VisionState,
    vConfig: VisionConfig,
    inspectedPoint: Offset?,
    onPointInspected: (Offset) -> Unit,
    onToggleStream: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    onOpenGallery: () -> Unit,
    onSelectScene: (Int) -> Unit,
    scenes: List<BenchmarkScene>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Viewfinder Top Bar: Scene indicator & HUD
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
                            .background(if (visionState.isStreaming) Color(0xFF00FF66) else Color(0xFFFF9800))
                    )
                    Text(
                        text = if (visionState.isStreaming) "LIVE FEED" else "PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (visionState.isStreaming) Color(0xFF00FF66) else Color(0xFFFF9800)
                    )
                    Text(
                        text = "• ${String.format(Locale.US, "%.1f", visionState.currentFps)} FPS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "• ${String.format(Locale.US, "%.1f", visionState.currentLatencyMs)}ms",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8E95A5)
                    )
                }

                Surface(
                    color = Color(0xFF1F2430),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = vConfig.selectedModel.displayName,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Viewfinder Viewport Canvas (Shows either Camera, Depth Map, or Synthetic stream)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onPointInspected(offset)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background image or depth map
                val displayBitmap = if (vConfig.activeMode == VisionMode.DEPTH_ESTIMATION && visionState.depthMapBitmap != null) {
                    visionState.depthMapBitmap
                } else {
                    visionState.currentFrameBitmap
                }

                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Vision Viewfinder Frame",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Vector Bounding Box Overlay Canvas
                if (vConfig.activeMode != VisionMode.DEPTH_ESTIMATION && vConfig.enableBboxOverlay && visionState.detectedObjects.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Optional Line Crossing Gate overlay
                        if (vConfig.enableLineCrossingGate) {
                            val gateY = canvasH * vConfig.lineCrossingYRatio
                            drawLine(
                                color = Color(0xFFFFD600),
                                start = Offset(0f, gateY),
                                end = Offset(canvasW, gateY),
                                strokeWidth = 3f
                            )
                        }

                        visionState.detectedObjects.forEach { obj ->
                            val left = obj.bbox.left * canvasW
                            val top = obj.bbox.top * canvasH
                            val right = obj.bbox.right * canvasW
                            val bottom = obj.bbox.bottom * canvasH
                            val boxColor = Color(obj.colorHex)

                            // Draw rectangle box
                            drawRect(
                                color = boxColor,
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = 2.5.dp.toPx())
                            )

                            // Corner accents for high-tech HUD look
                            val cornerLen = 12.dp.toPx()
                            drawLine(boxColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = 5f)
                            drawLine(boxColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth = 5f)
                            drawLine(boxColor, Offset(right, top), Offset(right - cornerLen, top), strokeWidth = 5f)
                            drawLine(boxColor, Offset(right, top), Offset(right, top + cornerLen), strokeWidth = 5f)
                        }
                    }
                }

                // Tap inspector crosshair target marker
                if (inspectedPoint != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFF00FF66),
                            radius = 16.dp.toPx(),
                            center = inspectedPoint,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawLine(
                            color = Color(0xFF00FF66),
                            start = Offset(inspectedPoint.x - 24.dp.toPx(), inspectedPoint.y),
                            end = Offset(inspectedPoint.x + 24.dp.toPx(), inspectedPoint.y),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color(0xFF00FF66),
                            start = Offset(inspectedPoint.x, inspectedPoint.y - 24.dp.toPx()),
                            end = Offset(inspectedPoint.x, inspectedPoint.y + 24.dp.toPx()),
                            strokeWidth = 2f
                        )
                    }
                }

                // Floating Viewfinder HUD Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top HUD: Object counter pill
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
                                text = "TARGETS: ${visionState.totalCount} | ${vConfig.activeMode.title.uppercase()}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF66),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        if (vConfig.activeMode == VisionMode.DEPTH_ESTIMATION && visionState.depthStats != null) {
                            Surface(
                                color = Color(0xCC0D0E11),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "MEAN: ${String.format(Locale.US, "%.1f", visionState.depthStats.meanDistanceMeters)}m",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Bottom HUD hint
                    Text(
                        text = "Tap any pixel on screen to inspect distance & classification",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(Color(0x99000000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Stream, Snap Photo, Gallery, Scenes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Stream button
                Button(
                    onClick = onToggleStream,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (visionState.isStreaming) Color(0xFF2A1B1B) else Color(0xFF1B2A1E),
                        contentColor = if (visionState.isStreaming) Color(0xFFFF5252) else Color(0xFF00FF66)
                    ),
                    border = BorderStroke(1.dp, if (visionState.isStreaming) Color(0xFFFF5252) else Color(0xFF00FF66)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_vision_stream_toggle")
                ) {
                    Icon(
                        imageVector = if (visionState.isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Stream Toggle",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (visionState.isStreaming) "PAUSE" else "STREAM",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Snap photo button
                Button(
                    onClick = onCaptureSnapshot,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF66),
                        contentColor = Color(0xFF0D0E11)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_vision_snapshot")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture Snapshot",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SNAPSHOT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pick from gallery
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier
                        .background(Color(0xFF1F2430), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open Gallery",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scene Benchmarks Carousel
            Text(
                text = "SYNTHETIC BENCHMARK & TEST STREAMS:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(scenes.indices.toList()) { idx ->
                    val scene = scenes[idx]
                    val isSelected = visionState.selectedSceneIndex == idx
                    Surface(
                        color = if (isSelected) Color(0xFF1B2A1E) else Color(0xFF14171E),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                        ),
                        modifier = Modifier.clickable { onSelectScene(idx) }
                    ) {
                        Text(
                            text = scene.name,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// MODE SELECTOR CHIPS
// ------------------------------------------------------------------------------------------------

@Composable
fun VisionModeSelectorBar(
    activeMode: VisionMode,
    onSelectMode: (VisionMode) -> Unit
) {
    val modes = VisionMode.values()
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
                    .testTag("mode_${mode.name}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val icon = when (mode) {
                        VisionMode.DEPTH_ESTIMATION -> Icons.Default.Layers
                        VisionMode.OBJECT_DETECTION -> Icons.Default.Visibility
                        VisionMode.OBJECT_COUNTING -> Icons.Default.Numbers
                        VisionMode.FLORA_FAUNA_IDENTIFIER -> Icons.Default.Eco
                        VisionMode.MULTI_MODAL_ANALYSIS -> Icons.Default.Psychology
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = mode.title,
                        tint = if (isSelected) Color(0xFF0D0E11) else Color(0xFF00FF66),
                        modifier = Modifier.size(14.dp)
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
// POINT INSPECTOR CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun PointInspectionCard(
    point: Offset,
    visionState: VisionState,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2230)),
        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "POINT INSPECTOR (X: ${(point.x).toInt()}px, Y: ${(point.y).toInt()}px)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "Estimated Spatial Distance: ${String.format(Locale.US, "%.2f", (visionState.depthStats?.meanDistanceMeters ?: 1.8f) * 0.95f)} meters",
                    fontSize = 11.sp,
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.SemiBold
                )
                val closestObj = visionState.detectedObjects.firstOrNull()
                if (closestObj != null) {
                    Text(
                        text = "Nearest Salient Segment: ${closestObj.label} (${(closestObj.confidence * 100).toInt()}%)",
                        fontSize = 10.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Inspector",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// DEPTH ESTIMATION PANEL
// ------------------------------------------------------------------------------------------------

@Composable
fun DepthEstimationCard(
    stats: DepthMetricStats?,
    selectedColormap: DepthColormap,
    onSelectColormap: (DepthColormap) -> Unit,
    onExportDepth: () -> Unit
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
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DEPTH ESTIMATION & 3D METRICS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                }

                Button(
                    onClick = onExportDepth,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F2937),
                        contentColor = Color(0xFF00FF66)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00FF66)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("SAVE DEPTH MAP", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metric telemetry indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    title = "MIN DISTANCE",
                    value = "${String.format(Locale.US, "%.2f", stats?.minDistanceMeters ?: 0.45f)}m",
                    color = Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "MEAN DEPTH",
                    value = "${String.format(Locale.US, "%.2f", stats?.meanDistanceMeters ?: 2.30f)}m",
                    color = Color(0xFF00FF66),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "MAX REACH",
                    value = "${String.format(Locale.US, "%.1f", stats?.maxDistanceMeters ?: 8.20f)}m",
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Colormap selector
            Text(
                text = "DEPTH COLORMAP VISUALIZATION:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(DepthColormap.values()) { cmap ->
                    val isSelected = selectedColormap == cmap
                    Surface(
                        color = if (isSelected) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00FF66) else Color(0xFF2A303C)
                        ),
                        modifier = Modifier.clickable { onSelectColormap(cmap) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = cmap.colormapName,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF00FF66) else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Colormap Profile: ${selectedColormap.description}",
                fontSize = 9.5.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF8E95A5)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// OBJECT DETECTION & EVENT LOG CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun ObjectDetectionEventsCard(
    detectedObjects: List<DetectedObjectItem>,
    logs: List<VisionTelemetryEvent>,
    filterCategory: String,
    onFilterChange: (String) -> Unit,
    onExportJson: () -> Unit
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
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF00FF66),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "REAL-TIME DETECTIONS (${detectedObjects.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }

                Button(
                    onClick = onExportJson,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F2937),
                        contentColor = Color(0xFF00E5FF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("EXPORT JSON/CSV", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detected Objects List Pills
            if (detectedObjects.isEmpty()) {
                Text(
                    text = "No targets currently detected in viewport matching criteria.",
                    fontSize = 11.sp,
                    color = Color(0xFF8E95A5),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detectedObjects) { obj ->
                        Surface(
                            color = Color(0xFF1A1E26),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(obj.colorHex))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(obj.colorHex))
                                    )
                                    Text(
                                        text = "#${obj.trackingId} [${obj.category}]",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(obj.colorHex),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = obj.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Confidence: ${(obj.confidence * 100).toInt()}% • Depth: ${String.format(Locale.US, "%.1f", obj.estimatedDepthMeters)}m",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8E95A5)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// OBJECT COUNTING SCOREBOARD
// ------------------------------------------------------------------------------------------------

@Composable
fun ObjectCountingCard(
    totalCount: Int,
    categoryCounts: List<CategoryCountSummary>,
    lineCrossingCount: Int,
    enableLineCrossing: Boolean,
    onToggleLineCrossing: (Boolean) -> Unit,
    enableHeatmap: Boolean,
    onToggleHeatmap: (Boolean) -> Unit
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
                        imageVector = Icons.Default.Numbers,
                        contentDescription = null,
                        tint = Color(0xFFFFD600),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "OBJECT COUNTER & DENSITY SCOREBOARD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD600)
                    )
                }

                Surface(
                    color = Color(0xFF2A2810),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD600))
                ) {
                    Text(
                        text = "TOTAL: $totalCount ITEMS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD600),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Breakdown Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryCounts.take(3).forEach { cat ->
                    Surface(
                        color = Color(0xFF1A1E26),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(cat.colorHex).copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cat.category,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(cat.colorHex),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${cat.count}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${(cat.averageConfidence * 100).toInt()}% avg",
                                fontSize = 8.5.sp,
                                color = Color(0xFF8E95A5)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Virtual Line Crossing Trigger & Gate Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1E26), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Virtual Gate Crossing Counter: $lineCrossingCount crosses",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Counts objects crossing horizontal boundary line",
                        fontSize = 8.5.sp,
                        color = Color(0xFF8E95A5)
                    )
                }
                Switch(
                    checked = enableLineCrossing,
                    onCheckedChange = onToggleLineCrossing,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFD600),
                        checkedTrackColor = Color(0xFF5A4D00)
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// FLORA & FAUNA BOTANICAL & ENTOMOLOGICAL CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun FloraFaunaBotanicalCard(
    taxonomy: FloraFaunaTaxonomy?,
    selectedFloraModel: VisionModelArchitecture,
    onShareSpecies: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.6f)),
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
                        imageVector = Icons.Default.Eco,
                        contentDescription = null,
                        tint = Color(0xFF00FF66),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "FLORA & FAUNA TAXONOMIC IDENTIFIER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }

                Button(
                    onClick = onShareSpecies,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B2A1E),
                        contentColor = Color(0xFF00FF66)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00FF66)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("SAVE PROFILE", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (taxonomy != null) {
                // Common & Scientific Binomial Name
                Text(
                    text = taxonomy.commonName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = taxonomy.scientificBinomial,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFF00FF66)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Taxonomy & Health Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF1F2937),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = taxonomy.kingdomType.displayName,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF1F2937),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = taxonomy.familyOrOrder,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF1F2937),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${(taxonomy.confidence * 100).toInt()}% Match",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF66),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Health & Pest Condition Indicator
                Surface(
                    color = Color(0xFF1A261C),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(taxonomy.healthColorHex).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(taxonomy.healthColorHex),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "HEALTH & PEST CONDITION ASSESSMENT:",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8E95A5),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = taxonomy.healthCondition,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(taxonomy.healthColorHex)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ecological & Diagnostic Notes
                Text(
                    text = "ECOLOGICAL HABITAT & MORPHOLOGY:",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E95A5)
                )
                Text(
                    text = taxonomy.ecologicalNotes,
                    fontSize = 11.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Key Diagnostic Features
                taxonomy.keyDiagnosticFeatures.forEach { feat ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("•", color = Color(0xFF00FF66), fontSize = 10.sp)
                        Text(feat, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    }
                }
            } else {
                Text(
                    text = "Awaiting biological specimen frame in camera view...",
                    fontSize = 11.sp,
                    color = Color(0xFF8E95A5),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// MULTI-MODAL SUMMARY CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun MultiModalSummaryCard(
    visionState: VisionState,
    vConfig: VisionConfig,
    onExportAll: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF7C4DFF)),
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
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "UNIFIED MULTI-MODAL SCENE REASONING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF7C4DFF)
                    )
                }

                Button(
                    onClick = onExportAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF221A36),
                        contentColor = Color(0xFF7C4DFF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF7C4DFF)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("SHARE MATRIX", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Scene synthesis combining depth spatial estimation, bounding box clustering, and taxonomic classification into unified edge context.",
                fontSize = 10.5.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    title = "OBJECTS",
                    value = "${visionState.totalCount}",
                    color = Color(0xFF00FF66),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "MEAN DEPTH",
                    value = "${String.format(Locale.US, "%.1f", visionState.depthStats?.meanDistanceMeters ?: 0f)}m",
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "FPS",
                    value = "${String.format(Locale.US, "%.0f", visionState.currentFps)}",
                    color = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// REAL-TIME EVENT TELEMETRY LOG TERMINAL
// ------------------------------------------------------------------------------------------------

@Composable
fun VisionTelemetryLogTerminal(
    logs: List<VisionTelemetryEvent>,
    onShareLog: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C10)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME VISION EVENT LOG (${logs.size})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF66)
                )
                IconButton(
                    onClick = onShareLog,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Log",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color(0xFF08090C), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "Awaiting visual detection stream events...",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF5A6270)
                        )
                    } else {
                        logs.take(30).forEach { event ->
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(event.timestamp))
                            Text(
                                text = "[$timeStr] [FRM #${event.frameIndex}] ${event.summary}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// SAVED SNAPSHOTS GALLERY
// ------------------------------------------------------------------------------------------------

@Composable
fun VisionSnapshotGalleryCard(
    snapshots: List<SavedVisionSnapshot>,
    onShare: (SavedVisionSnapshot) -> Unit,
    onDelete: (SavedVisionSnapshot) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "SAVED VISION SNAPSHOTS & MEDIA HUB (${snapshots.size})",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (snapshots.isEmpty()) {
                Text(
                    text = "No saved snapshots yet. Tap 'SNAPSHOT' in the viewfinder to capture annotated frames and depth maps.",
                    fontSize = 10.sp,
                    color = Color(0xFF8E95A5),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(snapshots) { snap ->
                        Surface(
                            color = Color(0xFF1A1E26),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF2A303C)),
                            modifier = Modifier.width(200.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = snap.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(snap.timestamp)),
                                    fontSize = 8.5.sp,
                                    color = Color(0xFF8E95A5),
                                    fontFamily = FontFamily.Monospace
                                )
                                if (snap.detectedObjectsSummary.isNotBlank()) {
                                    Text(
                                        text = snap.detectedObjectsSummary,
                                        fontSize = 9.sp,
                                        color = Color(0xFF00FF66),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { onShare(snap) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(snap) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
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
// VISION MODEL & HARDWARE SETTINGS
// ------------------------------------------------------------------------------------------------

@Composable
fun VisionModelSettingsCard(
    vConfig: VisionConfig,
    onConfigUpdate: (VisionConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = BorderStroke(1.dp, Color(0xFF232731)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "NEURAL VISION ENGINE & MODEL ARCHITECTURES",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00FF66)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Model Architecture Selector
            Text(
                text = "PRIMARY INFERENCE MODEL ARCHITECTURE:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8E95A5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(VisionModelArchitecture.values()) { arch ->
                    val isSelected = vConfig.selectedModel == arch
                    Surface(
                        color = if (isSelected) Color(0xFF1B2A1E) else Color(0xFF1A1E26),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00FF66) else Color(0xFF2A303C)
                        ),
                        modifier = Modifier.clickable {
                            onConfigUpdate(vConfig.copy(selectedModel = arch))
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = arch.displayName,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF00FF66) else Color(0xFFCBD5E1)
                            )
                            Text(
                                text = "${arch.engineFamily} • ${arch.latencySpec}",
                                fontSize = 8.sp,
                                color = Color(0xFF8E95A5)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Confidence Threshold Slider
            Text(
                text = "CONFIDENCE DETECTION THRESHOLD: ${(vConfig.confidenceThreshold * 100).toInt()}%",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8E95A5)
            )
            Slider(
                value = vConfig.confidenceThreshold,
                onValueChange = { onConfigUpdate(vConfig.copy(confidenceThreshold = it)) },
                valueRange = 0.10f..0.90f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )

            // Overlays switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Draw Bounding Boxes & Labels",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                Switch(
                    checked = vConfig.enableBboxOverlay,
                    onCheckedChange = { onConfigUpdate(vConfig.copy(enableBboxOverlay = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00FF66),
                        checkedTrackColor = Color(0xFF1B4D2E)
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HELPER PILL
// ------------------------------------------------------------------------------------------------

@Composable
fun MetricPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1A1E26),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8E95A5),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
