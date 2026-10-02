package com.quantum.agent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

data class ConsoleLine(
    val sender: String,
    val message: String,
    val tool: String = "",
    val params: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class TerminalViewModel : ViewModel() {
    val consoleLog = mutableStateListOf<ConsoleLine>()
    var activeEngineStatus by mutableStateOf("IDLE - Awaiting Instructions")
        private set
    var isExecuting by mutableStateOf(false)
        private set

    // For direct solo stream accumulation
    var isSoloStreaming by mutableStateOf(false)
        private set

    init {
        consoleLog.add(
            ConsoleLine(
                sender = "SYSTEM",
                message = "Quantum Swarm & Inference Core v2.2 initialized (:swarm_engine_v1)."
            )
        )
        consoleLog.add(
            ConsoleLine(
                sender = "SYSTEM",
                message = "Backends: Llama.cpp, MLC-LLM, Ollama, Kobold.cpp. Fast C++ Audio: RapidSpeech.cpp, Whisper.cpp, Piper.cpp."
            )
        )
    }

    fun logSystem(msg: String) {
        activeEngineStatus = msg
        isExecuting = !msg.contains("concluded", ignoreCase = true) &&
                !msg.contains("threshold reached", ignoreCase = true) &&
                !msg.contains("cleared", ignoreCase = true) &&
                !msg.contains("completed", ignoreCase = true) &&
                !msg.contains("IDLE", ignoreCase = true)
        consoleLog.add(ConsoleLine(sender = "SYSTEM", message = msg))
    }

    fun logAgent(role: String, thought: String, tool: String, params: String) {
        isExecuting = true
        activeEngineStatus = "Active: $role reasoning..."
        consoleLog.add(
            ConsoleLine(
                sender = role.uppercase(),
                message = thought,
                tool = tool,
                params = params
            )
        )
    }

    fun appendSoloStreamChunk(chunk: String) {
        isExecuting = true
        isSoloStreaming = true
        activeEngineStatus = "Streaming Direct Inference..."

        if (consoleLog.isEmpty() || consoleLog.last().sender != "MODEL (SOLO)") {
            consoleLog.add(ConsoleLine(sender = "MODEL (SOLO)", message = chunk))
        } else {
            val lastIdx = consoleLog.size - 1
            val current = consoleLog[lastIdx]
            consoleLog[lastIdx] = current.copy(message = current.message + chunk)
        }
    }

    fun completeSoloStream(fullText: String) {
        isSoloStreaming = false
        isExecuting = false
        activeEngineStatus = "Inference completed."
        if (consoleLog.isNotEmpty() && consoleLog.last().sender == "MODEL (SOLO)") {
            val lastIdx = consoleLog.size - 1
            consoleLog[lastIdx] = consoleLog[lastIdx].copy(message = fullText)
        }
    }

    fun clear() {
        consoleLog.clear()
        isExecuting = false
        isSoloStreaming = false
        logSystem("Telemetry console memory cleared.")
    }
}

@Composable
fun RegisterSwarmReceiver(
    viewModel: TerminalViewModel,
    onSpeechPlaybackRequested: (String) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == SwarmBroadcastContract.ACTION_SWARM_TELEMETRY) {
                    val isChunk = intent.getBooleanExtra(SwarmBroadcastContract.EXTRA_IS_STREAMING_CHUNK, false)
                    val isComplete = intent.getBooleanExtra(SwarmBroadcastContract.EXTRA_STREAM_COMPLETE, false)
                    val streamChunk = intent.getStringExtra(SwarmBroadcastContract.EXTRA_STREAM_CHUNK)

                    if (isChunk && streamChunk != null) {
                        viewModel.appendSoloStreamChunk(streamChunk)
                        return
                    }
                    if (isComplete && streamChunk != null) {
                        viewModel.completeSoloStream(streamChunk)
                        onSpeechPlaybackRequested(streamChunk)
                        return
                    }

                    val status = intent.getStringExtra(SwarmBroadcastContract.EXTRA_SYSTEM_STATUS)
                    if (status != null) {
                        viewModel.logSystem(status)
                    } else {
                        val role = intent.getStringExtra(SwarmBroadcastContract.EXTRA_ROLE) ?: "UNKNOWN"
                        val thought = intent.getStringExtra(SwarmBroadcastContract.EXTRA_THOUGHT) ?: ""
                        val tool = intent.getStringExtra(SwarmBroadcastContract.EXTRA_TOOL) ?: ""
                        val params = intent.getStringExtra(SwarmBroadcastContract.EXTRA_PARAMS) ?: ""
                        viewModel.logAgent(role, thought, tool, params)
                        if (thought.isNotBlank()) {
                            onSpeechPlaybackRequested(thought)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(SwarmBroadcastContract.ACTION_SWARM_TELEMETRY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }
}

@Composable
fun WorkspaceContainer(
    viewModel: TerminalViewModel,
    onRun: (String, SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var config by remember { mutableStateOf(SwarmConfig()) }
    val tabs = listOf("CONSOLE", "DIFFUSION", "VISION", "CODING", "MUSIC", "ENGINES", "AUDIO", "CONTROL", "TOOLS", "MODELS")

    val fastAudioEngine = remember { FastCppAudioEngine(context) }
    val audioState by fastAudioEngine.state.collectAsStateWithLifecycle()

    // Permission launcher for Realtime STT Microphone
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            fastAudioEngine.startRealtimeMicrophoneStream(config)
        } else {
            viewModel.logSystem("Microphone permission denied for real-time STT.")
        }
    }

    // Connect STT transcription callback to console and active inference
    LaunchedEffect(fastAudioEngine, config) {
        fastAudioEngine.onTranscriptReady = { text ->
            viewModel.logSystem("Acoustic Input Transcribed: \"$text\"")
            if (config.speechSttParams.autoFeedToInference && text.isNotBlank()) {
                onRun(text, config)
            }
        }
    }

    // Clean up audio engine when disposed
    DisposableEffect(Unit) {
        onDispose {
            fastAudioEngine.shutdown()
        }
    }

    RegisterSwarmReceiver(
        viewModel = viewModel,
        onSpeechPlaybackRequested = { speechText ->
            if (config.speechTtsParams.autoSpeakEngineOutputs && speechText.isNotBlank()) {
                fastAudioEngine.synthesizeAndSpeak(speechText, config)
            }
        }
    )

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .systemBarsPadding()
    ) {
        // Immersive Top Header
        Surface(
            color = Color(0xFF0D0E11),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Quantum Inference",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color(0xFF00FF66)
                    )
                    Text(
                        text = "${config.selectedEngine.name} • ${config.executionMode.name} • ${config.speechTtsParams.engineType.name}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.0.sp,
                        color = Color(0xFF00E5FF)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Audio Mode Indicator pill
                    Surface(
                        color = if (audioState.isRecording || audioState.isSpeaking) Color(0xFF1F2937) else Color(0xFF14171E),
                        shape = RoundedCornerShape(percent = 50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (audioState.isRecording) Color(0xFFFF5252) else if (audioState.isSpeaking) Color(0xFF00E5FF) else Color(0xFF232731)
                        ),
                        modifier = Modifier.clickable {
                            if (!audioState.isRecording) {
                                if (hasRecordPermission) {
                                    fastAudioEngine.startRealtimeMicrophoneStream(config)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                fastAudioEngine.stopRealtimeMicrophoneStream()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (audioState.isRecording) Color(0xFFFF5252)
                                        else if (audioState.isSpeaking) Color(0xFF00E5FF)
                                        else Color(0xFF8E95A5)
                                    )
                                    .alpha(if (audioState.isRecording || audioState.isSpeaking) pulseAlpha else 0.7f)
                            )
                            Text(
                                text = if (audioState.isRecording) "MIC REC" else if (audioState.isSpeaking) "TTS SPEAK" else "MIC OFF",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (audioState.isRecording) Color(0xFFFF5252) else if (audioState.isSpeaking) Color(0xFF00E5FF) else Color(0xFF8E95A5)
                            )
                        }
                    }

                    // Engine status pill
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(percent = 50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModel.isExecuting) Color(0xFF00FF66).copy(alpha = 0.5f) else Color(0xFF232731)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (viewModel.isExecuting) Color(0xFF00FF66) else Color(0xFF00E5FF))
                                    .alpha(if (viewModel.isExecuting) pulseAlpha else 0.8f)
                            )
                            Text(
                                text = if (viewModel.isExecuting) "STREAMING" else "STANDBY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF66)
                            )
                        }
                    }
                }
            }
        }

        // Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF14171E),
            contentColor = Color(0xFF00FF66),
            edgePadding = 12.dp,
            divider = { HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp) },
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF00FF66),
                        height = 2.dp
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("tab_$title"),
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5)
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> TerminalScreen(
                    viewModel = viewModel,
                    config = config,
                    audioState = audioState,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onStartMic = { fastAudioEngine.startRealtimeMicrophoneStream(config) },
                    onStopMic = { fastAudioEngine.stopRealtimeMicrophoneStream() },
                    onStopSpeak = { fastAudioEngine.stopSpeaking() },
                    onConfigChange = { config = it },
                    onRun = onRun
                )
                1 -> DiffusionScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                2 -> VisionScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                3 -> CodingWorkspaceScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                4 -> MusicStudioScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                5 -> EngineSettingsScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                6 -> AudioPipelineScreen(
                    config = config,
                    audioState = audioState,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onStartMic = { fastAudioEngine.startRealtimeMicrophoneStream(config) },
                    onStopMic = { fastAudioEngine.stopRealtimeMicrophoneStream() },
                    onSynthesizeTest = { text -> fastAudioEngine.synthesizeAndSpeak(text, config) },
                    onStopSpeak = { fastAudioEngine.stopSpeaking() },
                    onConfigChange = { config = it }
                )
                7 -> ControlScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                8 -> ToolsScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
                9 -> HuggingFaceModelManagerScreen(
                    config = config,
                    onConfigChange = { config = it }
                )
            }
        }
    }
}

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    config: SwarmConfig,
    audioState: AudioEngineState,
    hasRecordPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartMic: () -> Unit,
    onStopMic: () -> Unit,
    onStopSpeak: () -> Unit,
    onConfigChange: (SwarmConfig) -> Unit,
    onRun: (String, SwarmConfig) -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.consoleLog.size, viewModel.consoleLog.lastOrNull()?.message?.length) {
        if (viewModel.consoleLog.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.consoleLog.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
    ) {
        // Mode & Engine Banner
        Surface(
            color = Color(0xFF14171E),
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Solo vs Swarm Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MODE:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E95A5)
                    )
                    FilterChip(
                        selected = config.executionMode == ExecutionMode.SOLO,
                        onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.SOLO)) },
                        label = { Text("SOLO INFERENCE", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00FF66).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF00FF66),
                            containerColor = Color(0xFF0D0E11),
                            labelColor = Color(0xFF8E95A5)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = config.executionMode == ExecutionMode.SOLO,
                            borderColor = Color(0xFF232731),
                            selectedBorderColor = Color(0xFF00FF66)
                        )
                    )
                    FilterChip(
                        selected = config.executionMode == ExecutionMode.AGENTIC,
                        onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.AGENTIC)) },
                        label = { Text("SWARM AGENTIC", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF00E5FF),
                            containerColor = Color(0xFF0D0E11),
                            labelColor = Color(0xFF8E95A5)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = config.executionMode == ExecutionMode.AGENTIC,
                            borderColor = Color(0xFF232731),
                            selectedBorderColor = Color(0xFF00E5FF)
                        )
                    )
                }

                // Clear button
                IconButton(
                    onClick = { viewModel.clear() },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("clear_console_button")
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Clear Console",
                        tint = Color(0xFF8E95A5),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Live Audio Visualizer / STT Status Strip
        AnimatedVisibility(visible = audioState.isRecording || audioState.isSpeaking || audioState.liveTranscript.isNotBlank()) {
            Surface(
                color = Color(0xFF181D26),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (audioState.isRecording) Color(0xFFFF5252).copy(alpha = 0.6f) else Color(0xFF00E5FF).copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Decibel waveform visualizer
                    AudioWaveformBar(
                        decibels = audioState.currentDecibels,
                        isActive = audioState.isRecording || audioState.isSpeaking,
                        color = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF00E5FF),
                        modifier = Modifier
                            .width(60.dp)
                            .height(18.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (audioState.isRecording) "LIVE ACOUSTIC CAPTURE (${audioState.activeSttEngine.name})"
                            else "FAST C++ VOCALIZER (${audioState.activeTtsEngine.name})",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF00E5FF)
                        )
                        if (audioState.liveTranscript.isNotBlank()) {
                            Text(
                                text = audioState.liveTranscript,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = audioState.statusMessage,
                                fontSize = 10.sp,
                                color = Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    if (audioState.isSpeaking) {
                        IconButton(
                            onClick = onStopSpeak,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = "Mute",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Terminal Log Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(viewModel.consoleLog) { item ->
                ConsoleRow(item)
            }
        }

        // Status Line
        Surface(
            color = Color(0xFF14171E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS: ${viewModel.activeEngineStatus}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00E5FF),
                    maxLines = 1
                )
            }
        }

        // Bottom Input Row
        Surface(
            color = Color(0xFF0D0E11),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mic Button
                IconButton(
                    onClick = {
                        if (!audioState.isRecording) {
                            if (hasRecordPermission) {
                                onStartMic()
                            } else {
                                onRequestPermission()
                            }
                        } else {
                            onStopMic()
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (audioState.isRecording) Color(0xFFFF5252).copy(alpha = 0.25f) else Color(0xFF14171E))
                        .border(
                            1.dp,
                            if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF232731),
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("mic_toggle_button")
                ) {
                    Icon(
                        if (audioState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF00FF66),
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_prompt_input"),
                    placeholder = {
                        Text(
                            text = if (config.executionMode == ExecutionMode.SOLO) "Prompt ${config.selectedEngine.name}..." else "Dispatch swarm command...",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF555D6E)
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF232731),
                        focusedContainerColor = Color(0xFF14171E),
                        unfocusedContainerColor = Color(0xFF14171E),
                        cursorColor = Color(0xFF00FF66)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (promptInput.isNotBlank()) {
                            val msg = promptInput.trim()
                            promptInput = ""
                            keyboardController?.hide()
                            viewModel.logSystem("User dispatched: $msg")
                            onRun(msg, config)
                        }
                    }),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val msg = promptInput.trim()
                            promptInput = ""
                            keyboardController?.hide()
                            viewModel.logSystem("User dispatched: $msg")
                            onRun(msg, config)
                        }
                    },
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("send_prompt_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF66),
                        contentColor = Color(0xFF0D0E11)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConsoleRow(item: ConsoleLine) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))

    val (badgeColor, textColor) = when (item.sender) {
        "SYSTEM" -> Color(0xFF00E5FF) to Color(0xFF00E5FF)
        "MODEL (SOLO)" -> Color(0xFF00FF66) to Color(0xFFE0E0E0)
        "USER" -> Color(0xFFF59E0B) to Color(0xFFFFFFFF)
        "ORCHESTRATOR" -> Color(0xFF8B5CF6) to Color(0xFFE2E8F0)
        "ANALYST" -> Color(0xFF3B82F6) to Color(0xFFE2E8F0)
        "EXECUTOR" -> Color(0xFFEF4444) to Color(0xFFE2E8F0)
        else -> Color(0xFF8E95A5) to Color(0xFF8E95A5)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF14171E))
            .border(1.dp, Color(0xFF232731), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = item.sender,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (item.tool.isNotBlank()) {
                    Text(
                        text = "TOOL [${item.tool}]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )
                }
            }

            Text(
                text = timeStr,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF555D6E)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.message,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            lineHeight = 16.sp
        )

        if (item.params.isNotBlank() && item.params != "{}") {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xFF0D0E11),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.params,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5),
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun AudioWaveformBar(
    decibels: Float,
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 12
    val animTransition = rememberInfiniteTransition(label = "WaveAnim")
    val phase by animTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / (barCount * 1.5f)
        val step = width / barCount

        val normalized = if (isActive) (decibels / 80f).coerceIn(0.15f, 1f) else 0.05f

        for (i in 0 until barCount) {
            val wave = if (isActive) (sin(phase + i * 0.5f) + 1f) * 0.5f else 0.1f
            val barHeight = height * (normalized * 0.6f + wave * 0.4f * normalized).coerceIn(0.08f, 1f)
            val left = i * step
            val top = (height - barHeight) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// ENGINES CONFIGURATION SCREEN
// ------------------------------------------------------------------------------------------------
@Composable
fun EngineSettingsScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    var selectedEngineTab by remember { mutableStateOf(config.selectedEngine) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "INFERENCE ENGINE SELECTION",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00FF66),
            letterSpacing = 1.sp
        )

        // Engine Selector Radio Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InferenceEngineType.values().forEach { engineType ->
                val isSelected = config.selectedEngine == engineType
                Surface(
                    color = if (isSelected) Color(0xFF1A2234) else Color(0xFF14171E),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedEngineTab = engineType
                            onConfigChange(config.copy(selectedEngine = engineType))
                        }
                        .testTag("select_engine_${engineType.name}")
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (engineType) {
                                InferenceEngineType.LLAMA_CPP -> "LLAMA.CPP"
                                InferenceEngineType.MLC_LLM -> "MLC-LLM"
                                InferenceEngineType.OLLAMA -> "OLLAMA"
                                InferenceEngineType.KOBOLD_CPP -> "KOBOLD.CPP"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5)
                        )
                        Text(
                            text = when (engineType) {
                                InferenceEngineType.LLAMA_CPP -> "Local GGUF"
                                InferenceEngineType.MLC_LLM -> "Vulkan/TVM"
                                InferenceEngineType.OLLAMA -> "REST/SSE"
                                InferenceEngineType.KOBOLD_CPP -> "Unified HTTP"
                            },
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF555D6E)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // Engine Specific Settings
        when (config.selectedEngine) {
            InferenceEngineType.LLAMA_CPP -> LlamaCppParamsEditor(
                params = config.llamaCppParams,
                onParamsChange = { onConfigChange(config.copy(llamaCppParams = it)) }
            )
            InferenceEngineType.MLC_LLM -> MlcLlmParamsEditor(
                params = config.mlcLlmParams,
                onParamsChange = { onConfigChange(config.copy(mlcLlmParams = it)) }
            )
            InferenceEngineType.OLLAMA -> OllamaParamsEditor(
                params = config.ollamaParams,
                onParamsChange = { onConfigChange(config.copy(ollamaParams = it)) }
            )
            InferenceEngineType.KOBOLD_CPP -> KoboldCppParamsEditor(
                params = config.koboldCppParams,
                onParamsChange = { onConfigChange(config.copy(koboldCppParams = it)) }
            )
        }
    }
}

@Composable
fun KoboldCppParamsEditor(
    params: KoboldCppParams,
    onParamsChange: (KoboldCppParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "KOBOLD.CPP CONFIGURATION (HTTP/SSE BRIDGE)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        OutlinedTextField(
            value = params.hostUrl,
            onValueChange = { onParamsChange(params.copy(hostUrl = it)) },
            label = { Text("Kobold.cpp Host Endpoint URL", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF66),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = params.maxContextLength.toString(),
                onValueChange = { str ->
                    str.toIntOrNull()?.let { onParamsChange(params.copy(maxContextLength = it)) }
                },
                label = { Text("Max Context", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF232731),
                    focusedContainerColor = Color(0xFF14171E),
                    unfocusedContainerColor = Color(0xFF14171E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            OutlinedTextField(
                value = params.maxLength.toString(),
                onValueChange = { str ->
                    str.toIntOrNull()?.let { onParamsChange(params.copy(maxLength = it)) }
                },
                label = { Text("Max Gen Length", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF232731),
                    focusedContainerColor = Color(0xFF14171E),
                    unfocusedContainerColor = Color(0xFF14171E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )
        }

        // Temperature Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Temperature", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.2f", params.temperature), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
            }
            Slider(
                value = params.temperature,
                onValueChange = { onParamsChange(params.copy(temperature = it)) },
                valueRange = 0.05f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )
        }

        // Top P Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Top P Sampling", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.2f", params.topP), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
            }
            Slider(
                value = params.topP,
                onValueChange = { onParamsChange(params.copy(topP = it)) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )
        }

        // Repetition Penalty Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Repetition Penalty", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.2f", params.repPen), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
            }
            Slider(
                value = params.repPen,
                onValueChange = { onParamsChange(params.copy(repPen = it)) },
                valueRange = 1.0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )
        }

        // SSE Streaming toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("SSE Stream Generation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Real-time token streaming (/api/extra/generate/stream)", fontSize = 10.sp, color = Color(0xFF8E95A5))
            }
            Switch(
                checked = params.useStreaming,
                onCheckedChange = { onParamsChange(params.copy(useStreaming = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00FF66),
                    checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF8E95A5),
                    uncheckedTrackColor = Color(0xFF232731)
                )
            )
        }
    }
}

@Composable
fun LlamaCppParamsEditor(
    params: LlamaCppParams,
    onParamsChange: (LlamaCppParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "LLAMA.CPP NATIVE GGUF PARAMETERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = params.contextSize.toString(),
                onValueChange = { str ->
                    str.toIntOrNull()?.let { onParamsChange(params.copy(contextSize = it)) }
                },
                label = { Text("Context Size (n_ctx)", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF232731),
                    focusedContainerColor = Color(0xFF14171E),
                    unfocusedContainerColor = Color(0xFF14171E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            OutlinedTextField(
                value = params.threadCount.toString(),
                onValueChange = { str ->
                    str.toIntOrNull()?.let { onParamsChange(params.copy(threadCount = it)) }
                },
                label = { Text("Threads (n_threads)", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF232731),
                    focusedContainerColor = Color(0xFF14171E),
                    unfocusedContainerColor = Color(0xFF14171E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )
        }

        // KV Cache Precision
        Text("KV Cache Quantization Precision", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KvCachePrecision.values().forEach { prec ->
                val isSelected = params.cachePrecision == prec
                Surface(
                    color = if (isSelected) Color(0xFF1A2234) else Color(0xFF14171E),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onParamsChange(params.copy(cachePrecision = prec)) }
                ) {
                    Text(
                        text = prec.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Temperature Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Temperature", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.2f", params.temperature), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
            }
            Slider(
                value = params.temperature,
                onValueChange = { onParamsChange(params.copy(temperature = it)) },
                valueRange = 0.05f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )
        }
    }
}

@Composable
fun MlcLlmParamsEditor(
    params: MlcLlmParams,
    onParamsChange: (MlcLlmParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "MLC-LLM (VULKAN TVM RUNTIME) PARAMETERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        OutlinedTextField(
            value = params.maxGenLen.toString(),
            onValueChange = { str ->
                str.toIntOrNull()?.let { onParamsChange(params.copy(maxGenLen = it)) }
            },
            label = { Text("Max Generation Length", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF66),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Vulkan GPU Acceleration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Leverage Qualcomm Adreno / ARM Mali GPU", fontSize = 10.sp, color = Color(0xFF8E95A5))
            }
            Switch(
                checked = params.vulkanGpuEnabled,
                onCheckedChange = { onParamsChange(params.copy(vulkanGpuEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00FF66),
                    checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF8E95A5),
                    uncheckedTrackColor = Color(0xFF232731)
                )
            )
        }
    }
}

@Composable
fun OllamaParamsEditor(
    params: OllamaParams,
    onParamsChange: (OllamaParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "OLLAMA ENGINE CONFIGURATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        OutlinedTextField(
            value = params.hostUrl,
            onValueChange = { onParamsChange(params.copy(hostUrl = it)) },
            label = { Text("Ollama Daemon Host URL", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF66),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )

        OutlinedTextField(
            value = params.modelTag,
            onValueChange = { onParamsChange(params.copy(modelTag = it)) },
            label = { Text("Model Tag (e.g., llama3.2:1b, qwen2.5:0.5b)", fontSize = 11.sp, color = Color(0xFF8E95A5)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF66),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )
    }
}

// ------------------------------------------------------------------------------------------------
// AUDIO / VOICE PIPELINE SCREEN (RapidSpeech.cpp, Whisper.cpp, Piper.cpp)
// ------------------------------------------------------------------------------------------------
@Composable
fun AudioPipelineScreen(
    config: SwarmConfig,
    audioState: AudioEngineState,
    hasRecordPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartMic: () -> Unit,
    onStopMic: () -> Unit,
    onSynthesizeTest: (String) -> Unit,
    onStopSpeak: () -> Unit,
    onConfigChange: (SwarmConfig) -> Unit
) {
    var testSpeakInput by remember { mutableStateOf("Quantum Swarm and RapidSpeech C++ acoustic vocalizer online.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Text(
            text = "FAST C++ AUDIO PROCESSING ENGINE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00FF66),
            letterSpacing = 1.sp
        )

        // Live Audio Visualizer Card
        Surface(
            color = Color(0xFF14171E),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ACOUSTIC SPECTRUM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "${audioState.currentDecibels.toInt()} dB SPL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF8E95A5)
                    )
                }

                // Waveform Canvas
                AudioWaveformBar(
                    decibels = audioState.currentDecibels,
                    isActive = audioState.isRecording || audioState.isSpeaking,
                    color = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF00FF66),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )

                Text(
                    text = "STATUS: ${audioState.statusMessage}",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!audioState.isRecording) {
                                if (hasRecordPermission) {
                                    onStartMic()
                                } else {
                                    onRequestPermission()
                                }
                            } else {
                                onStopMic()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (audioState.isRecording) Color(0xFFFF5252) else Color(0xFF00FF66),
                            contentColor = Color(0xFF0D0E11)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            if (audioState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (audioState.isRecording) "STOP MIC STREAM" else "START MIC STREAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (audioState.isSpeaking) {
                        OutlinedButton(
                            onClick = onStopSpeak,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
                        ) {
                            Text("STOP TTS", color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // 1. C++ Audio Backend Engine Selection
        Text(
            text = "AUDIO BACKEND ENGINES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioEngineType.values().forEach { engine ->
                val isSelected = config.speechTtsParams.engineType == engine
                Surface(
                    color = if (isSelected) Color(0xFF1A2234) else Color(0xFF14171E),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onConfigChange(
                                config.copy(
                                    speechTtsParams = config.speechTtsParams.copy(engineType = engine),
                                    speechSttParams = config.speechSttParams.copy(engineType = engine)
                                )
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = when (engine) {
                                AudioEngineType.RAPID_SPEECH_CPP -> "RapidSpeech.cpp"
                                AudioEngineType.WHISPER_CPP -> "Whisper.cpp"
                                AudioEngineType.PIPER_CPP -> "Piper.cpp"
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5)
                        )
                        Text(
                            text = when (engine) {
                                AudioEngineType.RAPID_SPEECH_CPP -> "Unified STT/TTS"
                                AudioEngineType.WHISPER_CPP -> "Fast GGML STT"
                                AudioEngineType.PIPER_CPP -> "Neural VITS TTS"
                            },
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF555D6E)
                        )
                    }
                }
            }
        }

        // 2. Audio Compute Device Selector
        Text(
            text = "COMPUTE ACCELERATION DEVICE (DEFAULT: LOCAL CPU NEON)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8E95A5)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioComputeDevice.values().forEach { device ->
                val isSelected = config.speechTtsParams.computeDevice == device
                Surface(
                    color = if (isSelected) Color(0xFF1A2234) else Color(0xFF14171E),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onConfigChange(
                                config.copy(
                                    speechTtsParams = config.speechTtsParams.copy(computeDevice = device),
                                    speechSttParams = config.speechSttParams.copy(computeDevice = device)
                                )
                            )
                        }
                ) {
                    Text(
                        text = when (device) {
                            AudioComputeDevice.LOCAL_CPU_NEON -> "CPU NEON (Default)"
                            AudioComputeDevice.GPU_VULKAN -> "Vulkan GPU"
                            AudioComputeDevice.OPENCL -> "OpenCL"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // 3. Real-time STT Microphone Stream Integration
        Text(
            text = "REAL-TIME STT MICROPHONE STREAM INTEGRATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Auto-Feed Mic Transcript to Inference", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Transcriptions immediately dispatch to active model backend", fontSize = 9.5.sp, color = Color(0xFF8E95A5))
            }
            Switch(
                checked = config.speechSttParams.autoFeedToInference,
                onCheckedChange = {
                    onConfigChange(config.copy(speechSttParams = config.speechSttParams.copy(autoFeedToInference = it)))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00FF66),
                    checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF8E95A5),
                    uncheckedTrackColor = Color(0xFF232731)
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Continuous Stream Listening", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Keep acoustic sensor alive across multiple speech turns", fontSize = 9.5.sp, color = Color(0xFF8E95A5))
            }
            Switch(
                checked = config.speechSttParams.continuousStream,
                onCheckedChange = {
                    onConfigChange(config.copy(speechSttParams = config.speechSttParams.copy(continuousStream = it)))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00FF66),
                    checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF8E95A5),
                    uncheckedTrackColor = Color(0xFF232731)
                )
            )
        }

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // 4. Fast C++ TTS Vocalizer Output Settings
        Text(
            text = "FAST C++ TTS VOCALIZER OUTPUT SETTINGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Auto-Speak Inference Engine Output", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Model responses synthesized immediately through C++ TTS", fontSize = 9.5.sp, color = Color(0xFF8E95A5))
            }
            Switch(
                checked = config.speechTtsParams.autoSpeakEngineOutputs,
                onCheckedChange = {
                    onConfigChange(config.copy(speechTtsParams = config.speechTtsParams.copy(autoSpeakEngineOutputs = it)))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00FF66),
                    checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF8E95A5),
                    uncheckedTrackColor = Color(0xFF232731)
                )
            )
        }

        // Speaking Rate Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Speaking Rate", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                Text(String.format(Locale.US, "%.1fx", config.speechTtsParams.speakingRate), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
            }
            Slider(
                value = config.speechTtsParams.speakingRate,
                onValueChange = {
                    onConfigChange(config.copy(speechTtsParams = config.speechTtsParams.copy(speakingRate = it)))
                },
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66),
                    inactiveTrackColor = Color(0xFF232731)
                )
            )
        }

        // Test Speech Synthesis
        Surface(
            color = Color(0xFF14171E),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test Speech Synthesis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(
                    value = testSpeakInput,
                    onValueChange = { testSpeakInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF232731),
                        focusedContainerColor = Color(0xFF0D0E11),
                        unfocusedContainerColor = Color(0xFF0D0E11),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                )
                Button(
                    onClick = { onSynthesizeTest(testSpeakInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF0D0E11)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PLAY TEST AUDIO SYNTHESIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// CONTROL SCREEN (Swarm Prompts & Agent Allocation)
// ------------------------------------------------------------------------------------------------
@Composable
fun ControlScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SWARM & DIRECT INFERENCE CONTROL",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00FF66),
            letterSpacing = 1.sp
        )

        // Solo System Prompt
        Text(
            text = "SOLO DIRECT INFERENCE SYSTEM PROMPT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )
        OutlinedTextField(
            value = config.soloSystemPrompt,
            onValueChange = { onConfigChange(config.copy(soloSystemPrompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF66),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            maxLines = 4
        )

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // Max Swarm Agent Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Active Swarm Agents", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Autonomous loop thread concurrency", fontSize = 10.sp, color = Color(0xFF8E95A5))
            }
            Text("${config.maxAgents} Agents", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
        }
        Slider(
            value = config.maxAgents.toFloat(),
            onValueChange = { onConfigChange(config.copy(maxAgents = it.toInt())) },
            valueRange = 1f..6f,
            steps = 4,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00FF66),
                activeTrackColor = Color(0xFF00FF66),
                inactiveTrackColor = Color(0xFF232731)
            )
        )

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        // Agent Role Prompts
        Text("ORCHESTRATOR ROLE PROMPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF8B5CF6))
        OutlinedTextField(
            value = config.orchestratorPrompt,
            onValueChange = { onConfigChange(config.copy(orchestratorPrompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )

        Text("HARDWARE EXECUTOR ROLE PROMPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFEF4444))
        OutlinedTextField(
            value = config.executorPrompt,
            onValueChange = { onConfigChange(config.copy(executorPrompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFEF4444),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )

        Text("ANALYST ROLE PROMPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF3B82F6))
        OutlinedTextField(
            value = config.analystPrompt,
            onValueChange = { onConfigChange(config.copy(analystPrompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF232731),
                focusedContainerColor = Color(0xFF14171E),
                unfocusedContainerColor = Color(0xFF14171E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )
    }
}

// ------------------------------------------------------------------------------------------------
// TOOLS SCREEN (MCP Servers & Hardware Tools)
// ------------------------------------------------------------------------------------------------
@Composable
fun ToolsScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MCP TOOL BRIDGES & PERMISSIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00FF66),
            letterSpacing = 1.sp
        )

        Text("MCP Tool Server Endpoints", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))

        config.mcpServers.forEachIndexed { index, server ->
            Surface(
                color = Color(0xFF14171E),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(server.serverName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(server.endpointUrl, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8E95A5))
                    }
                    Switch(
                        checked = server.isEnabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.mcpServers.toMutableList()
                            updated[index] = server.copy(isEnabled = isChecked)
                            onConfigChange(config.copy(mcpServers = updated))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color(0xFF8E95A5),
                            uncheckedTrackColor = Color(0xFF232731)
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF232731), thickness = 1.dp)

        Text("System Tools Permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))

        Surface(
            color = Color(0xFF14171E),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Orchestrator System Tools", fontSize = 11.sp, color = Color.White)
                    Switch(
                        checked = config.orchestratorTools.allowSystemTools,
                        onCheckedChange = {
                            onConfigChange(config.copy(orchestratorTools = config.orchestratorTools.copy(allowSystemTools = it)))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Executor Hardware APIs", fontSize = 11.sp, color = Color.White)
                    Switch(
                        checked = config.executorTools.allowSystemTools,
                        onCheckedChange = {
                            onConfigChange(config.copy(executorTools = config.executorTools.copy(allowSystemTools = it)))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HUGGING FACE GGUF MODEL MANAGER SCREEN
// ------------------------------------------------------------------------------------------------
@Composable
fun HuggingFaceModelManagerScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { ModelStorageManager(context) }
    val modelDownloader = remember { ModelDownloader(context) }
    val hfService = remember { HuggingFaceService() }
    val scope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Local Models, 1: Hugging Face, 2: Permissions & Health
    val downloadState by modelDownloader.downloadState.collectAsStateWithLifecycle()

    var localModels by remember { mutableStateOf<List<LocalModelFile>>(emptyList()) }
    var storageInfo by remember { mutableStateOf(storageManager.queryStorageSpace()) }
    var isRefreshingLocal by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("qwen") }
    var isHfLoading by remember { mutableStateOf(false) }
    var hfModelList by remember { mutableStateOf<List<HuggingFaceModelItem>>(emptyList()) }
    var statusText by remember { mutableStateOf("Local storage and Hugging Face repository ready.") }

    var modelToDelete by remember { mutableStateOf<LocalModelFile?>(null) }
    var importStatusMessage by remember { mutableStateOf("") }

    // SAF Document Picker for importing .gguf, .bin, .onnx models from anywhere on device
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importStatusMessage = "Importing model binary from storage..."
                val imported = storageManager.importModelFromUri(uri)
                if (imported != null) {
                    importStatusMessage = "Successfully imported: ${imported.name} (${imported.formattedSize})"
                    localModels = storageManager.listLocalModels()
                    storageInfo = storageManager.queryStorageSpace()
                    onConfigChange(config.copy(selectedModelPath = imported.absolutePath))
                } else {
                    importStatusMessage = "Failed to import model from selected URI."
                }
            }
        }
    }

    // Permission launcher for Notifications on Android 13+
    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) "Notification permission granted." else "Notification permission declined."
    }

    // Permission launcher for Microphone
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) "Microphone permission granted." else "Microphone permission declined."
    }

    fun refreshLocal() {
        scope.launch {
            isRefreshingLocal = true
            localModels = storageManager.listLocalModels()
            storageInfo = storageManager.queryStorageSpace()
            isRefreshingLocal = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLocal()
        isHfLoading = true
        statusText = "Querying huggingface.co API for GGUF models..."
        hfModelList = hfService.searchGgufModels("qwen")
        isHfLoading = false
        statusText = "Ready. Found ${hfModelList.size} HF models."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MODEL STORAGE & EXPLORER",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF66),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Scoped Storage • GGUF Validation • HF Index",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E95A5)
                )
            }

            Surface(
                color = Color(0xFF14171E),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731))
            ) {
                Text(
                    text = "Free: ${storageInfo.freeFormatted} / ${storageInfo.totalFormatted}",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Active Download Monitor Banner (if downloading or recently completed)
        if (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.COMPLETED) {
            Surface(
                color = if (downloadState.status == DownloadStatus.DOWNLOADING) Color(0xFF161F2E) else Color(0xFF102619),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (downloadState.status == DownloadStatus.DOWNLOADING) Color(0xFF00E5FF) else Color(0xFF00FF66)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (downloadState.status == DownloadStatus.DOWNLOADING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(
                                    text = "DOWNLOADING: ${downloadState.fileName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00E5FF)
                                )
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "DOWNLOAD COMPLETE: ${downloadState.fileName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00FF66)
                                )
                            }
                        }

                        if (downloadState.status == DownloadStatus.DOWNLOADING) {
                            IconButton(
                                onClick = { modelDownloader.cancelDownload() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (downloadState.status == DownloadStatus.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { downloadState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF232731)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${downloadState.progressPercent}% (${downloadState.downloadSpeedText})",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                            Text(
                                text = "ETA: ${downloadState.etaText}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8E95A5)
                            )
                        }
                    }
                }
            }
        }

        // Sub-Tab Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val subTabs = listOf("ON-DEVICE MODELS (${localModels.size})", "HUGGING FACE", "PERMISSIONS & AUDIT")
            subTabs.forEachIndexed { index, label ->
                val isSelected = activeSubTab == index
                Surface(
                    color = if (isSelected) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFF14171E),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = index }
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Content Area based on sub-tab
        when (activeSubTab) {
            0 -> {
                // LOCAL ON-DEVICE MODELS TAB
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A2234),
                                contentColor = Color(0xFF00E5FF)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("IMPORT GGUF / ONNX", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        IconButton(
                            onClick = { refreshLocal() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
                        }
                    }

                    if (importStatusMessage.isNotBlank()) {
                        Text(
                            text = importStatusMessage,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    if (localModels.isEmpty()) {
                        Surface(
                            color = Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
                                Text(
                                    text = "No GGUF models in app sandbox yet",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Text(
                                    text = "Download a model from the 'HUGGING FACE' tab or import an existing .gguf file from your device storage.",
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8E95A5),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(localModels) { modelFile ->
                                val isSelected = config.selectedModelPath == modelFile.absolutePath

                                Surface(
                                    color = if (isSelected) Color(0xFF1A261E) else Color(0xFF14171E),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = modelFile.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isSelected) Color(0xFF00FF66) else Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { modelToDelete = modelFile },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Size: ${modelFile.formattedSize}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF00E5FF)
                                            )
                                            Text(
                                                text = "Modified: ${modelFile.lastModifiedFormatted}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF8E95A5)
                                            )
                                            Surface(
                                                color = if (modelFile.isGgufValid) Color(0xFF0D2818) else Color(0xFF2A1515),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (modelFile.isGgufValid) "GGUF v${modelFile.ggufVersion} VALID" else "BINARY",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (modelFile.isGgufValid) Color(0xFF00FF66) else Color(0xFFFF5252),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = modelFile.absolutePath,
                                                fontSize = 7.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF555D6E),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = {
                                                    onConfigChange(
                                                        config.copy(
                                                            selectedModelPath = modelFile.absolutePath,
                                                            ollamaParams = config.ollamaParams.copy(modelTag = modelFile.name.substringBefore("."))
                                                        )
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF232731),
                                                    contentColor = if (isSelected) Color(0xFF0D0E11) else Color.White
                                                ),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = if (isSelected) "LOADED" else "LOAD MODEL",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
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

            1 -> {
                // HUGGING FACE REPOSITORY TAB
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search HF models (e.g. qwen, llama, deepseek)...", fontSize = 10.5.sp, color = Color(0xFF555D6E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF66),
                                unfocusedBorderColor = Color(0xFF232731),
                                focusedContainerColor = Color(0xFF14171E),
                                unfocusedContainerColor = Color(0xFF14171E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isHfLoading = true
                                    statusText = "Searching Hugging Face for '$searchQuery'..."
                                    hfModelList = hfService.searchGgufModels(searchQuery)
                                    isHfLoading = false
                                    statusText = "Found ${hfModelList.size} models."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00FF66),
                                contentColor = Color(0xFF0D0E11)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = statusText,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )

                    if (isHfLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00FF66))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(hfModelList) { item ->
                                val isSelected = config.selectedModelPath == item.directGgufUrl || config.selectedModelPath.contains(item.modelName)
                                Surface(
                                    color = if (isSelected) Color(0xFF1A2234) else Color(0xFF14171E),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.id,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF00FF66) else Color.White,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = Color(0xFF0D0E11),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${item.downloads} DLs",
                                                    fontSize = 8.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF00E5FF),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Author: ${item.author} • Likes: ${item.likes} • Tag: GGUF",
                                            fontSize = 9.sp,
                                            color = Color(0xFF8E95A5),
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        modelDownloader.downloadModel(
                                                            modelItem = item,
                                                            onComplete = { downloadedFile ->
                                                                refreshLocal()
                                                                onConfigChange(
                                                                    config.copy(
                                                                        selectedModelPath = downloadedFile.absolutePath,
                                                                        ollamaParams = config.ollamaParams.copy(modelTag = item.modelName.lowercase())
                                                                    )
                                                                )
                                                            }
                                                        )
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF00E5FF),
                                                    contentColor = Color(0xFF0D0E11)
                                                ),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("DOWNLOAD GGUF", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    onConfigChange(
                                                        config.copy(
                                                            selectedModelPath = item.directGgufUrl,
                                                            ollamaParams = config.ollamaParams.copy(modelTag = item.modelName.lowercase())
                                                        )
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF232731),
                                                    contentColor = if (isSelected) Color(0xFF0D0E11) else Color.White
                                                ),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = if (isSelected) "LOADED" else "SET AS TARGET",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
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

            2 -> {
                // PERMISSIONS & DEVICE COMPATIBILITY AUDIT TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DEVICE & ENVIRONMENT COMPATIBILITY AUDIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )

                    // Diagnostic Card 1: Scoped Storage
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Scoped App Storage", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                Surface(color = Color(0xFF0D2818), shape = RoundedCornerShape(4.dp)) {
                                    Text("0 PERMISSION SAFE", fontSize = 8.sp, color = Color(0xFF00FF66), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                text = "Path: ${storageManager.getModelsDirectory().absolutePath}\nCompatible with Android 8 (API 26) through Android 15/16 (API 36). Fully compliant with Google Play Scoped Storage & zero broad permission rules.",
                                fontSize = 9.sp,
                                color = Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Diagnostic Card 2: Audio Sensor (RECORD_AUDIO)
                    val isRecordGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Microphone Sensor (STT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                Surface(color = if (isRecordGranted) Color(0xFF0D2818) else Color(0xFF2A1515), shape = RoundedCornerShape(4.dp)) {
                                    Text(if (isRecordGranted) "GRANTED" else "NOT GRANTED", fontSize = 8.sp, color = if (isRecordGranted) Color(0xFF00FF66) else Color(0xFFFF5252), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                text = "Required for real-time acoustic phoneme processing (RapidSpeech.cpp / Whisper.cpp).",
                                fontSize = 9.sp,
                                color = Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace
                            )
                            if (!isRecordGranted) {
                                Button(
                                    onClick = { recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color(0xFF0D0E11)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("GRANT MIC ACCESS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Diagnostic Card 3: Post Notifications (API 33+)
                    val isNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Foreground Notifications", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                Surface(color = if (isNotifGranted) Color(0xFF0D2818) else Color(0xFF2A1515), shape = RoundedCornerShape(4.dp)) {
                                    Text(if (isNotifGranted) "ACTIVE" else "DISABLED", fontSize = 8.sp, color = if (isNotifGranted) Color(0xFF00FF66) else Color(0xFFFF5252), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                text = "Enables background execution status notification for :swarm_engine_v1 process.",
                                fontSize = 9.sp,
                                color = Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace
                            )
                            if (!isNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(
                                    onClick = { postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0D0E11)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("GRANT NOTIFICATION ACCESS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Diagnostic Card 4: Cleartext Traffic & IPC
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cleartext HTTP & Multi-Process IPC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                Surface(color = Color(0xFF0D2818), shape = RoundedCornerShape(4.dp)) {
                                    Text("CONFIGURED", fontSize = 8.sp, color = Color(0xFF00FF66), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                text = "• usesCleartextTraffic: Enabled (Local Ollama & KoboldCpp bridges on 10.0.2.2/127.0.0.1)\n• Service Process: :swarm_engine_v1 isolated runtime\n• Minimum SDK: Android 8.0 (API 26) Oreo\n• Target SDK: Android 15/16 (API 36)",
                                fontSize = 9.sp,
                                color = Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Model File Deletion
    if (modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = {
                Text("Delete Model File?", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete '${modelToDelete?.name}' (${modelToDelete?.formattedSize}) to free up device storage?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF8E95A5)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = modelToDelete?.file
                        if (file != null) {
                            scope.launch {
                                storageManager.deleteModelFile(file)
                                refreshLocal()
                                modelToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("DELETE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF8E95A5))
                }
            },
            containerColor = Color(0xFF14171E)
        )
    }
}

// ------------------------------------------------------------------------------------------------
// CODING MODE & DEDICATED SANDBOX CONSOLE SCREEN
// ------------------------------------------------------------------------------------------------
@Composable
fun CodingWorkspaceScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sandboxEngine = remember { CodeSandboxEngine(context) }

    var activeSubTab by remember { mutableStateOf(0) } // 0: Editor & Copilot, 1: Sandbox Console, 2: Sandbox Config, 3: Evaluator
    var currentLanguage by remember { mutableStateOf(config.codingConfig.selectedLanguage) }
    var codeText by remember { mutableStateOf(config.codingConfig.selectedLanguage.defaultTemplate) }
    var copilotPrompt by remember { mutableStateOf("") }

    val codingLogs = remember { mutableStateListOf<CodingConsoleLine>() }
    var isExecuting by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("Coding Sandbox Ready.") }
    var lastExecutionResult by remember { mutableStateOf<CodeExecutionResult?>(null) }

    var pingStatus by remember { mutableStateOf("") }
    var isPinging by remember { mutableStateOf(false) }

    val consoleListState = rememberLazyListState()

    // Initialize with a welcome log if empty
    LaunchedEffect(Unit) {
        if (codingLogs.isEmpty()) {
            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.SYSTEM_STATUS,
                    title = "CODING SANDBOX INITIALIZED",
                    content = "Sandbox: ${config.codingConfig.selectedSandbox.displayName}\nActive Inference Model: ${config.selectedEngine.name}\nEnvironment ready for code generation, execution, and evaluation.",
                    language = currentLanguage
                )
            )
        }
    }

    // Helper to execute code in active sandbox
    fun runCodeInSandbox(codeToRun: String, lang: CodeLanguage) {
        scope.launch {
            isExecuting = true
            progressStatus = "Dispatching to ${config.codingConfig.selectedSandbox.displayName}..."
            
            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.PROMPT,
                    title = "EXECUTION TRIGGERED: ${lang.displayName}",
                    content = codeToRun,
                    language = lang
                )
            )

            val result = sandboxEngine.executeCode(codeToRun, lang, config.codingConfig)
            lastExecutionResult = result
            isExecuting = false
            progressStatus = if (result.isSuccess) "Execution completed in ${result.executionTimeMs}ms [EXIT ${result.exitCode}]" else "Execution finished with errors [EXIT ${result.exitCode}]"

            if (result.stdout.isNotBlank()) {
                codingLogs.add(
                    CodingConsoleLine(
                        type = CodingLogType.SANDBOX_STDOUT,
                        title = "STDOUT • ${result.sandboxProvider}",
                        content = result.stdout,
                        language = lang,
                        executionResult = result
                    )
                )
            }

            if (result.stderr.isNotBlank()) {
                codingLogs.add(
                    CodingConsoleLine(
                        type = CodingLogType.SANDBOX_STDERR,
                        title = "STDERR • Traceback & Diagnostics",
                        content = result.stderr,
                        language = lang,
                        executionResult = result
                    )
                )
            }

            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.EVALUATION_REPORT,
                    title = "EVALUATION VERDICT",
                    content = result.evaluationVerdict,
                    language = lang,
                    executionResult = result
                )
            )

            // Auto-switch to console on run
            activeSubTab = 1
        }
    }

    // Helper for AI Model Code Generation & Immediate Sandbox Run
    fun generateAndRun(prompt: String) {
        if (prompt.isBlank()) return
        scope.launch {
            isExecuting = true
            progressStatus = "Model ${config.selectedEngine.name} is writing code for: \"$prompt\"..."

            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.PROMPT,
                    title = "AI COPILOT PROMPT",
                    content = prompt,
                    language = currentLanguage
                )
            )

            val engine = InferenceEngineFactory.createEngine(config.selectedEngine)
            engine.initializeEngine(config)

            val systemInstruction = "You are an elite code generator. Output ONLY a clean, valid ${currentLanguage.displayName} script solving the user's request. Include print/console.log statements to demonstrate results."
            val generatedRaw = engine.executeDirectInference(
                prompt = "Task: $prompt\nLanguage: ${currentLanguage.displayName}\nCurrent Code:\n$codeText",
                systemPrompt = systemInstruction,
                config = config,
                onTokenReceived = {}
            )

            // Extract code
            val extracted = if (generatedRaw.contains("```")) {
                val afterFirst = generatedRaw.substringAfter("```")
                val codeBlock = afterFirst.substringAfter("\n").substringBeforeLast("```")
                if (codeBlock.isNotBlank()) codeBlock.trim() else generatedRaw.trim()
            } else {
                generatedRaw.trim()
            }

            codeText = extracted
            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.MODEL_CODE,
                    title = "MODEL GENERATED SCRIPT (${currentLanguage.displayName})",
                    content = extracted,
                    language = currentLanguage
                )
            )

            progressStatus = "Dispatching generated script to ${config.codingConfig.selectedSandbox.displayName}..."
            val result = sandboxEngine.executeCode(extracted, currentLanguage, config.codingConfig)
            lastExecutionResult = result
            isExecuting = false
            progressStatus = "Execution finished in ${result.executionTimeMs}ms [EXIT ${result.exitCode}]"

            if (result.stdout.isNotBlank()) {
                codingLogs.add(
                    CodingConsoleLine(
                        type = CodingLogType.SANDBOX_STDOUT,
                        title = "STDOUT • ${result.sandboxProvider}",
                        content = result.stdout,
                        language = currentLanguage,
                        executionResult = result
                    )
                )
            }

            if (result.stderr.isNotBlank()) {
                codingLogs.add(
                    CodingConsoleLine(
                        type = CodingLogType.SANDBOX_STDERR,
                        title = "STDERR • Diagnostics",
                        content = result.stderr,
                        language = currentLanguage,
                        executionResult = result
                    )
                )
            }

            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.EVALUATION_REPORT,
                    title = "EVALUATION VERDICT",
                    content = result.evaluationVerdict,
                    language = currentLanguage,
                    executionResult = result
                )
            )

            activeSubTab = 1
        }
    }

    // Helper for Autonomous Auto-Evaluate & Self-Fix Loop
    fun autoEvaluateAndFix(prompt: String) {
        scope.launch {
            isExecuting = true
            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.PROMPT,
                    title = "AUTONOMOUS SELF-CORRECTION LOOP INITIATED",
                    content = "Task: ${prompt.ifBlank { "Evaluate and optimize current script" }}\nSandbox: ${config.codingConfig.selectedSandbox.displayName}",
                    language = currentLanguage
                )
            )

            val (verifiedCode, finalResult) = sandboxEngine.runAutonomousCodeEvaluationLoop(
                taskPrompt = prompt.ifBlank { "Evaluate and optimize current script" },
                initialCode = codeText,
                language = currentLanguage,
                swarmConfig = config,
                onProgress = { stepStatus ->
                    progressStatus = stepStatus
                }
            )

            codeText = verifiedCode
            lastExecutionResult = finalResult
            isExecuting = false
            progressStatus = "Auto-Evaluation completed in ${finalResult.executionTimeMs}ms."

            codingLogs.add(
                CodingConsoleLine(
                    type = CodingLogType.MODEL_CODE,
                    title = "VERIFIED / FIXED CODE",
                    content = verifiedCode,
                    language = currentLanguage
                )
            )

            codingLogs.add(
                CodingConsoleLine(
                    type = if (finalResult.isSuccess) CodingLogType.SANDBOX_STDOUT else CodingLogType.SANDBOX_STDERR,
                    title = "SANDBOX FINAL OUT [EXIT ${finalResult.exitCode}]",
                    content = if (finalResult.isSuccess) finalResult.stdout else finalResult.stderr,
                    language = currentLanguage,
                    executionResult = finalResult
                )
            )

            activeSubTab = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header with Engine & Sandbox Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CODE LAB • NEURAL SANDBOX IDE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF66),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Model: ${config.selectedEngine.name} • Sandbox: ${config.codingConfig.selectedSandbox.displayName}",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
            }

            Surface(
                color = if (isExecuting) Color(0xFF241C10) else Color(0xFF14171E),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isExecuting) Color(0xFFFFB300) else Color(0xFF232731)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFFFFB300)
                        )
                    }
                    Text(
                        text = if (isExecuting) "RUNNING" else "SANDBOX READY",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isExecuting) Color(0xFFFFB300) else Color(0xFF00FF66)
                    )
                }
            }
        }

        // Sub-Tab Navigation Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val subTabs = listOf(
                "EDITOR & COPILOT",
                "CONSOLE (${codingLogs.size})",
                "SANDBOX CONFIG",
                "EVALUATOR & TESTS"
            )

            subTabs.forEachIndexed { index, label ->
                val isSelected = activeSubTab == index
                Surface(
                    color = if (isSelected) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFF14171E),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = index }
                ) {
                    Text(
                        text = label,
                        fontSize = 8.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color(0xFF00FF66) else Color(0xFF8E95A5),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }
            }
        }

        // Live Status Line
        Text(
            text = "Status: $progressStatus",
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isExecuting) Color(0xFFFFB300) else Color(0xFF8E95A5)
        )

        // Sub-tab Content Dispatcher
        when (activeSubTab) {
            0 -> {
                // TAB 0: CODE EDITOR & COPILOT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Language Selector Chips & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(CodeLanguage.values()) { lang ->
                                val isSelected = currentLanguage == lang
                                Surface(
                                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF14171E),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00E5FF) else Color(0xFF232731)
                                    ),
                                    modifier = Modifier.clickable {
                                        currentLanguage = lang
                                        codeText = lang.defaultTemplate
                                        onConfigChange(config.copy(codingConfig = config.codingConfig.copy(selectedLanguage = lang)))
                                    }
                                ) {
                                    Text(
                                        text = lang.displayName,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8E95A5),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { codeText = currentLanguage.defaultTemplate },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Template", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Code Editor Container with Line Numbers
                    Surface(
                        color = Color(0xFF0A0C10),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Editor Title bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF14171E))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SCRIPT: main.${currentLanguage.extension}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FF66)
                                )
                                Text(
                                    text = "${codeText.lines().size} lines • ${codeText.length} chars",
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8E95A5)
                                )
                            }

                            // Code Text Input
                            OutlinedTextField(
                                value = codeText,
                                onValueChange = { codeText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 320.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = Color(0xFFE6EDF3)
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color(0xFF0A0C10),
                                    unfocusedContainerColor = Color(0xFF0A0C10),
                                    focusedTextColor = Color(0xFFE6EDF3),
                                    unfocusedTextColor = Color(0xFFE6EDF3)
                                )
                            )
                        }
                    }

                    // AI Copilot Input & Prompt Presets
                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AI CODING COPILOT (${config.selectedEngine.name})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E5FF)
                            )

                            OutlinedTextField(
                                value = copilotPrompt,
                                onValueChange = { copilotPrompt = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Ask coding model (e.g., 'Write prime sieve & benchmark', 'Optimize with dynamic programming')...",
                                        fontSize = 10.sp,
                                        color = Color(0xFF555D6E)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color(0xFF232731),
                                    focusedContainerColor = Color(0xFF0D0E11),
                                    unfocusedContainerColor = Color(0xFF0D0E11),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                singleLine = false,
                                maxLines = 3
                            )

                            // Quick Prompt Chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val suggestions = listOf(
                                    "🚀 Benchmark Algorithm",
                                    "🧪 Write Unit Test Assertions",
                                    "🔍 Find Edge Case Bugs",
                                    "⚡ Add Memoization",
                                    "📊 Parse JSON Matrix"
                                )
                                items(suggestions) { sug ->
                                    Surface(
                                        color = Color(0xFF0D0E11),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                                        modifier = Modifier.clickable { copilotPrompt = sug.substring(2).trim() }
                                    ) {
                                        Text(
                                            text = sug,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF00E5FF),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Execution Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runCodeInSandbox(codeText, currentLanguage) },
                            enabled = !isExecuting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00FF66),
                                contentColor = Color(0xFF0D0E11)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("RUN IN SANDBOX", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { generateAndRun(copilotPrompt.ifBlank { "Optimize and execute current script" }) },
                            enabled = !isExecuting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF0D0E11)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI GENERATE & RUN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Button(
                        onClick = { autoEvaluateAndFix(copilotPrompt) },
                        enabled = !isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C4DFF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AUTONOMOUS SELF-EVALUATE & FIX LOOP", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            1 -> {
                // TAB 1: DEDICATED SANDBOX CONSOLE
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Console Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEDICATED CODE EXECUTION LOGS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66)
                            )
                            if (lastExecutionResult != null) {
                                Surface(
                                    color = if (lastExecutionResult?.isSuccess == true) Color(0xFF0D2818) else Color(0xFF2A1515),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "EXIT ${lastExecutionResult?.exitCode} (${lastExecutionResult?.executionTimeMs}ms)",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (lastExecutionResult?.isSuccess == true) Color(0xFF00FF66) else Color(0xFFFF5252),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { runCodeInSandbox(codeText, currentLanguage) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Rerun", tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { codingLogs.clear() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (codingLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Console is clear. Execute code from the 'EDITOR & COPILOT' tab.",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF555D6E)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = consoleListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF0A0C10), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF232731), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(codingLogs, key = { it.id }) { log ->
                                Surface(
                                    color = when (log.type) {
                                        CodingLogType.SANDBOX_STDOUT -> Color(0xFF0F1B14)
                                        CodingLogType.SANDBOX_STDERR -> Color(0xFF221114)
                                        CodingLogType.MODEL_CODE -> Color(0xFF121B2A)
                                        CodingLogType.EVALUATION_REPORT -> Color(0xFF1B162B)
                                        else -> Color(0xFF14171E)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        when (log.type) {
                                            CodingLogType.SANDBOX_STDOUT -> Color(0xFF00FF66).copy(alpha = 0.4f)
                                            CodingLogType.SANDBOX_STDERR -> Color(0xFFFF5252).copy(alpha = 0.5f)
                                            CodingLogType.MODEL_CODE -> Color(0xFF00E5FF).copy(alpha = 0.4f)
                                            CodingLogType.EVALUATION_REPORT -> Color(0xFFB388FF).copy(alpha = 0.4f)
                                            else -> Color(0xFF232731)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = log.title,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = when (log.type) {
                                                    CodingLogType.SANDBOX_STDOUT -> Color(0xFF00FF66)
                                                    CodingLogType.SANDBOX_STDERR -> Color(0xFFFF5252)
                                                    CodingLogType.MODEL_CODE -> Color(0xFF00E5FF)
                                                    CodingLogType.EVALUATION_REPORT -> Color(0xFFB388FF)
                                                    else -> Color.White
                                                }
                                            )
                                            Text(
                                                text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF555D6E)
                                            )
                                        }

                                        SelectionContainer {
                                            Text(
                                                text = log.content,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = when (log.type) {
                                                    CodingLogType.SANDBOX_STDOUT -> Color(0xFFE6EDF3)
                                                    CodingLogType.SANDBOX_STDERR -> Color(0xFFFF8A80)
                                                    CodingLogType.MODEL_CODE -> Color(0xFF80D8FF)
                                                    CodingLogType.EVALUATION_REPORT -> Color(0xFFD1C4E9)
                                                    else -> Color(0xFFB0B8C4)
                                                },
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // TAB 2: SANDBOX CONFIGURATION (USER-SELECTABLE LOCAL OR REMOTE)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "USER-SELECTABLE CODE EXECUTION SANDBOXES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )

                    // Sandbox Cards
                    CodeSandboxType.values().forEach { sandboxType ->
                        val isSelected = config.codingConfig.selectedSandbox == sandboxType
                        Surface(
                            color = if (isSelected) Color(0xFF14241B) else Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF00FF66) else Color(0xFF232731)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onConfigChange(
                                        config.copy(
                                            codingConfig = config.codingConfig.copy(selectedSandbox = sandboxType)
                                        )
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sandboxType.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color(0xFF00FF66) else Color.White
                                    )
                                    Surface(
                                        color = if (sandboxType.isRemote) Color(0xFF1E2838) else Color(0xFF1A2B1E),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (sandboxType.isRemote) "CLOUD MICROVM" else "LOCAL SAFE",
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (sandboxType.isRemote) Color(0xFF00E5FF) else Color(0xFF00FF66),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = sandboxType.description,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8E95A5)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Remote Sandbox API Credentials
                    if (config.codingConfig.selectedSandbox == CodeSandboxType.REMOTE_BLAXEL) {
                        Surface(
                            color = Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BLAXEL SANDBOX CONFIGURATION (blaxel.ai)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                                OutlinedTextField(
                                    value = config.codingConfig.blaxelEndpoint,
                                    onValueChange = { onConfigChange(config.copy(codingConfig = config.codingConfig.copy(blaxelEndpoint = it))) },
                                    label = { Text("Endpoint URL", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = config.codingConfig.blaxelApiKey,
                                    onValueChange = { onConfigChange(config.copy(codingConfig = config.codingConfig.copy(blaxelApiKey = it))) },
                                    label = { Text("Blaxel API Token (Optional / Workspace Key)", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    singleLine = true
                                )
                            }
                        }
                    } else if (config.codingConfig.selectedSandbox == CodeSandboxType.REMOTE_MODAL) {
                        Surface(
                            color = Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("MODAL LABS SANDBOX (modal.com)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                                OutlinedTextField(
                                    value = config.codingConfig.modalEndpoint,
                                    onValueChange = { onConfigChange(config.copy(codingConfig = config.codingConfig.copy(modalEndpoint = it))) },
                                    label = { Text("Modal Serverless Endpoint", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    singleLine = true
                                )
                            }
                        }
                    } else if (config.codingConfig.selectedSandbox == CodeSandboxType.REMOTE_GOOGLE_CLOUD) {
                        Surface(
                            color = Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("GOOGLE CLOUD RUN SANDBOX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                                OutlinedTextField(
                                    value = config.codingConfig.googleCloudEndpoint,
                                    onValueChange = { onConfigChange(config.copy(codingConfig = config.codingConfig.copy(googleCloudEndpoint = it))) },
                                    label = { Text("Cloud Functions / Cloud Run URL", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    singleLine = true
                                )
                            }
                        }
                    } else if (config.codingConfig.selectedSandbox == CodeSandboxType.REMOTE_AMAZON_AWS) {
                        Surface(
                            color = Color(0xFF14171E),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AMAZON AWS LAMBDA SANDBOX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                                OutlinedTextField(
                                    value = config.codingConfig.awsEndpoint,
                                    onValueChange = { onConfigChange(config.copy(codingConfig = config.codingConfig.copy(awsEndpoint = it))) },
                                    label = { Text("API Gateway / Lambda Endpoint", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Ping Sandbox Button
                    Button(
                        onClick = {
                            scope.launch {
                                isPinging = true
                                pingStatus = "Pinging sandbox: ${config.codingConfig.selectedSandbox.displayName}..."
                                val (ok, msg) = sandboxEngine.pingSandbox(config.codingConfig)
                                isPinging = false
                                pingStatus = msg
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A2234),
                            contentColor = Color(0xFF00E5FF)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPinging) "PINGING SANDBOX..." else "TEST SANDBOX CONNECTIVITY", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    if (pingStatus.isNotBlank()) {
                        Text(
                            text = pingStatus,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF66)
                        )
                    }
                }
            }

            3 -> {
                // TAB 3: EVALUATOR & BENCHMARK SUITE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "AI CODE EVALUATION & BENCHMARK SUITE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66)
                    )

                    Surface(
                        color = Color(0xFF14171E),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232731)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Real-Time Execution Telemetry", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            Text(
                                text = "• Sandbox Provider: ${lastExecutionResult?.sandboxProvider ?: "Not run yet"}\n• Exit Code: ${lastExecutionResult?.exitCode ?: 0}\n• Execution Latency: ${lastExecutionResult?.executionTimeMs ?: 0} ms\n• Memory Footprint: ${lastExecutionResult?.memoryUsedKb ?: 0} KB\n• Health Status: ${if (lastExecutionResult?.isSuccess == true) "PASSED" else "NEEDS EVALUATION"}",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            autoEvaluateAndFix("Benchmark script execution speed, evaluate memory bounds, and report Big-O complexity.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color(0xFF0D0E11)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("RUN BENCHMARK & EVALUATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// ACE-STEP 1.5 C++ MUSIC GENERATION STUDIO & LIBRARY UI
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStudioScreen(
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fileManager = remember { MusicFileManager(context) }
    val musicEngine = remember { AceStepMusicEngine(context, fileManager) }
    val engineState by musicEngine.state.collectAsStateWithLifecycle()

    var studioSubTab by remember { mutableStateOf(0) }
    val subTabs = listOf("STUDIO", "NOW PLAYING", "LIBRARY", "ACE-STEP SPECS")

    var savedTracks by remember { mutableStateOf(fileManager.loadAllTracks()) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    var isAiComposerLoading by remember { mutableStateOf(false) }
    var aiComposerIdeaText by remember { mutableStateOf("") }
    var showAiComposerDialog by remember { mutableStateOf(false) }

    var renameDialogTrack by remember { mutableStateOf<GeneratedTrackItem?>(null) }
    var newRenameTitle by remember { mutableStateOf("") }

    var exportNoticeMessage by remember { mutableStateOf<String?>(null) }

    // Helper to refresh track list from storage
    fun refreshTrackList() {
        savedTracks = fileManager.loadAllTracks()
    }

    Scaffold(
        containerColor = Color(0xFF090A0E)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Subtab Navigation Bar
            Surface(
                color = Color(0xFF101218),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E222D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = studioSubTab,
                    containerColor = Color(0xFF101218),
                    contentColor = Color(0xFF00FF66),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[studioSubTab]),
                            color = Color(0xFF00FF66),
                            height = 2.dp
                        )
                    }
                ) {
                    subTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = studioSubTab == index,
                            onClick = { studioSubTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 11.sp,
                                    fontWeight = if (studioSubTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (studioSubTab == index) Color(0xFF00FF66) else Color(0xFF8E95A5)
                                )
                            }
                        )
                    }
                }
            }

            // Export or Status snackbar/banner
            exportNoticeMessage?.let { msg ->
                Surface(
                    color = Color(0xFF14241C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF66)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF66),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { exportNoticeMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Active Subtab View
            when (studioSubTab) {
                0 -> AceStepGeneratorView(
                    config = config,
                    engineState = engineState,
                    isAiComposerLoading = isAiComposerLoading,
                    onConfigChange = onConfigChange,
                    onOpenAiComposer = { showAiComposerDialog = true },
                    onGenerate = {
                        coroutineScope.launch {
                            val result = musicEngine.generateMusicTrack(
                                params = config.aceStepMusicParams,
                                swarmConfig = config
                            )
                            if (result.isSuccess) {
                                refreshTrackList()
                                studioSubTab = 1 // Switch to Now Playing
                            }
                        }
                    }
                )
                1 -> AceStepNowPlayingView(
                    track = engineState.currentlyPlayingTrack ?: engineState.lastGeneratedTrack,
                    engineState = engineState,
                    config = config,
                    onConfigChange = onConfigChange,
                    onPlay = { track -> musicEngine.playTrack(track) },
                    onPause = { musicEngine.pausePlayback() },
                    onResume = { musicEngine.resumePlayback() },
                    onSeek = { pos -> musicEngine.seekTo(pos) },
                    onToggleLoop = { musicEngine.toggleLooping() },
                    onShare = { track -> fileManager.shareTrack(track, context) },
                    onExport = { track ->
                        val (success, message) = fileManager.exportTrackToPublicStorage(track)
                        exportNoticeMessage = message
                    },
                    onFavoriteToggle = { track ->
                        val updated = fileManager.toggleFavorite(track.id)
                        if (updated != null) refreshTrackList()
                    },
                    onOpenRename = { track ->
                        renameDialogTrack = track
                        newRenameTitle = track.title
                    },
                    onDelete = { track ->
                        fileManager.deleteTrack(track.id)
                        musicEngine.stopPlayback()
                        refreshTrackList()
                    }
                )
                2 -> AceStepTrackLibraryView(
                    tracks = savedTracks,
                    currentPlayingTrack = engineState.currentlyPlayingTrack,
                    isPlaying = engineState.isPlaying,
                    searchQuery = trackSearchQuery,
                    onlyFavorites = showOnlyFavorites,
                    onSearchQueryChange = { trackSearchQuery = it },
                    onToggleFavoritesFilter = { showOnlyFavorites = it },
                    onPlayTrack = { track ->
                        musicEngine.playTrack(track)
                        studioSubTab = 1
                    },
                    onShareTrack = { track -> fileManager.shareTrack(track, context) },
                    onExportTrack = { track ->
                        val (success, message) = fileManager.exportTrackToPublicStorage(track)
                        exportNoticeMessage = message
                    },
                    onFavoriteToggle = { track ->
                        fileManager.toggleFavorite(track.id)
                        refreshTrackList()
                    },
                    onOpenRename = { track ->
                        renameDialogTrack = track
                        newRenameTitle = track.title
                    },
                    onDeleteTrack = { track ->
                        fileManager.deleteTrack(track.id)
                        if (engineState.currentlyPlayingTrack?.id == track.id) {
                            musicEngine.stopPlayback()
                        }
                        refreshTrackList()
                    }
                )
                3 -> AceStepSpecsView()
            }
        }
    }

    // AI Composer Modal Dialog
    if (showAiComposerDialog) {
        AlertDialog(
            onDismissRequest = { showAiComposerDialog = false },
            containerColor = Color(0xFF12151D),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Music Producer & Composer", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Describe your song concept or theme in plain language. The active Swarm inference engine will craft an optimized ACE-Step 1.5 prompt, chords, key, BPM, and verse/chorus lyrics.",
                        fontSize = 11.sp,
                        color = Color(0xFFA0A7B5)
                    )
                    OutlinedTextField(
                        value = aiComposerIdeaText,
                        onValueChange = { aiComposerIdeaText = it },
                        placeholder = { Text("e.g., A rainy synthwave night in Neo-Tokyo with slow bass and melancholic saxophone lead", fontSize = 11.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF2B3242),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    // Quick inspiration tags
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Cyberpunk Boss Fight", "Lo-Fi Coffee Study", "Epic Movie Trailer", "80s Retrowave Drive", "Acoustic Sunset")) { tag ->
                            Surface(
                                color = Color(0xFF1E222D),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable { aiComposerIdeaText = tag }
                            ) {
                                Text(tag, fontSize = 9.5.sp, color = Color(0xFF00E5FF), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiComposerDialog = false
                        isAiComposerLoading = true
                        coroutineScope.launch {
                            try {
                                val newParams = musicEngine.generateAiComposerBlueprint(
                                    userIdea = aiComposerIdeaText.ifBlank { "Energetic electronic music" },
                                    swarmConfig = config
                                )
                                onConfigChange(config.copy(aceStepMusicParams = newParams))
                            } catch (e: Exception) {
                                Log.e("MusicStudio", "AI Composer error: ${e.message}")
                            } finally {
                                isAiComposerLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0D0E11))
                ) {
                    Text("GENERATE BLUEPRINT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiComposerDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    // Rename Track Modal Dialog
    if (renameDialogTrack != null) {
        val target = renameDialogTrack!!
        AlertDialog(
            onDismissRequest = { renameDialogTrack = null },
            containerColor = Color(0xFF12151D),
            title = {
                Text("Rename Track", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new title for this generated song:", fontSize = 11.sp, color = Color(0xFFA0A7B5))
                    OutlinedTextField(
                        value = newRenameTitle,
                        onValueChange = { newRenameTitle = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color(0xFF2B3242),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRenameTitle.isNotBlank()) {
                            fileManager.renameTrack(target.id, newRenameTitle)
                            refreshTrackList()
                        }
                        renameDialogTrack = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color(0xFF0D0E11))
                ) {
                    Text("SAVE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogTrack = null }) {
                    Text("CANCEL", color = Color.Gray, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

// ------------------------------------------------------------------------------------------------
// SUB-TAB 0: STUDIO & GENERATOR VIEW
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AceStepGeneratorView(
    config: SwarmConfig,
    engineState: AceStepEngineState,
    isAiComposerLoading: Boolean,
    onConfigChange: (SwarmConfig) -> Unit,
    onOpenAiComposer: () -> Unit,
    onGenerate: () -> Unit
) {
    val musicParams = config.aceStepMusicParams
    val isGenerating = engineState.status != AceStepEngineStatus.IDLE &&
            engineState.status != AceStepEngineStatus.COMPLETED &&
            engineState.status != AceStepEngineStatus.ERROR

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Engine Status & Telemetry Banner
        Surface(
            color = Color(0xFF10141D),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D2434)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (isGenerating) Color(0xFF00FF66) else Color(0xFF00E5FF),
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ACE-Step 1.5 C++ Audio Core",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Variant: ${musicParams.modelVariant.displayName} • Sampler: ${musicParams.sampler.displayName}",
                        fontSize = 9.5.sp,
                        color = Color(0xFF8E95A5),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = Color(0xFF1A2233),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "⚡ ARM NEON + VULKAN",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // AI Composer Assistant Banner & Quick Action
        Surface(
            color = Color(0xFF131A26),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3952)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Swarm Composer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                    Text("Auto-generate tailored prompts, chords, BPM & lyrics using LLM", fontSize = 9.5.sp, color = Color(0xFFA0AABF))
                }

                Button(
                    onClick = onOpenAiComposer,
                    enabled = !isGenerating && !isAiComposerLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0D0E11)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (isAiComposerLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI ASSIST", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Music Prompt Input Box
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Music Prompt & Style Description", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                value = musicParams.prompt,
                onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(prompt = it))) },
                modifier = Modifier.fillMaxWidth().height(90.dp),
                placeholder = { Text("Describe instruments, beats, vibe, tempo, sound design...", fontSize = 11.sp, color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF66),
                    unfocusedBorderColor = Color(0xFF232B3B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Genre Selector Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Music Genre", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MusicGenre.values()) { genre ->
                    val isSelected = musicParams.genre == genre
                    Surface(
                        color = if (isSelected) Color(0xFF00FF66) else Color(0xFF141722),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF00FF66) else Color(0xFF232B3B)),
                        modifier = Modifier.clickable {
                            onConfigChange(
                                config.copy(
                                    aceStepMusicParams = musicParams.copy(
                                        genre = genre,
                                        tempoBpm = genre.defaultBpm
                                    )
                                )
                            )
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(genre.iconTag, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                genre.displayName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D0E11) else Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Mood & Atmosphere Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mood & Aesthetic", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MusicMood.values()) { mood ->
                    val isSelected = musicParams.mood == mood
                    Surface(
                        color = if (isSelected) Color(mood.colorHex).copy(alpha = 0.25f) else Color(0xFF141722),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(mood.colorHex) else Color(0xFF232B3B)),
                        modifier = Modifier.clickable {
                            onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(mood = mood)))
                        }
                    ) {
                        Text(
                            mood.displayName,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(mood.colorHex) else Color(0xFFA0AABF),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Instrumental vs Neural Vocal Mode & Lyrics
        Surface(
            color = Color(0xFF10141E),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Vocal Synthesis & Lyrics", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        Text(
                            if (musicParams.isInstrumentalOnly) "Mode: Instrumental Only (No Vocals)" else "Mode: Neural Vocals Enabled (Lyrics active)",
                            fontSize = 9.5.sp,
                            color = if (musicParams.isInstrumentalOnly) Color(0xFF8E95A5) else Color(0xFF00FF66)
                        )
                    }
                    Switch(
                        checked = !musicParams.isInstrumentalOnly,
                        onCheckedChange = { isVocal ->
                            onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(isInstrumentalOnly = !isVocal)))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF104423)
                        )
                    )
                }

                if (!musicParams.isInstrumentalOnly) {
                    OutlinedTextField(
                        value = musicParams.lyrics,
                        onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(lyrics = it))) },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        placeholder = { Text("[Verse 1]\nLyrics here...\n\n[Chorus]\nChorus lyrics...", fontSize = 10.5.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color(0xFF232B3B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        }

        // Musical Key, Scale & Tempo Controls
        Surface(
            color = Color(0xFF10141E),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Musical Harmonic Blueprint", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)

                // Tempo BPM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tempo (BPM)", fontSize = 10.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                    Text("${musicParams.tempoBpm} BPM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = musicParams.tempoBpm.toFloat(),
                    onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(tempoBpm = it.toInt()))) },
                    valueRange = 40f..220f,
                    steps = 179,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00FF66), activeTrackColor = Color(0xFF00FF66))
                )

                // Key & Scale Dropdowns / Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Key
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Root Key", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                        var expandedKey by remember { mutableStateOf(false) }
                        Surface(
                            color = Color(0xFF161B26),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3447)),
                            modifier = Modifier.fillMaxWidth().clickable { expandedKey = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(musicParams.musicalKey.noteName, fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = expandedKey,
                                onDismissRequest = { expandedKey = false },
                                modifier = Modifier.background(Color(0xFF161B26))
                            ) {
                                MusicalKey.values().forEach { k ->
                                    DropdownMenuItem(
                                        text = { Text(k.noteName, color = Color.White, fontSize = 11.sp) },
                                        onClick = {
                                            onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(musicalKey = k)))
                                            expandedKey = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Scale
                    Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Musical Scale", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                        var expandedScale by remember { mutableStateOf(false) }
                        Surface(
                            color = Color(0xFF161B26),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3447)),
                            modifier = Modifier.fillMaxWidth().clickable { expandedScale = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(musicParams.musicalScale.scaleName, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = expandedScale,
                                onDismissRequest = { expandedScale = false },
                                modifier = Modifier.background(Color(0xFF161B26))
                            ) {
                                MusicalScale.values().forEach { sc ->
                                    DropdownMenuItem(
                                        text = { Text(sc.scaleName, color = Color.White, fontSize = 11.sp) },
                                        onClick = {
                                            onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(musicalScale = sc)))
                                            expandedScale = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Duration Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duration (Seconds)", fontSize = 10.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                    Text("${musicParams.durationSeconds}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = musicParams.durationSeconds.toFloat(),
                    onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(durationSeconds = it.toInt()))) },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )
            }
        }

        // ACE-Step 1.5 C++ Diffusion Engine Configuration
        Surface(
            color = Color(0xFF10141E),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ACE-Step 1.5 C++ Engine Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)

                // Model Variant Picker
                Text("Model Variant & Quantization", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                AceStepModelVariant.values().forEach { variant ->
                    val isSelected = musicParams.modelVariant == variant
                    Surface(
                        color = if (isSelected) Color(0xFF1C2B3E) else Color(0xFF141822),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF232A39)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(modelVariant = variant)))
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(modelVariant = variant))) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(variant.displayName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.width(6.dp))
                                    Text("(${variant.quantization})", fontSize = 9.sp, color = Color(0xFF00E5FF))
                                }
                                Text(variant.description, fontSize = 8.5.sp, color = Color(0xFF8E95A5))
                            }
                        }
                    }
                }

                // Diffusion Steps & CFG Scale
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Steps", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                            Text("${musicParams.diffusionSteps}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = musicParams.diffusionSteps.toFloat(),
                            onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(diffusionSteps = it.toInt()))) },
                            valueRange = 4f..50f,
                            steps = 45,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF00FF66), activeTrackColor = Color(0xFF00FF66))
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CFG Guidance", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                            Text("${String.format(Locale.US, "%.1f", musicParams.cfgScale)}x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = musicParams.cfgScale,
                            onValueChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(cfgScale = it))) },
                            valueRange = 1.0f..15.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                        )
                    }
                }

                // Sampler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sampler Solver", fontSize = 9.5.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                    var samplerExpanded by remember { mutableStateOf(false) }
                    Surface(
                        color = Color(0xFF161B26),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3447)),
                        modifier = Modifier.clickable { samplerExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(musicParams.sampler.displayName, fontSize = 9.5.sp, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                        DropdownMenu(
                            expanded = samplerExpanded,
                            onDismissRequest = { samplerExpanded = false },
                            modifier = Modifier.background(Color(0xFF161B26))
                        ) {
                            AceStepSampler.values().forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.displayName, color = Color.White, fontSize = 10.5.sp) },
                                    onClick = {
                                        onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(sampler = s)))
                                        samplerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Audio Loop & Stems Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Seamless Audio Loop Points", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = musicParams.audioLoopingPoints,
                        onCheckedChange = { onConfigChange(config.copy(aceStepMusicParams = musicParams.copy(audioLoopingPoints = it))) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF66), checkedTrackColor = Color(0xFF104423))
                    )
                }
            }
        }

        // Live Generation Progress Bar
        if (isGenerating || engineState.status == AceStepEngineStatus.COMPLETED) {
            Surface(
                color = Color(0xFF0F1522),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D2E49)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isGenerating) "GENERATING TRACK..." else "READY",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGenerating) Color(0xFF00FF66) else Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${(engineState.progressFraction * 100).toInt()}%",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    LinearProgressIndicator(
                        progress = { engineState.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00FF66),
                        trackColor = Color(0xFF192335)
                    )

                    Text(
                        text = engineState.statusMessage,
                        fontSize = 9.5.sp,
                        color = Color(0xFFA0AABF),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Main Generate Button
        Button(
            onClick = onGenerate,
            enabled = !isGenerating,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF66),
                contentColor = Color(0xFF0D0E11)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_music_button")
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0D0E11), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(8.dp))
                Text("DIFFUSING AUDIO...", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            } else {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("GENERATE TRACK WITH ACE-STEP 1.5", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// SUB-TAB 1: NOW PLAYING & WAVEFORM VIEW
// ------------------------------------------------------------------------------------------------

@Composable
fun AceStepNowPlayingView(
    track: GeneratedTrackItem?,
    engineState: AceStepEngineState,
    config: SwarmConfig,
    onConfigChange: (SwarmConfig) -> Unit,
    onPlay: (GeneratedTrackItem) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleLoop: () -> Unit,
    onShare: (GeneratedTrackItem) -> Unit,
    onExport: (GeneratedTrackItem) -> Unit,
    onFavoriteToggle: (GeneratedTrackItem) -> Unit,
    onOpenRename: (GeneratedTrackItem) -> Unit,
    onDelete: (GeneratedTrackItem) -> Unit
) {
    if (track == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF333E54), modifier = Modifier.size(64.dp))
                Text("No Track Currently Loaded", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                Text("Generate a new song in the Studio tab or pick one from the Library.", fontSize = 11.sp, color = Color(0xFF8E95A5), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }

    val isCurrentPlaying = engineState.isPlaying && engineState.currentlyPlayingTrack?.id == track.id
    val progressMs = if (engineState.currentlyPlayingTrack?.id == track.id) engineState.currentPlaybackPositionMs else 0
    val durationMs = (track.durationSeconds * 1000).coerceAtLeast(1000)
    val progressFraction = (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Track Header Vinyl & Title Card
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF00FF66).copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF66)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            Text("${track.genre} • ${track.bpm} BPM • Key: ${track.musicalKey}", fontSize = 10.sp, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Favorite Button
                    IconButton(
                        onClick = { onFavoriteToggle(track) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (track.isFavorite) Color(0xFFFF4081) else Color.Gray
                        )
                    }
                }

                // Interactive Glowing Waveform Visualizer
                AceStepWaveformCanvas(
                    waveformPoints = track.waveformPoints,
                    progressFraction = progressFraction,
                    isPlaying = isCurrentPlaying,
                    onSeekFraction = { frac ->
                        onSeek((frac * durationMs).toInt())
                    }
                )

                // Time Position & Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val curSec = progressMs / 1000
                    val totalSec = track.durationSeconds
                    Text(
                        String.format(Locale.US, "%02d:%02d", curSec / 60, curSec % 60),
                        fontSize = 10.sp,
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60),
                        fontSize = 10.sp,
                        color = Color(0xFF8E95A5),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Main Playback Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop Button
                    IconButton(onClick = onToggleLoop) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (engineState.isLooping) Color(0xFF00FF66) else Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Seek -10s
                    IconButton(onClick = { onSeek((progressMs - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    // Large Play / Pause
                    Surface(
                        color = Color(0xFF00FF66),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable {
                                if (isCurrentPlaying) onPause()
                                else if (engineState.currentlyPlayingTrack?.id == track.id) onResume()
                                else onPlay(track)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                                tint = Color(0xFF090A0E),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Seek +10s
                    IconButton(onClick = { onSeek((progressMs + 10000).coerceAtMost(durationMs)) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    // Rename Track
                    IconButton(onClick = { onOpenRename(track) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFFA0AABF), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // Action Toolbar: SHARE & EXPORT & MANAGE
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Track Management & Distribution", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Primary Share Button
                    Button(
                        onClick = { onShare(track) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0D0E11)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).testTag("share_track_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SHARE AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Save to Public Music Folder
                    Button(
                        onClick = { onExport(track) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283A), contentColor = Color.White),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).testTag("export_track_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EXPORT WAV", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                OutlinedButton(
                    onClick = { onDelete(track) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DELETE TRACK FROM DISK", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Detailed Track Generation Metadata
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Generation Specs & Metadata", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                Text("• File Path: ${track.filePath}", fontSize = 9.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                Text("• File Size: ${String.format(Locale.US, "%.2f", track.fileSizeBytes / (1024.0 * 1024.0))} MB", fontSize = 9.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                Text("• Audio Format: WAV 16-bit PCM @ ${track.sampleRateHz} Hz Stereo", fontSize = 9.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                Text("• Model: ${track.modelVariant} (${track.sampler})", fontSize = 9.sp, color = Color(0xFFA0AABF), fontFamily = FontFamily.Monospace)
                Text("• Seed: ${track.seed}", fontSize = 9.sp, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                Text("• Prompt: \"${track.prompt}\"", fontSize = 9.sp, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                if (track.lyrics.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Lyrics / Formants:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(track.lyrics, fontSize = 9.sp, color = Color(0xFFE0E0E0), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// SUB-TAB 2: TRACK LIBRARY & FILE MANAGER VIEW
// ------------------------------------------------------------------------------------------------

@Composable
fun AceStepTrackLibraryView(
    tracks: List<GeneratedTrackItem>,
    currentPlayingTrack: GeneratedTrackItem?,
    isPlaying: Boolean,
    searchQuery: String,
    onlyFavorites: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: (Boolean) -> Unit,
    onPlayTrack: (GeneratedTrackItem) -> Unit,
    onShareTrack: (GeneratedTrackItem) -> Unit,
    onExportTrack: (GeneratedTrackItem) -> Unit,
    onFavoriteToggle: (GeneratedTrackItem) -> Unit,
    onOpenRename: (GeneratedTrackItem) -> Unit,
    onDeleteTrack: (GeneratedTrackItem) -> Unit
) {
    val filtered = tracks.filter { t ->
        (!onlyFavorites || t.isFavorite) &&
                (searchQuery.isBlank() ||
                        t.title.contains(searchQuery, ignoreCase = true) ||
                        t.genre.contains(searchQuery, ignoreCase = true) ||
                        t.prompt.contains(searchQuery, ignoreCase = true))
    }

    val totalSizeMb = tracks.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Storage Header & Search Bar
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Tracks (${tracks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("Disk Used: ${String.format(Locale.US, "%.1f", totalSizeMb)} MB", fontSize = 9.5.sp, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search by title, genre, prompt...", fontSize = 10.5.sp, color = Color.Gray) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color(0xFF2B3448),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onToggleFavoritesFilter(!onlyFavorites) },
                        label = { Text("★ Starred", fontSize = 9.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF4081).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFFFF4081)
                        )
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF2C3549), modifier = Modifier.size(48.dp))
                    Text(
                        if (tracks.isEmpty()) "No tracks generated yet." else "No tracks match your filter.",
                        fontSize = 11.5.sp,
                        color = Color(0xFFA0AABF),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { track ->
                    val isThisTrackPlaying = isPlaying && currentPlayingTrack?.id == track.id

                    Surface(
                        color = if (isThisTrackPlaying) Color(0xFF14241F) else Color(0xFF10141F),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isThisTrackPlaying) Color(0xFF00FF66) else Color(0xFF1F2738)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Play / Pause Icon
                            Surface(
                                color = if (isThisTrackPlaying) Color(0xFF00FF66) else Color(0xFF1B2332),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onPlayTrack(track) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isThisTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isThisTrackPlaying) Color(0xFF090A0E) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Track Info
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(track.title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace, maxLines = 1)
                                    if (track.isFavorite) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    }
                                }
                                Text(
                                    "${track.genre} • ${track.durationSeconds}s • ${track.bpm} BPM • ${String.format(Locale.US, "%.1f", track.fileSizeBytes / (1024.0 * 1024.0))} MB",
                                    fontSize = 9.sp,
                                    color = Color(0xFF8E95A5),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Actions: Favorite, Share, Export, Delete
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { onFavoriteToggle(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (track.isFavorite) Color(0xFFFF4081) else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onShareTrack(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { onExportTrack(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Export", tint = Color(0xFFA0AABF), modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { onDeleteTrack(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
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
// SUB-TAB 3: ACE-STEP 1.5 C++ ARCHITECTURE SPECS VIEW
// ------------------------------------------------------------------------------------------------

@Composable
fun AceStepSpecsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ACE-Step 1.5 C++ Architecture & Capabilities", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                Text(
                    "ACE-Step 1.5 is a high-speed C++ neural music generation foundation model optimized for mobile ARM NEON vector instructions and GPU Vulkan shader compute graphs.",
                    fontSize = 10.sp,
                    color = Color(0xFFA0AABF)
                )
            }
        }

        // Feature Matrix Card
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hardware & Algorithmic Capabilities", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)

                val specs = listOf(
                    "Flow-Matching ODE Solvers" to "Optimal transport ODE formulation allows generating studio-grade music in as few as 8 to 20 integration steps (vs 1000 in classic diffusion).",
                    "ARM NEON SIMD & OpenMP" to "Direct multi-threaded 128-bit vector register acceleration for instant DSP filtering, convolution, and polyphonic voice mixing.",
                    "Vulkan / OpenCL GPU Shaders" to "Mobile GPU tensor offloading for latent mel-spectrogram cross-attention transformer layers.",
                    "Dynamic Stem Separation" to "Independent latent channels for drums, bass, leads, harmony pads, and vocal formant synthesis.",
                    "Deterministic Seed Control" to "Bit-exact reproducible musical tracks using 64-bit seed initialization.",
                    "Android Share Sheet Native Integration" to "Zero-friction audio sharing via FileProvider to WhatsApp, Telegram, Google Drive, Email, and DAW tools."
                )

                specs.forEach { (title, desc) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("• $title", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                        Text(desc, fontSize = 9.sp, color = Color(0xFF8E95A5))
                    }
                }
            }
        }

        // Quantization Benchmarks Card
        Surface(
            color = Color(0xFF10141F),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222B3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quantization & Latency Matrix", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)

                Text("• ACE-Step 1.5 Flash (Q4_K_M): 512 MB RAM | ~0.05x RTF (20x Real-time)", fontSize = 9.5.sp, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                Text("• ACE-Step 1.5 Turbo (Q8_0): 1024 MB RAM | ~0.10x RTF (10x Real-time)", fontSize = 9.5.sp, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace)
                Text("• ACE-Step 1.5 Pro (FP16): 2048 MB RAM | ~0.25x RTF (4x Real-time)", fontSize = 9.5.sp, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                Text("• ACE-Step 2.0 Studio Max: 3072 MB RAM | 48kHz Stereo Master", fontSize = 9.5.sp, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// INTERACTIVE GLOWING WAVEFORM CANVAS
// ------------------------------------------------------------------------------------------------

@Composable
fun AceStepWaveformCanvas(
    waveformPoints: List<Float>,
    progressFraction: Float,
    isPlaying: Boolean,
    onSeekFraction: (Float) -> Unit
) {
    val points = if (waveformPoints.isNotEmpty()) waveformPoints else List(60) { 0.25f }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF090B10), shape = RoundedCornerShape(6.dp))
            .clickable { /* Tap to seek */ }
    ) {
        val width = size.width
        val height = size.height
        val barCount = points.size
        val barWidth = width / barCount
        val playheadX = width * progressFraction

        for (i in points.indices) {
            val barX = i * barWidth
            val barHeight = (points[i] * height * 0.85f).coerceIn(4f, height)
            val topY = (height - barHeight) / 2f
            val isPassed = barX <= playheadX

            val barColor = if (isPassed) {
                if (isPlaying) Color(0xFF00FF66) else Color(0xFF00E5FF)
            } else {
                Color(0xFF222B3E)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(barX + 1.5f, topY),
                size = Size((barWidth - 3f).coerceAtLeast(2f), barHeight),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }

        // Draw glowing playhead vertical cursor
        drawLine(
            color = Color.White,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, height),
            strokeWidth = 2.5f
        )
    }
}


