package com.quantum.agent

object SwarmBroadcastContract {
    const val ACTION_SWARM_TELEMETRY = "com.quantum.agent.ACTION_SWARM_TELEMETRY"
    const val EXTRA_ROLE = "EXTRA_ROLE"
    const val EXTRA_THOUGHT = "EXTRA_THOUGHT"
    const val EXTRA_TOOL = "EXTRA_TOOL"
    const val EXTRA_PARAMS = "EXTRA_PARAMS"
    const val EXTRA_SYSTEM_STATUS = "EXTRA_SYSTEM_STATUS"
    
    // Solo Direct Stream extras
    const val EXTRA_IS_STREAMING_CHUNK = "EXTRA_IS_STREAMING_CHUNK"
    const val EXTRA_STREAM_CHUNK = "EXTRA_STREAM_CHUNK"
    const val EXTRA_STREAM_COMPLETE = "EXTRA_STREAM_COMPLETE"
    const val EXTRA_EXECUTION_MODE = "EXTRA_EXECUTION_MODE"
}
