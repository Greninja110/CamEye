// File: com/example/cameye/data/model/StreamConfig.kt

package com.example.cameye.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Configuration class for streaming parameters.
 * This class defines all settings required for video/audio streaming.
 */
@Parcelize
data class StreamConfig(
    val mode: StreamMode = StreamMode.NORMAL,
    val hasVideo: Boolean = true,
    val hasAudio: Boolean = true,
    val hasAr: Boolean = false,
    val includeDepth: Boolean = false,
    val videoResolutionWidth: Int = 1280,
    val videoResolutionHeight: Int = 720,
    val videoFps: Int = 30,
    val videoBitrate: Int = 4_000_000, // 4 Mbps
    val audioSampleRate: Int = 44100,  // Added missing property
    val audioChannels: Int = 2,        // Added missing property
    val audioBitrate: Int = 128_000    // 128 kbps
) : Parcelable {

    /**
     * Gets the resolution as a formatted string.
     * @return String representing the resolution (e.g., "1280x720")
     */
    fun getResolutionString(): String = "${videoResolutionWidth}x${videoResolutionHeight}"

    /**
     * Validates that the configuration has valid values.
     * @return Boolean indicating if the configuration is valid
     */
    fun isValid(): Boolean {
        // Basic validation rules
        return videoResolutionWidth > 0 &&
                videoResolutionHeight > 0 &&
                videoFps > 0 &&
                videoBitrate > 0 &&
                (!hasAudio || (audioSampleRate > 0 && audioChannels > 0 && audioBitrate > 0))
    }

    companion object {
        // Preset configurations
        val HD = StreamConfig(
            videoResolutionWidth = 1280,
            videoResolutionHeight = 720,
            videoFps = 30,
            videoBitrate = 4_000_000,
            audioSampleRate = 44100,
            audioChannels = 2,
            audioBitrate = 128_000
        )

        val LOW_BANDWIDTH = StreamConfig(
            videoResolutionWidth = 640,
            videoResolutionHeight = 480,
            videoFps = 24,
            videoBitrate = 1_500_000,
            audioSampleRate = 22050,
            audioChannels = 1,
            audioBitrate = 64_000
        )

        val AR_MODE = StreamConfig(
            mode = StreamMode.AR_OVERLAY,
            hasAr = true,
            includeDepth = true,
            videoResolutionWidth = 1280,
            videoResolutionHeight = 720,
            videoFps = 30,
            videoBitrate = 5_000_000,
            audioSampleRate = 44100,
            audioChannels = 2,
            audioBitrate = 128_000
        )
    }
}

/**
 * Defines the streaming mode.
 */

enum class StreamMode {
    NORMAL,          // Add this
    AUDIO_ONLY,
    VIDEO_ONLY,
    VIDEO_AUDIO,
    VIDEO_AR,
    VIDEO_AUDIO_AR,
    AR_OVERLAY       // Add this
}

