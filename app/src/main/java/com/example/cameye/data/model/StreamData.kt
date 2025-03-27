package com.example.cameye.data.model

import java.nio.ByteBuffer

// Represents the combined data to be potentially muxed and streamed
// This is conceptual for the placeholder network layer
data class StreamData(
    val videoFrame: ByteBuffer?, // Encoded video frame (e.g., H.264 NAL unit)
    val videoTimestampUs: Long?, // Microseconds
    val audioChunk: ByteBuffer?, // Encoded audio chunk (e.g., AAC frame)
    val audioTimestampUs: Long?, // Microseconds
    val arDataPacket: ArDataPacket?, // Serialized AR data
    val isKeyFrame: Boolean = false // For video
)