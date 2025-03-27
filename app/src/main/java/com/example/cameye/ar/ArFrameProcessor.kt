package com.example.cameye.ar

import android.media.Image // Use android.media.Image
import com.example.cameye.data.model.ArDataPacket
import com.google.ar.core.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPOutputStream


class ArFrameProcessor {

    // Simple counter for frame sequence number
    private var frameSequence: Long = 0

    /**
     * Processes an ARCore Frame to extract pose and depth information.
     * Runs potentially blocking operations like depth data copying on Dispatchers.IO.
     *
     * @param frame The ARCore Frame to process.
     * @param includeDepth If true, attempts to acquire and process depth data.
     * @return An ArDataPacket containing the extracted data, or null if processing fails.
     */
    suspend fun processFrame(frame: Frame, includeDepth: Boolean): ArDataPacket? = withContext(Dispatchers.IO) {
        try {
            val camera = frame.camera ?: return@withContext null // Need camera for pose

            // 1. Get Camera Pose
            val pose = camera.pose
            val translation = pose.translation // FloatArray(3) [x, y, z]
            val rotation = pose.rotationQuaternion // FloatArray(4) [qx, qy, qz, qw]

            // 2. Get Timestamp
            val timestamp = frame.timestamp // Nanoseconds

            // 3. Optionally Get Depth Data
            var depthWidth: Int? = null
            var depthHeight: Int? = null
            var depthDataCompressed: ByteArray? = null

            if (includeDepth) {
                var depthImage: Image? = null
                try {
                    depthImage = DepthHelper.acquireDepthImage(frame)
                    if (depthImage != null) {
                        depthWidth = depthImage.width
                        depthHeight = depthImage.height

                        // --- Depth Data Handling ---
                        // Option A: Copy raw ShortBuffer data (Potentially large!)
                        // val rawDepthData = DepthHelper.copyDepthData(depthImage)
                        // if (rawDepthData != null) {
                        //    depthDataCompressed = compressShortArray(rawDepthData) // Compress it
                        // }

                        // Option B: For this example, just log dimensions, don't send full map yet
                        Timber.v("Depth map acquired: ${depthWidth}x${depthHeight}")

                        // TODO: Implement efficient depth map serialization/compression
                        // e.g., Downsample, encode as PNG/JPEG (lossy), use specific compression.
                        // For now, we'll send null depth data. Replace with actual compressed data.
                        depthDataCompressed = null // Placeholder


                    }
                } finally {
                    depthImage?.close() // IMPORTANT: Always close the acquired image!
                }
            }

            // Increment frame sequence number
            val currentSequence = frameSequence++

            // 4. Create Data Packet
            ArDataPacket(
                timestampNanos = timestamp,
                sequence = currentSequence,
                cameraPoseTranslation = translation.toList(), // Convert FloatArray to List for serialization
                cameraPoseRotation = rotation.toList(),       // Convert FloatArray to List
                depthWidth = depthWidth,
                depthHeight = depthHeight,
                depthDataCompressed = depthDataCompressed // Send compressed data (or null)
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing AR frame.")
            null
        }
    }

    // Example compression using GZIP (Simple, but maybe not the best for depth data)
    private fun compressShortArray(data: ShortArray): ByteArray? {
        return try {
            val byteStream = ByteArrayOutputStream()
            // Convert ShortArray to ByteArray first (2 bytes per short, little-endian example)
            val byteBuffer = ByteBuffer.allocate(data.size * 2)
            // Consider Endianness based on your receiver
            // byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(data)
            val rawBytes = byteBuffer.array()

            // Compress the byte array
            GZIPOutputStream(byteStream).use { gzip ->
                gzip.write(rawBytes)
            }
            byteStream.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Failed to compress depth data")
            null
        }
    }
}