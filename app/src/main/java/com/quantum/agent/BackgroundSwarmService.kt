package com.quantum.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BackgroundSwarmService : Service() {
    private val CHANNEL_ID = "swarm_service_runtime_channel"
    private val NOTIFICATION_ID = 4001
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var currentEngine: ModelInferenceEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val initialNotification = buildNotification("Inference engine process active (:swarm_engine_v1)...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        acquireWakeLock()

        @Suppress("DEPRECATION")
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra("CONFIG_KEY", SwarmConfig::class.java)
        } else {
            intent?.getSerializableExtra("CONFIG_KEY") as? SwarmConfig
        } ?: SwarmConfig()

        val prompt = intent?.getStringExtra("PROMPT_KEY") ?: ""

        if (prompt.isNotBlank()) {
            serviceScope.launch {
                val engineName = when (config.selectedEngine) {
                    InferenceEngineType.LLAMA_CPP -> "Llama.cpp (Local GGUF)"
                    InferenceEngineType.MLC_LLM -> "MLC-LLM (Vulkan/WebGPU)"
                    InferenceEngineType.OLLAMA -> "Ollama (${config.ollamaParams.modelTag})"
                    InferenceEngineType.KOBOLD_CPP -> "Kobold.cpp (${config.koboldCppParams.hostUrl})"
                }

                if (config.executionMode == ExecutionMode.SOLO) {
                    emitBroadcastSystemStatus("Engaging Direct Solo Inference mode via $engineName...")
                    val engine = InferenceEngineFactory.createEngine(config.selectedEngine)
                    currentEngine = engine
                    engine.initializeEngine(config)

                    val fullOutput = StringBuilder()
                    engine.executeDirectInference(
                        prompt = prompt,
                        systemPrompt = config.soloSystemPrompt,
                        config = config,
                        onTokenReceived = { token ->
                            fullOutput.append(token)
                            emitBroadcastStreamChunk(token)
                        }
                    )

                    emitBroadcastStreamComplete(fullOutput.toString())
                    emitBroadcastSystemStatus("Direct solo inference streaming completed via $engineName.")
                } else {
                    emitBroadcastSystemStatus("Background Agentic Swarm Engine allocated via $engineName (:swarm_engine_v1)")
                    val orchestrator = SwarmOrchestrator(
                        context = applicationContext,
                        config = config,
                        onTelemetryEmit = { r, th, tl, p -> emitBroadcastTelemetry(r, th, tl, p) },
                        onSystemStatusEmit = { s -> emitBroadcastSystemStatus(s) }
                    )
                    orchestrator.coordinateSwarmExecution(prompt)
                }

                stopCleanupSequence()
            }
        } else {
            stopCleanupSequence()
        }

        return START_NOT_STICKY
    }

    private fun emitBroadcastStreamChunk(chunk: String) {
        val intent = Intent(SwarmBroadcastContract.ACTION_SWARM_TELEMETRY).apply {
            setPackage(packageName)
            putExtra(SwarmBroadcastContract.EXTRA_IS_STREAMING_CHUNK, true)
            putExtra(SwarmBroadcastContract.EXTRA_STREAM_CHUNK, chunk)
        }
        sendBroadcast(intent)
    }

    private fun emitBroadcastStreamComplete(fullText: String) {
        val intent = Intent(SwarmBroadcastContract.ACTION_SWARM_TELEMETRY).apply {
            setPackage(packageName)
            putExtra(SwarmBroadcastContract.EXTRA_STREAM_COMPLETE, true)
            putExtra(SwarmBroadcastContract.EXTRA_STREAM_CHUNK, fullText)
        }
        sendBroadcast(intent)
    }

    private fun emitBroadcastTelemetry(role: String, thought: String, tool: String, params: String) {
        val intent = Intent(SwarmBroadcastContract.ACTION_SWARM_TELEMETRY).apply {
            setPackage(packageName)
            putExtra(SwarmBroadcastContract.EXTRA_ROLE, role)
            putExtra(SwarmBroadcastContract.EXTRA_THOUGHT, thought)
            putExtra(SwarmBroadcastContract.EXTRA_TOOL, tool)
            putExtra(SwarmBroadcastContract.EXTRA_PARAMS, params)
        }
        sendBroadcast(intent)
    }

    private fun emitBroadcastSystemStatus(msg: String) {
        val intent = Intent(SwarmBroadcastContract.ACTION_SWARM_TELEMETRY).apply {
            setPackage(packageName)
            putExtra(SwarmBroadcastContract.EXTRA_SYSTEM_STATUS, msg)
        }
        sendBroadcast(intent)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuantumSwarm::ProcessingWakeLock").apply {
                setReferenceCounted(false)
                acquire(20 * 60 * 1000L)
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quantum Swarm Engine")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Runtime Services Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background LLM inference and multi-agent execution"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopCleanupSequence() {
        serviceScope.cancel()
        try {
            if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
        } catch (e: Exception) {}
        currentEngine?.deallocateEngine()
        NativeEngine().deallocateEngine()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCleanupSequence()
        super.onDestroy()
    }
}
