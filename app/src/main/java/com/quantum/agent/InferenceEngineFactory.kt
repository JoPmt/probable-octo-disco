package com.quantum.agent

object InferenceEngineFactory {
    fun createEngine(type: InferenceEngineType): ModelInferenceEngine {
        return when (type) {
            InferenceEngineType.LLAMA_CPP -> LlamaCppEngine()
            InferenceEngineType.MLC_LLM -> MlcLlmEngine()
            InferenceEngineType.OLLAMA -> OllamaEngine()
            InferenceEngineType.KOBOLD_CPP -> KoboldCppEngine()
        }
    }
}
