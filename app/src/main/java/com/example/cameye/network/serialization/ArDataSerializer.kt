package com.example.cameye.network.serialization

import com.example.cameye.data.model.ArDataPacket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

// Simple JSON serializer using kotlinx.serialization
class ArDataSerializer @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true // Be lenient on receiver side
        // encodeDefaults = true // Include default values if needed
    }

    fun serialize(packet: ArDataPacket): ByteArray {
        // Encode to String then to ByteArray (UTF-8)
        return json.encodeToString(packet).encodeToByteArray()
    }

    fun deserialize(bytes: ByteArray): ArDataPacket? {
        return try {
            // Decode from ByteArray (UTF-8) to String then to Object
            json.decodeFromString<ArDataPacket>(bytes.decodeToString())
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to deserialize ArDataPacket")
            null
        }
    }
}