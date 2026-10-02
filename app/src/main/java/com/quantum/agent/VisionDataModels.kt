package com.quantum.agent

import android.graphics.Bitmap
import android.graphics.RectF
import java.io.Serializable

enum class VisionMode(val title: String, val description: String) : Serializable {
    DEPTH_ESTIMATION("Depth Analysis", "Real-time depth estimation, distance metric calculation & 3D point gradients"),
    OBJECT_DETECTION("Object Detection", "Real-time bounding box identification with dynamic event telemetry logging"),
    OBJECT_COUNTING("Object Counter", "Real-time tally scoreboard, ROI crossing gates & density heatmap analysis"),
    FLORA_FAUNA_IDENTIFIER("Flora & Fauna ID", "Scientific taxonomy for trees, botanical plants, flowers, insects & wildlife"),
    MULTI_MODAL_ANALYSIS("Multi-Modal Scene", "Unified depth mapping, object localization and ecological taxonomy matrix")
}

enum class VisionInputSource(val label: String) : Serializable {
    LIVE_CAMERA("Live Camera Stream"),
    PHOTO_CAPTURE("Photo Snapshot"),
    GALLERY_IMPORT("Gallery Image / File"),
    VIDEO_STREAM("Video File Stream"),
    BENCHMARK_SYNTHETIC("Synthetic Test Bench")
}

enum class VisionModelArchitecture(val displayName: String, val engineFamily: String, val latencySpec: String) : Serializable {
    DEPTH_ANYTHING_V2_FAST("Depth-Anything-V2 Small", "ONNX / NEON C++", "16ms / 60 FPS"),
    DEPTH_ANYTHING_V2_LARGE("Depth-Anything-V2 Large", "Vulkan GPU Tensor", "42ms / 24 FPS"),
    MIDAS_V3_1_DPT("MiDaS v3.1 DPT 384", "GGML / CPU C++", "35ms / 28 FPS"),
    ZOEDEPTH_METRIC("ZoeDepth Metric (0-20m)", "Float16 TensorRT", "48ms / 20 FPS"),
    YOLO_V11_NANO("YOLOv11-Nano Edge", "C++ NCNN / NEON", "3.2ms / 120 FPS"),
    YOLO_V10_MEDIUM("YOLOv10-Medium", "Vulkan Tensor Core", "14ms / 70 FPS"),
    RT_DETR_R50("RT-DETR ResNet50", "Transformer Vision", "28ms / 35 FPS"),
    CSRNET_DENSITY_COUNTER("CSRNet Density Counter", "Convolutional Heatmap", "18ms / 55 FPS"),
    BIOCLIP_PLANTNET("BioCLIP + PlantNet-v2", "Taxonomic Dual-Encoder", "22ms / 45 FPS"),
    INSECT_ID_SPECIALIST("Insect-ID Entomology Net", "Morphology Classifier", "19ms / 50 FPS"),
    CANOPY_TREE_FORESTRY("TreeCanopy & Pathology Net", "Arboriculture Vision", "24ms / 40 FPS")
}

enum class DepthColormap(val colormapName: String, val description: String) : Serializable {
    TURBO("Turbo", "High-contrast perceptually enhanced rainbow gradient"),
    INFERNO("Inferno", "Dark purple to fiery yellow heat dissipation map"),
    MAGMA("Magma", "Deep violet through orange to incandescent cream"),
    VIRIDIS("Viridis", "Perceptually uniform color vision safe gradient"),
    PLASMA("Plasma", "Rich blue-violet to intense gold spectral ramp"),
    MONOCHROME("Monochrome", "16-bit linear grayscale normalized depth map")
}

enum class TaxonomicKingdom(val displayName: String) : Serializable {
    PLANTAE_TREE("Tree / Arboriculture"),
    PLANTAE_BOTANICAL("Plant / Botanical Flower"),
    INSECTA_ARTHROPOD("Insect / Arthropod"),
    ANIMALIA_WILDLIFE("Wildlife / Animal"),
    FUNGI_MYCOLOGY("Fungus / Mushroom"),
    OBJECT_INORGANIC("Inorganic Entity / Asset")
}

data class NormalizedBBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : Serializable {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

data class DetectedObjectItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val category: String,
    val confidence: Float,
    val bbox: NormalizedBBox,
    val colorHex: Long,
    val trackingId: Int,
    val estimatedDepthMeters: Float = 2.5f,
    val count: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

data class CategoryCountSummary(
    val category: String,
    val count: Int,
    val items: List<String>,
    val averageConfidence: Float,
    val colorHex: Long
) : Serializable

data class DepthMetricStats(
    val minDistanceMeters: Float,
    val maxDistanceMeters: Float,
    val meanDistanceMeters: Float,
    val estimatedFocalLengthMm: Float = 28.0f,
    val surfaceNormalSlopeDeg: Float = 14.2f,
    val estimatedPointCloudPoints: Int = 307200, // 640x480
    val histogramBuckets: List<Float> = emptyList()
) : Serializable

data class FloraFaunaTaxonomy(
    val commonName: String,
    val scientificBinomial: String,
    val kingdomType: TaxonomicKingdom,
    val familyOrOrder: String,
    val confidence: Float,
    val healthCondition: String, // "Healthy & Vigorous", "Mild Chlorosis", "Aphid Activity", "Leaf Spot"
    val healthColorHex: Long = 0xFF00FF66,
    val nativeRegion: String,
    val ecologicalNotes: String,
    val keyDiagnosticFeatures: List<String>
) : Serializable

data class VisionTelemetryEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val frameIndex: Long,
    val mode: VisionMode,
    val summary: String,
    val details: String,
    val latencyMs: Float,
    val fps: Float,
    val detectedCount: Int = 0
) : Serializable

data class SavedVisionSnapshot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val depthMapPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: VisionMode,
    val modelName: String,
    val detectedObjectsSummary: String,
    val totalObjectCount: Int,
    val meanDepthMeters: Float? = null,
    val scientificName: String? = null,
    val isFavorite: Boolean = false
) : Serializable

data class VisionConfig(
    val activeMode: VisionMode = VisionMode.OBJECT_DETECTION,
    val inputSource: VisionInputSource = VisionInputSource.BENCHMARK_SYNTHETIC,
    val selectedModel: VisionModelArchitecture = VisionModelArchitecture.YOLO_V11_NANO,
    val selectedDepthModel: VisionModelArchitecture = VisionModelArchitecture.DEPTH_ANYTHING_V2_FAST,
    val selectedFloraModel: VisionModelArchitecture = VisionModelArchitecture.BIOCLIP_PLANTNET,
    val colormap: DepthColormap = DepthColormap.TURBO,
    val confidenceThreshold: Float = 0.45f,
    val iouNmsThreshold: Float = 0.50f,
    val isRealtimeStreaming: Boolean = true,
    val targetFps: Int = 30,
    val enableBboxOverlay: Boolean = true,
    val enableLabelsOverlay: Boolean = true,
    val enableConfidenceOverlay: Boolean = true,
    val enableDepthGridOverlay: Boolean = true,
    val enableDensityHeatmap: Boolean = false,
    val enableLineCrossingGate: Boolean = false,
    val lineCrossingYRatio: Float = 0.5f,
    val filterCategory: String = "ALL", // "ALL", "PLANT", "INSECT", "VEHICLE", "PERSON", etc.
    val isTorchEnabled: Boolean = false,
    val useFrontCamera: Boolean = false,
    val streamResolutionWidth: Int = 640,
    val streamResolutionHeight: Int = 480,
    val autoLogDetections: Boolean = true,
    val autoCaptureOnSpeciesDetect: Boolean = false
) : Serializable
