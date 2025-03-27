package com.example.cameye.data.model

data class NetworkInfo(
    val ipAddress: String = "N/A",
    val port: Int = -1,
    val isServerRunning: Boolean = false,
    val clientCount: Int = 0,
    val error: String? = null,
    // Add stream quality metrics later (bitrate, packet loss, latency)
    val currentBitrateKbps: Int = 0,
    val latencyMs: Float = 0f,
)