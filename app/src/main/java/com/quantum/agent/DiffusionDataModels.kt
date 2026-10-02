package com.quantum.agent

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import java.io.Serializable

// ------------------------------------------------------------------------------------------------
// DIFFUSION MODES & GENERATION PARADIGMS
// ------------------------------------------------------------------------------------------------

enum class DiffusionMode(
    val title: String,
    val shortName: String,
    val description: String,
    val iconEmoji: String
) : Serializable {
    TXT2IMG(
        "Text to Image",
        "txt2img",
        "Generate high-fidelity imagery from natural language prompts using quantized diffusion weights.",
        "🎨"
    ),
    IMG2IMG(
        "Image to Image",
        "img2img",
        "Transform, restyle, or re-render an input photograph or sketch based on prompt & denoising strength.",
        "🖼️"
    ),
    INPAINT(
        "Image Inpaint / Edit",
        "inpaint",
        "Targeted brush masking & neural replacement of specific regions within an image.",
        "🖌️"
    ),
    OUTPAINT(
        "Image Outpaint / Expand",
        "outpaint",
        "Expand canvas boundaries seamlessly in directional axes (Left, Right, Top, Bottom, or 360° Panorama).",
        "📐"
    ),
    CONTROLNET(
        "ControlNet Guidance",
        "controlnet",
        "Structure-conditioned generation guided by Canny edges, Depth maps, OpenPose skeletons, or Scribbles.",
        "🕸️"
    ),
    UPSCALE(
        "Neural Super-Resolution",
        "upscale",
        "2x / 4x Latent and ESRGAN super-resolution upscaling with tile-based memory optimization.",
        "🔍"
    )
}

// ------------------------------------------------------------------------------------------------
// MODEL ARCHITECTURES & FOUNDATION WEIGHTS
// ------------------------------------------------------------------------------------------------

enum class DiffusionModelArchitecture(
    val displayName: String,
    val baseFamily: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val recommendedSteps: Int,
    val recommendedCfg: Float,
    val description: String
) : Serializable {
    STABLE_DIFFUSION_1_5(
        "Stable Diffusion 1.5 (GGML/GGUF)",
        "SD 1.5",
        512, 512,
        20, 7.0f,
        "Classic lightweight 860M UNet. Ultra-fast inference on edge CPU/GPU with massive LoRA ecosystem."
    ),
    STABLE_DIFFUSION_2_1(
        "Stable Diffusion 2.1 (768px)",
        "SD 2.1",
        768, 768,
        25, 7.5f,
        "OpenCLIP-based 768px architecture with crisp textural details and vibrant landscapes."
    ),
    SDXL_1_0_BASE(
        "SDXL 1.0 Base (3.5B Dual-CLIP)",
        "SDXL",
        1024, 1024,
        28, 6.5f,
        "Dual text-encoder 3.5B foundation model with rich aesthetics, human anatomy, and photorealism."
    ),
    SDXL_TURBO(
        "SDXL Turbo (1-4 Steps ADD)",
        "SDXL-Turbo",
        512, 512,
        4, 1.5f,
        "Adversarial Diffusion Distillation (ADD) enabling real-time generation in 1 to 4 steps."
    ),
    SD_3_5_MEDIUM(
        "SD 3.5 Medium (2.5B MMDiT)",
        "SD 3.5",
        1024, 1024,
        24, 4.5f,
        "Multimodal Diffusion Transformer (MMDiT) with exceptional prompt fidelity and in-image typography."
    ),
    FLUX_1_SCHNELL(
        "Flux.1 Schnell (4-Step Flow)",
        "Flux.1",
        1024, 1024,
        4, 3.5f,
        "12B Rectified Flow Transformer with state-of-the-art text rendering and cinematic photorealism."
    ),
    PIXART_SIGMA(
        "PixArt-Σ (DiT 4K Transformer)",
        "PixArt",
        1024, 1024,
        20, 4.0f,
        "Efficient DiT architecture trained with T5 encoder for ultra-fast high-resolution synthesis."
    ),
    LCM_DREAMSHAPER(
        "LCM DreamShaper v8 (Fast 4-Step)",
        "LCM",
        512, 512,
        4, 2.0f,
        "Latent Consistency Model for sub-second edge generation with minimal compute overhead."
    ),
    PLAYGROUND_V2_5(
        "Playground v2.5 Aesthetic",
        "Playground",
        1024, 1024,
        25, 3.0f,
        "Aesthetic-tuned diffusion architecture specialized in portraiture, lighting, and vivid colors."
    )
}

// ------------------------------------------------------------------------------------------------
// GGML / GGUF QUANTIZATION TYPES
// ------------------------------------------------------------------------------------------------

enum class DiffusionQuantization(
    val typeCode: String,
    val bitsPerWeight: Float,
    val ramEstimateMb: Int,
    val description: String
) : Serializable {
    Q4_0("Q4_0", 4.5f, 650, "Standard 4-bit block quantization (Fastest CPU throughput)"),
    Q4_1("Q4_1", 5.0f, 720, "4-bit quantization with bias scaling (Better dynamic range)"),
    Q5_0("Q5_0", 5.5f, 850, "5-bit balanced quantization for crisp edges"),
    Q5_1("Q5_1", 6.0f, 920, "5-bit with bias scales (High clarity)"),
    Q8_0("Q8_0", 8.5f, 1350, "8-bit quantization (Near-lossless studio fidelity)"),
    Q4_K_M("Q4_K_M", 4.8f, 700, "K-quants medium matrix quantization (Optimal speed/quality)"),
    Q5_K_M("Q5_K_M", 5.8f, 880, "5-bit K-quants with high precision attention tensors"),
    Q6_K("Q6_K", 6.6f, 1050, "6-bit K-quants studio accuracy"),
    F16("FP16", 16.0f, 2100, "Half-precision 16-bit floating point"),
    F32("FP32", 32.0f, 4200, "Full 32-bit floating point precision")
}

// ------------------------------------------------------------------------------------------------
// SAMPLING ALGORITHMS & SOLVERS
// ------------------------------------------------------------------------------------------------

enum class DiffusionSampler(
    val displayName: String,
    val order: Int,
    val isStochastic: Boolean,
    val description: String
) : Serializable {
    EULER("Euler", 1, false, "Standard 1st-order solver with smooth monotonic convergence"),
    EULER_A("Euler Ancestral (Euler A)", 1, true, "Stochastic ancestral solver providing rich creative variations"),
    DPM_PLUS_PLUS_2M_KARRAS("DPM++ 2M Karras", 2, false, "Second-order multi-step solver with Karras noise scheduling"),
    DPM_PLUS_PLUS_SDE_KARRAS("DPM++ SDE Karras", 2, true, "Stochastic Differential Equation solver for photorealistic micro-details"),
    HEUN("Heun 2nd-Order", 2, false, "High-accuracy numerical predictor-corrector for crisp lines"),
    DDIM("DDIM (Denoising Implicit)", 1, false, "Deterministic implicit diffusion for fast predictable rendering"),
    LCM("LCM (Latent Consistency)", 1, false, "Distilled consistency solver for ultra-fast 2 to 6 step generation"),
    FLOW_MATCHING_EULER("Euler Flow-Matching ODE", 1, false, "Continuous normalizing flow solver tailored for Flux and SD3.5"),
    UNIPC("UniPC Unified Predictor", 2, false, "Fast multi-step convergence in as few as 10-14 steps")
}

// ------------------------------------------------------------------------------------------------
// VAE (AUTOENCODER) TYPES
// ------------------------------------------------------------------------------------------------

enum class DiffusionVaeType(
    val displayName: String,
    val decodeLatencyMs: Long,
    val description: String
) : Serializable {
    TAESD("TAESD (Tiny AutoEncoder - Realtime Preview)", 4, "Sub-millisecond latent decoding enabling live step-by-step preview"),
    VAE_FP16("Standard SD VAE (FP16)", 48, "Full precision standard latent autoencoder with rich dynamic range"),
    VAE_Q8_0("Quantized VAE (Q8_0)", 22, "8-bit quantized autoencoder with reduced VRAM footprint"),
    SDXL_VAE_FP16("SDXL High-Bit VAE (FP16)", 65, "Specialized 1024px autoencoder tuned for SDXL architecture"),
    FLUX_AE_16CH("Flux 16-Channel AE (Q8_0)", 78, "High-capacity 16-channel spatial autoencoder for Flux models")
}

// ------------------------------------------------------------------------------------------------
// COMPUTE BACKENDS & HARDWARE ACCELERATORS
// ------------------------------------------------------------------------------------------------

enum class DiffusionComputeBackend(
    val displayName: String,
    val hardwareTarget: String,
    val description: String
) : Serializable {
    CPU_ARM_NEON("CPU (ARM NEON / AVX2)", "Multi-Threaded SIMD", "Universal CPU execution with ARM NEON vector instructions"),
    GPU_VULKAN("Vulkan Mobile GPU (SPIR-V)", "Mobile Adreno/Mali", "High-throughput GPU compute shaders via Vulkan API"),
    GPU_OPENCL("OpenCL Mobile Accelerator", "Adreno / Immortalis", "Optimized OpenCL matrix-multiplication kernels"),
    NPU_QUALCOMM_QNN("NPU (Snapdragon HTP / QNN)", "Hardware NPU", "Ultra-low power neural processing unit hardware offload")
}

// ------------------------------------------------------------------------------------------------
// CONTROLNET PREPROCESSORS & CONDITIONING
// ------------------------------------------------------------------------------------------------

enum class DiffusionControlNetType(
    val displayName: String,
    val conditionTarget: String,
    val description: String
) : Serializable {
    NONE("None (Standard Diffusion)", "None", "No structural conditioning applied"),
    CANNY_EDGE("Canny Edge Detector", "Edge Contours", "Extracts hard edges and structural silhouettes from reference image"),
    DEPTH_MAP("Depth Map (MiDaS/DepthAnything)", "3D Geometry", "Preserves 3D spatial depth, foreground/background separation"),
    OPENPOSE("OpenPose Human Skeleton", "Keypoint Anatomy", "Detects and locks human posture, body orientation, and gestures"),
    SCRIBBLE("Scribble / Rough Sketch", "Hand-Drawn Lines", "Transforms coarse sketches and wireframes into finished artwork"),
    LINEART("LineArt / Detailed Anime", "Contour Drawing", "Fine vector lineart conditioning for anime, concept art, and comics"),
    SOFTEDGE("SoftEdge (HED / PIDI)", "Soft Boundaries", "Extracts smooth gradients and natural boundary transitions")
}

// ------------------------------------------------------------------------------------------------
// OUTPAINTING DIRECTION & EXTENSION AXIS
// ------------------------------------------------------------------------------------------------

enum class DiffusionOutpaintDirection(
    val displayName: String,
    val axisEmoji: String,
    val leftExpand: Boolean,
    val rightExpand: Boolean,
    val topExpand: Boolean,
    val bottomExpand: Boolean
) : Serializable {
    ALL_DIRECTIONS("All 4 Directions (360° Panorama)", "🌐", true, true, true, true),
    HORIZONTAL("Horizontal (Left & Right)", "↔️", true, true, false, false),
    VERTICAL("Vertical (Top & Bottom)", "↕️", false, false, true, true),
    LEFT_ONLY("Leftward Expansion", "⬅️", true, false, false, false),
    RIGHT_ONLY("Rightward Expansion", "➡️", false, true, false, false),
    TOP_ONLY("Upward Extension", "⬆️", false, false, true, false),
    BOTTOM_ONLY("Downward Extension", "⬇️", false, false, false, true)
}

// ------------------------------------------------------------------------------------------------
// ASPECT RATIO PRESETS
// ------------------------------------------------------------------------------------------------

data class AspectRatioPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val description: String
) : Serializable

val DIFFUSION_ASPECT_RATIOS = listOf(
    AspectRatioPreset("1:1 Square (512x512)", 512, 512, "Classic balanced square"),
    AspectRatioPreset("1:1 HD Square (1024x1024)", 1024, 1024, "SDXL & Flux native HD square"),
    AspectRatioPreset("16:9 Landscape (910x512)", 910, 512, "Widescreen cinematic view"),
    AspectRatioPreset("9:16 Portrait (512x910)", 512, 910, "Mobile vertical story format"),
    AspectRatioPreset("4:3 Standard (680x512)", 680, 512, "Traditional photo landscape"),
    AspectRatioPreset("3:4 Portrait (512x680)", 512, 680, "Classic portrait orientation"),
    AspectRatioPreset("21:9 Ultra-Wide (1024x440)", 1024, 440, "Ultra-panoramic cinematic frame")
)

// ------------------------------------------------------------------------------------------------
// INPAINT BRUSH POINT FOR TOUCH CANVAS
// ------------------------------------------------------------------------------------------------

data class BrushStroke(
    val points: List<Offset>,
    val strokeWidth: Float = 28f,
    val isEraser: Boolean = false
)

// ------------------------------------------------------------------------------------------------
// DIFFUSION CONFIGURATION
// ------------------------------------------------------------------------------------------------

data class DiffusionConfig(
    val activeMode: DiffusionMode = DiffusionMode.TXT2IMG,
    val prompt: String = "A high-tech cyberpunk quantum computing laboratory with glowing neon holographic interfaces, volumetric particle dust, cinematic lighting, 8k masterpiece, octane render",
    val negativePrompt: String = "ugly, deformed, noisy, blurry, distorted, out of focus, bad anatomy, extra limbs, low quality, artifacts, watermark",
    val modelArchitecture: DiffusionModelArchitecture = DiffusionModelArchitecture.STABLE_DIFFUSION_1_5,
    val quantization: DiffusionQuantization = DiffusionQuantization.Q4_0,
    val sampler: DiffusionSampler = DiffusionSampler.DPM_PLUS_PLUS_2M_KARRAS,
    val vaeType: DiffusionVaeType = DiffusionVaeType.TAESD,
    val computeBackend: DiffusionComputeBackend = DiffusionComputeBackend.CPU_ARM_NEON,
    val threadCount: Int = 4,
    val width: Int = 512,
    val height: Int = 512,
    val inferenceSteps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Long = -1L, // -1 for random
    val denoisingStrength: Float = 0.75f, // for img2img, inpaint, outpaint
    val clipSkip: Int = 1,
    val enableFlashAttention: Boolean = true,
    val offloadVaeToCpu: Boolean = false,
    val freeMemoryAfterGen: Boolean = true,
    val enableLiveLatentPreview: Boolean = true,
    
    // ControlNet conditioning
    val controlNetType: DiffusionControlNetType = DiffusionControlNetType.NONE,
    val controlNetStrength: Float = 0.90f,
    val controlNetStartStepRatio: Float = 0.0f,
    val controlNetEndStepRatio: Float = 0.85f,

    // LoRA Adapter
    val loraModelName: String = "",
    val loraWeight: Float = 0.80f,

    // Outpainting
    val outpaintDirection: DiffusionOutpaintDirection = DiffusionOutpaintDirection.ALL_DIRECTIONS,
    val outpaintExpandPixels: Int = 128,
    val outpaintEdgeFeathering: Int = 16,

    // Inpainting
    val inpaintBrushSize: Float = 32f,
    val inpaintMaskBlur: Float = 4f,
    val inpaintFillMode: String = "Original + Latent Noise",

    // Super-Resolution
    val upscaleFactor: Int = 2, // 2x or 4x
    val upscaleDenoise: Float = 0.35f,

    // Custom Model Path
    val customModelPath: String = ""
) : Serializable

// ------------------------------------------------------------------------------------------------
// GENERATED IMAGE ITEM & METADATA
// ------------------------------------------------------------------------------------------------

data class GeneratedDiffusionImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val width: Int,
    val height: Int,
    val prompt: String,
    val negativePrompt: String,
    val mode: DiffusionMode,
    val modelName: String,
    val quantization: String,
    val sampler: String,
    val steps: Int,
    val cfgScale: Float,
    val seed: Long,
    val denoise: Float,
    val inferenceTimeMs: Long,
    val peakVramMb: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    @Transient var bitmap: Bitmap? = null
) : Serializable

// ------------------------------------------------------------------------------------------------
// TELEMETRY & LOG EVENTS
// ------------------------------------------------------------------------------------------------

data class DiffusionConsoleLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: String = "INFO" // INFO, DEBUG, WARN, PERF
) : Serializable

// ------------------------------------------------------------------------------------------------
// PROMPT TEMPLATES & INSPIRATIONS
// ------------------------------------------------------------------------------------------------

data class PromptPresetTemplate(
    val title: String,
    val category: String,
    val prompt: String,
    val negativePrompt: String,
    val recommendedSampler: DiffusionSampler,
    val recommendedCfg: Float,
    val recommendedSteps: Int,
    val emoji: String
)

val DIFFUSION_PROMPT_PRESETS = listOf(
    PromptPresetTemplate(
        title = "Cyberpunk Quantum Core",
        category = "Sci-Fi & Cyber",
        prompt = "Futuristic quantum neural processor in a cybernetic server room, glowing neon cyan and emerald light conduits, hyper-detailed circuit pathways, volumetric god rays, 8k resolution, octane render, unreal engine 5",
        negativePrompt = "blurry, low resolution, artifacts, poorly drawn, oversaturated, deformed",
        recommendedSampler = DiffusionSampler.DPM_PLUS_PLUS_2M_KARRAS,
        recommendedCfg = 7.0f,
        recommendedSteps = 20,
        emoji = "⚡"
    ),
    PromptPresetTemplate(
        title = "Bioluminescent Forest Sanctuary",
        category = "Nature & Fantasy",
        prompt = "Enchanted ancient rainforest at twilight with glowing bioluminescent mushrooms, crystalline flora, ethereal misty atmosphere, glowing fireflies, raytraced lighting, ultra-detailed textures",
        negativePrompt = "ugly, dark, murky, low quality, artifacts, watermark",
        recommendedSampler = DiffusionSampler.EULER_A,
        recommendedCfg = 7.5f,
        recommendedSteps = 24,
        emoji = "🌿"
    ),
    PromptPresetTemplate(
        title = "Studio Cinematic Portrait",
        category = "Portraiture",
        prompt = "Close-up studio portrait of a futuristic android explorer, intricate ceramic plating, subtle glowing ocular sensors, soft rim lighting, shallow depth of field, 85mm f/1.4 lens bokeh, highly detailed skin texture",
        negativePrompt = "bad eyes, poorly rendered face, extra fingers, cartoon, 3d CGI plastic, deformed anatomy",
        recommendedSampler = DiffusionSampler.DPM_PLUS_PLUS_SDE_KARRAS,
        recommendedCfg = 6.5f,
        recommendedSteps = 25,
        emoji = "👤"
    ),
    PromptPresetTemplate(
        title = "Retro Pixel Art Cyber City",
        category = "Stylized & Art",
        prompt = "Isometric pixel art illustration of a bustling neon futuristic city with flying hovercars, ramen stalls with steam, holographic billboards, vibrant palette, clean 16-bit aesthetic",
        negativePrompt = "smooth gradients, 3d render, photo, blurry, realistic, anti-aliased",
        recommendedSampler = DiffusionSampler.EULER,
        recommendedCfg = 8.0f,
        recommendedSteps = 18,
        emoji = "👾"
    ),
    PromptPresetTemplate(
        title = "Macro Botanical Insect Study",
        category = "Macro Photography",
        prompt = "Extreme macro photography of a jewel beetle resting on a dew-covered leaf, iridescent emerald and ruby exoskeleton, water droplet reflections, microscopic antenna details, f/2.8 macro lens",
        negativePrompt = "blurry, out of focus, low res, fake, illustration",
        recommendedSampler = DiffusionSampler.HEUN,
        recommendedCfg = 7.0f,
        recommendedSteps = 22,
        emoji = "🪲"
    )
)
