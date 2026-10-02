package com.quantum.agent

interface ModelInferenceEngine {
    val engineType: InferenceEngineType
    
    // Initialize or bind model
    fun initializeEngine(config: SwarmConfig): Boolean

    // Solo direct generation (supports token / chunk streaming callback)
    suspend fun executeDirectInference(
        prompt: String,
        systemPrompt: String,
        config: SwarmConfig,
        onTokenReceived: (String) -> Unit
    ): String

    // Agent structured turn generation
    suspend fun executeAgentTurn(
        rolePrompt: String,
        contextTranscript: String,
        config: SwarmConfig
    ): String

    // Free resources
    fun deallocateEngine()
}
