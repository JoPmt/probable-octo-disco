package com.quantum.agent

import java.io.Serializable

enum class InferenceEngineType : Serializable {
    LLAMA_CPP,  // Native llama.cpp / GGUF local runtime
    MLC_LLM,    // WebGPU / Vulkan MLC-LLM engine
    OLLAMA,     // Ollama local/remote daemon REST & SSE bridge
    KOBOLD_CPP  // Kobold.cpp local/remote unified HTTP & SSE inference engine
}

enum class ExecutionMode : Serializable {
    AGENTIC, // Multi-agent swarm (Orchestrator -> Executor -> Analyst)
    SOLO     // Direct streaming inference straight to terminal console
}

enum class KvCachePrecision(val bitValue: Int) : Serializable {
    FP16(16), INT8(8), INT4(4)
}

enum class AudioEngineType : Serializable {
    RAPID_SPEECH_CPP, // RapidSpeech.cpp - ultra-fast unified C++ engine for streaming STT & low-latency TTS
    WHISPER_CPP,      // Whisper.cpp - GGML/C++ optimized neural automatic speech recognition
    PIPER_CPP         // Piper.cpp / Sherpa-ONNX - Fast lightweight neural VITS speech synthesizer
}

enum class AudioComputeDevice : Serializable {
    LOCAL_CPU_NEON, // Fast local CPU vector / ARM NEON instructions (Default)
    GPU_VULKAN,     // Vulkan Mobile Shader Acceleration
    OPENCL          // OpenCL Compute Pipeline
}

data class MessageLog(
    val senderRole: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

data class McpServerConfig(
    val serverName: String,
    val endpointUrl: String,
    val isEnabled: Boolean = true
) : Serializable

data class AgentToolPermissions(
    val allowSystemTools: Boolean = true,
    val allowedMcpServers: List<String> = emptyList()
) : Serializable

// Parameters specific to Llama.cpp engine
data class LlamaCppParams(
    val contextSize: Int = 2048,
    val threadCount: Int = 4,
    val cachePrecision: KvCachePrecision = KvCachePrecision.INT8,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val gpuLayers: Int = 0 // GPU layer offload count
) : Serializable

// Parameters specific to MLC-LLM engine
data class MlcLlmParams(
    val maxGenLen: Int = 1024,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val repetitionPenalty: Float = 1.0f,
    val vulkanGpuEnabled: Boolean = true,
    val convTemplate: String = "auto"
) : Serializable

// Parameters specific to Ollama engine
data class OllamaParams(
    val hostUrl: String = "http://10.0.2.2:11434",
    val modelTag: String = "llama3.2:1b",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val numCtx: Int = 2048,
    val keepAlive: String = "5m",
    val seed: Int = -1
) : Serializable

// Parameters specific to Kobold.cpp engine
data class KoboldCppParams(
    val hostUrl: String = "http://10.0.2.2:5001",
    val maxContextLength: Int = 2048,
    val maxLength: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repPen: Float = 1.1f,
    val repPenRange: Int = 256,
    val useStreaming: Boolean = true
) : Serializable

// Dedicated user-selectable sandboxes for code execution & evaluation
enum class CodeSandboxType(val displayName: String, val isRemote: Boolean, val description: String) : Serializable {
    LOCAL_PYTHON_LITE(
        "Local Python Safe Evaluator",
        false,
        "Embedded sandboxed Python mathematical, algorithmic & logic runtime with timeout safeguards."
    ),
    LOCAL_JS_EMBEDDED(
        "Local JavaScript Engine (QuickJS / V8 Lite)",
        false,
        "In-process isolated ECMAScript execution sandbox with JSON, Math, and regex bindings."
    ),
    LOCAL_POSIX_SHELL(
        "Local POSIX Subprocess Sandbox",
        false,
        "Sandboxed OS process execution with strict CPU quotas, isolated environment, and stream capture."
    ),
    REMOTE_BLAXEL(
        "Blaxel Serverless Sandbox (blaxel.ai)",
        true,
        "High-performance cloud agentic sandbox microVM with Python 3.11, Node.js 20, and fast cold-starts."
    ),
    REMOTE_MODAL(
        "Modal Labs Cloud Sandbox (modal.com)",
        true,
        "Serverless containerized cloud execution engine with high-memory environments and GPU access."
    ),
    REMOTE_GOOGLE_CLOUD(
        "Google Cloud Run / Functions Sandbox",
        true,
        "GCP isolated container execution endpoint with secure service account auth."
    ),
    REMOTE_AMAZON_AWS(
        "Amazon AWS Lambda Sandbox",
        true,
        "AWS cloud serverless sandbox endpoint with IAM / API gateway integration."
    ),
    REMOTE_CUSTOM_E2B(
        "Custom Sandbox / E2B / Webhook",
        true,
        "User-defined remote gVisor, Docker, or E2B sandbox HTTP REST endpoint."
    )
}

enum class CodeLanguage(val displayName: String, val extension: String, val defaultTemplate: String) : Serializable {
    PYTHON(
        "Python 3",
        "py",
        """# Python 3 Sandbox Script
def fibonacci_primes(limit: int):
    primes = []
    a, b = 0, 1
    while len(primes) < limit:
        a, b = b, a + b
        if a > 1 and all(a % d != 0 for d in range(2, int(a**0.5) + 1)):
            primes.append(a)
    return primes

print("Executing Fibonacci Prime Sieve...")
result = fibonacci_primes(7)
print(f"Computed Primes: {result}")
print(f"Verification Checksum: {sum(result)}")
"""
    ),
    JAVASCRIPT(
        "JavaScript (Node/ES6)",
        "js",
        """// JavaScript ES6 Sandbox Engine
function matrixMultiply(a, b) {
    const rowsA = a.length, colsA = a[0].length, colsB = b[0].length;
    const result = Array.from({ length: rowsA }, () => Array(colsB).fill(0));
    for (let i = 0; i < rowsA; i++) {
        for (let j = 0; j < colsB; j++) {
            for (let k = 0; k < colsA; k++) {
                result[i][j] += a[i][k] * b[k][j];
            }
        }
    }
    return result;
}

const matA = [[1, 2], [3, 4]];
const matB = [[5, 6], [7, 8]];
console.log("Matrix A:", JSON.stringify(matA));
console.log("Matrix B:", JSON.stringify(matB));
console.log("A x B Result:", JSON.stringify(matrixMultiply(matA, matB)));
"""
    ),
    TYPESCRIPT(
        "TypeScript",
        "ts",
        """// TypeScript Sandbox Runtime
interface AgentTask {
    id: string;
    priority: number;
    payload: string;
}

const tasks: AgentTask[] = [
    { id: "T-101", priority: 3, payload: "Parse telemetry vectors" },
    { id: "T-102", priority: 1, payload: "Calibrate neural weights" },
    { id: "T-103", priority: 2, payload: "Sync MCP tool state" }
];

tasks.sort((a, b) => a.priority - b.priority);
console.log("Prioritized Tasks:", JSON.stringify(tasks, null, 2));
"""
    ),
    BASH(
        "Bash / Shell",
        "sh",
        """#!/bin/bash
echo "== Sandboxed Shell Environment Diagnostic =="
echo "Host Kernel / OS: $(uname -s 2>/dev/null || echo 'Android Linux POSIX')"
echo "Process ID: $$"
echo "Active Date: $(date)"
echo "Memory / Limits Check: OK"
"""
    ),
    KOTLIN(
        "Kotlin Script",
        "kts",
        """// Kotlin Script Sandbox
data class ModelBenchmark(val model: String, val tokPerSec: Double, val latencyMs: Long)

val benchmarks = listOf(
    ModelBenchmark("Qwen2.5-Coder", 42.5, 23),
    ModelBenchmark("DeepSeek-R1-Distill", 38.2, 26),
    ModelBenchmark("Llama-3.2-3B", 31.8, 32)
)

val top = benchmarks.maxByOrNull { it.tokPerSec }
println("Top Model: ${'$'}{top?.model} running at ${'$'}{top?.tokPerSec} tok/s")
"""
    ),
    C_CPP(
        "C / C++ (Clang)",
        "cpp",
        """#include <stdio.h>
#include <stdint.h>

int64_t fast_collatz_steps(int64_t n) {
    int64_t steps = 0;
    while (n > 1) {
        if (n % 2 == 0) n /= 2;
        else n = 3 * n + 1;
        steps++;
    }
    return steps;
}

int main() {
    int64_t test_val = 27;
    int64_t steps = fast_collatz_steps(test_val);
    printf("Collatz Conjecture test for %ld completed in %ld steps.\n", test_val, steps);
    return 0;
}
"""
    ),
    JSON(
        "JSON Data Matrix",
        "json",
        """{
  "sandbox": {
    "engine": "Quantum-CodeLab-v2",
    "isolation": "User-Selectable",
    "supported_backends": ["Local Python Lite", "QuickJS", "Blaxel", "Modal", "Google Cloud", "AWS Lambda"],
    "metrics": {
      "avg_cold_start_ms": 14,
      "security_tier": "Sandboxed / Restrictive"
    }
  }
}"""
    )
}

data class CodeSandboxConfig(
    val selectedSandbox: CodeSandboxType = CodeSandboxType.LOCAL_PYTHON_LITE,
    val selectedLanguage: CodeLanguage = CodeLanguage.PYTHON,
    val executionTimeoutSeconds: Int = 15,
    val memoryLimitMb: Int = 512,
    val networkAccessAllowed: Boolean = true,
    val autoEvaluateWithModel: Boolean = true,
    
    // Remote sandbox API keys & endpoints
    val blaxelEndpoint: String = "https://run.blaxel.ai/v1/sandbox/exec",
    val blaxelApiKey: String = "",
    val modalEndpoint: String = "https://api.modal.com/v1/sandbox/exec",
    val modalApiKey: String = "",
    val googleCloudEndpoint: String = "https://us-central1-quantum-agent.cloudfunctions.net/code-sandbox",
    val googleCloudApiKey: String = "",
    val awsEndpoint: String = "https://execute-api.us-east-1.amazonaws.com/prod/sandbox",
    val awsApiKey: String = "",
    val customSandboxEndpoint: String = "http://10.0.2.2:8000/sandbox/exec",
    val customSandboxApiKey: String = ""
) : Serializable

data class CodeExecutionResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val executionTimeMs: Long = 0L,
    val sandboxProvider: String = "",
    val memoryUsedKb: Long = 0L,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val evaluationVerdict: String = ""
) : Serializable

enum class CodingLogType : Serializable {
    PROMPT,
    MODEL_CODE,
    SANDBOX_STDOUT,
    SANDBOX_STDERR,
    EVALUATION_REPORT,
    SYSTEM_STATUS
}

data class CodingConsoleLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: CodingLogType = CodingLogType.SYSTEM_STATUS,
    val title: String = "",
    val content: String = "",
    val language: CodeLanguage = CodeLanguage.PYTHON,
    val executionResult: CodeExecutionResult? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

// Parameters for fast C++ Speech-to-Text (STT) mic stream processing
data class SpeechSttParams(
    val engineType: AudioEngineType = AudioEngineType.RAPID_SPEECH_CPP,
    val computeDevice: AudioComputeDevice = AudioComputeDevice.LOCAL_CPU_NEON,
    val sampleRateHz: Int = 16000,
    val vadSensitivity: Float = 0.6f, // Voice Activity Detection threshold
    val beamSize: Int = 2,
    val language: String = "en",
    val autoFeedToInference: Boolean = true, // Feed mic transcription directly to active engine
    val continuousStream: Boolean = true
) : Serializable

// Parameters for fast C++ Text-to-Speech (TTS) audio synthesis
data class SpeechTtsParams(
    val engineType: AudioEngineType = AudioEngineType.RAPID_SPEECH_CPP,
    val computeDevice: AudioComputeDevice = AudioComputeDevice.LOCAL_CPU_NEON,
    val voiceModel: String = "en_US-lessac-medium",
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val autoSpeakEngineOutputs: Boolean = true, // Auto-speak inference completions/tokens
    val chunkStreamingTts: Boolean = true       // Synthesize sentences as they stream
) : Serializable

data class HuggingFaceModelItem(
    val id: String,
    val author: String,
    val modelName: String,
    val downloads: Int,
    val likes: Int,
    val lastModified: String,
    val tags: List<String>,
    val directGgufUrl: String
) : Serializable

data class SwarmConfig(
    val executionMode: ExecutionMode = ExecutionMode.AGENTIC,
    val selectedEngine: InferenceEngineType = InferenceEngineType.LLAMA_CPP,
    val maxAgents: Int = 3,
    val selectedModelPath: String = "/data/local/tmp/qwen2.5-0.5b-instruct-q8_0.gguf",
    val verbosityLevel: Int = 2,
    
    // Per-backend engine parameter bundles
    val llamaCppParams: LlamaCppParams = LlamaCppParams(),
    val mlcLlmParams: MlcLlmParams = MlcLlmParams(),
    val ollamaParams: OllamaParams = OllamaParams(),
    val koboldCppParams: KoboldCppParams = KoboldCppParams(),

    // Audio C++ backends
    val speechSttParams: SpeechSttParams = SpeechSttParams(),
    val speechTtsParams: SpeechTtsParams = SpeechTtsParams(),
    val isAudioVoiceModeActive: Boolean = false,

    // Coding Mode & Sandboxes
    val codingConfig: CodeSandboxConfig = CodeSandboxConfig(),

    // ACE-Step 1.5 C++ Music Generation Parameters
    val aceStepMusicParams: AceStepMusicParams = AceStepMusicParams(),

    // Vision Mode & Real-Time Computer Vision Engine Parameters
    val visionConfig: VisionConfig = VisionConfig(),

    // Diffusion Mode & stable-diffusion.cpp Quantized Image Generation Parameters
    val diffusionConfig: DiffusionConfig = DiffusionConfig(),

    val orchestratorPrompt: String = "You are the Coordinator. Breakdown tasks into operations.",
    val analystPrompt: String = "You are the System Analyst. Parse localized text matrices.",
    val executorPrompt: String = "You are the Hardware Executor. Interact with device system APIs.",
    val soloSystemPrompt: String = "You are a concise, helpful edge AI system assistant running natively.",
    val globalMemoryHistory: ArrayList<MessageLog> = arrayListOf(),
    val mcpServers: List<McpServerConfig> = listOf(
        McpServerConfig("Local Sensor Bridge", "http://127.0.0.1:8080/mcp", isEnabled = true),
        McpServerConfig("External Knowledge Node", "https://mcp.quantum-swarm.net/api", isEnabled = false)
    ),
    val orchestratorTools: AgentToolPermissions = AgentToolPermissions(allowSystemTools = true),
    val analystTools: AgentToolPermissions = AgentToolPermissions(allowSystemTools = false),
    val executorTools: AgentToolPermissions = AgentToolPermissions(allowSystemTools = true)
) : Serializable

// ------------------------------------------------------------------------------------------------
// ACE-STEP 1.5 C++ MUSIC GENERATION DATA MODELS & ENUMS
// ------------------------------------------------------------------------------------------------

enum class AceStepModelVariant(
    val displayName: String,
    val quantization: String,
    val parameterSize: String,
    val minVramMb: Int,
    val description: String
) : Serializable {
    ACE_STEP_1_5_FLASH_Q4(
        "ACE-Step 1.5 Flash (Q4_K_M)",
        "4-bit Quantized GGML",
        "1.2B Params",
        512,
        "Ultra-low latency mobile flow-matching model (Fastest on ARM NEON CPU / Mobile GPU)"
    ),
    ACE_STEP_1_5_TURBO_Q8(
        "ACE-Step 1.5 Turbo (Q8_0)",
        "8-bit High Fidelity",
        "1.8B Params",
        1024,
        "Balanced studio transformer with enhanced dynamic range and rich stereo spatialization"
    ),
    ACE_STEP_1_5_PRO_FP16(
        "ACE-Step 1.5 Pro (FP16)",
        "16-bit Full Precision",
        "2.4B Params",
        2048,
        "High-fidelity acoustic diffusion with complex polyphonic harmonics and instrument separation"
    ),
    ACE_STEP_2_0_STUDIO_MAX(
        "ACE-Step 2.0 Studio Max (ODE-HD)",
        "48kHz Dual-Transformer",
        "3.2B Params",
        3072,
        "Next-generation multi-track neural music generation with vocal formant resonance & master limiter"
    )
}

enum class AceStepSampler(val displayName: String, val odeOrder: Int, val description: String) : Serializable {
    FLOW_MATCHING_ODE("Flow-Matching ODE (Fast 8-Step)", 1, "Optimal transport ODE sampler for instant generation"),
    DPM_PLUS_PLUS_2M("DPM++ 2M Karras", 2, "Second-order multi-step diffusion solver for intricate acoustic textures"),
    EULER_ANCESTRAL("Euler Ancestral (Stochastic)", 1, "Classic ancestral sampler offering rich harmonic variations"),
    HEUN_2ND_ORDER("Heun 2nd-Order Predictor", 2, "High-accuracy numerical integrator for clean transients"),
    UNIPC("UniPC Unified Predictor", 2, "Fast multi-step convergence for cinematic soundscapes")
}

enum class MusicGenre(val displayName: String, val defaultBpm: Int, val defaultKey: String, val iconTag: String) : Serializable {
    CYBERPUNK_SYNTHWAVE("Cyberpunk Synthwave", 128, "F Minor", "⚡"),
    LOFI_CHILL_BEATS("Lo-Fi Chillhop Beats", 84, "C Major", "☕"),
    CINEMATIC_ORCHESTRAL("Cinematic Epic Orchestral", 110, "D Minor", "🎻"),
    EDM_PROGRESSIVE_HOUSE("EDM / Progressive House", 126, "A Minor", "🎛️"),
    AMBIENT_SPACE_DRONE("Ambient Deep Space", 65, "G Major", "🌌"),
    ACOUSTIC_INDIE_FOLK("Acoustic Indie Folk", 98, "G Major", "🎸"),
    RETRO_8BIT_CHIPTUNE("Retro 8-Bit Chiptune", 145, "C Major", "👾"),
    NEO_SOUL_JAZZ("Neo-Soul & Smooth Jazz", 92, "Eb Major", "🎷"),
    TRAP_HIPHOP("Future Trap / Hip-Hop", 140, "C# Minor", "🔥"),
    PIANO_NEOCLASSICAL("Neoclassical Solo Piano", 72, "A Minor", "🎹"),
    SYNTH_POP_80S("80s Neon Synth-Pop", 120, "D Major", "🪩"),
    VOCAL_FUTURE_BASS("Vocal Future Bass", 150, "F# Minor", "🎤")
}

enum class MusicMood(val displayName: String, val colorHex: Long) : Serializable {
    EUPHORIC("Euphoric & Uplifting", 0xFF00FF66),
    DARK_CYBERNETIC("Dark & Cybernetic", 0xFF00E5FF),
    DREAMY_NOSTALGIC("Dreamy & Nostalgic", 0xFFB388FF),
    ENERGETIC_PUMPING("High Energy & Pumping", 0xFFFF9100),
    MELANCHOLIC_EMOTIONAL("Melancholic & Reflective", 0xFF448AFF),
    MYSTERIOUS_CINEMATIC("Mysterious & Ominous", 0xFFFF5252),
    RELAXING_MEDITATIVE("Relaxing & Meditative", 0xFF69F0AE)
}

enum class MusicalKey(val noteName: String) : Serializable {
    C("C"), C_SHARP("C# / Db"), D("D"), D_SHARP("D# / Eb"),
    E("E"), F("F"), F_SHARP("F# / Gb"), G("G"),
    G_SHARP("G# / Ab"), A("A"), A_SHARP("A# / Bb"), B("B")
}

enum class MusicalScale(val scaleName: String) : Serializable {
    MAJOR("Major (Ionian)"),
    MINOR("Natural Minor (Aeolian)"),
    HARMONIC_MINOR("Harmonic Minor"),
    DORIAN("Dorian (Chill/Jazz)"),
    MIXOLYDIAN("Mixolydian (Groovy/Blues)"),
    PENTATONIC("Pentatonic (Melodic)"),
    BLUES_HEXATONIC("Blues Hexatonic")
}

enum class TimeSignature(val display: String) : Serializable {
    FOUR_FOUR("4/4 Common Time"),
    THREE_FOUR("3/4 Waltz Time"),
    SIX_EIGHT("6/8 Compound Meter"),
    SEVEN_EIGHT("7/8 Complex Meter")
}

data class AceStepMusicParams(
    val prompt: String = "Epic cyberpunk synthwave with analog bassline, lush retro pads, arpeggiated leads, and punchy 80s drums",
    val negativePrompt: String = "low quality, distorted, clipping, muddy low-end, tinny, out of tune, harsh noise",
    val genre: MusicGenre = MusicGenre.CYBERPUNK_SYNTHWAVE,
    val mood: MusicMood = MusicMood.DARK_CYBERNETIC,
    val customTags: String = "analog synth, arpeggio, neon atmosphere, tape warmth, sidechain compression",
    val tempoBpm: Int = 128,
    val musicalKey: MusicalKey = MusicalKey.F,
    val musicalScale: MusicalScale = MusicalScale.MINOR,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val durationSeconds: Int = 30, // 5s to 180s
    val sampleRateHz: Int = 44100, // 24000, 32000, 44100, 48000
    val diffusionSteps: Int = 20, // 4 to 100
    val cfgScale: Float = 7.0f, // 1.0 to 20.0
    val modelVariant: AceStepModelVariant = AceStepModelVariant.ACE_STEP_1_5_TURBO_Q8,
    val sampler: AceStepSampler = AceStepSampler.FLOW_MATCHING_ODE,
    val computeDevice: AudioComputeDevice = AudioComputeDevice.LOCAL_CPU_NEON,
    val threadCount: Int = 4,
    val seed: Long = -1L, // -1 for random
    val isInstrumentalOnly: Boolean = true,
    val lyrics: String = "[Intro]\n(Synth pulses in the dark)\n\n[Verse 1]\nNeon reflections across the chrome street\nPulse of the quantum swarm under our feet\n\n[Chorus]\nBinary horizon, signals ignite\nWe ride the frequencies through the night\n\n[Outro]\nFading to digital dust...",
    val enableStemsSeparation: Boolean = false,
    val arrangementStructure: String = "Intro (4b) -> Verse (8b) -> Chorus (8b) -> Drop (8b) -> Outro (4b)",
    val audioLoopingPoints: Boolean = true,
    val stereoWidth: Float = 1.2f, // 0.0 (Mono) to 2.0 (Ultra-Wide)
    val masterReverb: Float = 0.35f,
    val masterBassBoostDb: Float = 2.5f
) : Serializable

data class GeneratedTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int,
    val sampleRateHz: Int,
    val bpm: Int,
    val musicalKey: String,
    val prompt: String,
    val lyrics: String = "",
    val genre: String,
    val modelVariant: String,
    val sampler: String,
    val seed: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val waveformPoints: List<Float> = emptyList()
) : Serializable
