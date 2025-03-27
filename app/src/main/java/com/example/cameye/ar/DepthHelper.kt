package com.example.cameye.ar

import android.media.Image // Use android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import timber.log.Timber
import java.nio.ShortBuffer

object DepthHelper {

    /**
     * Acquires the latest depth image from the ARCore frame.
     * Remember to close the returned Image!
     * @return The depth Image, or null if not available or an error occurs.
     */
    fun acquireDepthImage(frame: Frame): Image? {
        return try {
            // Use acquireDepthImage16Bits for wider device compatibility and typical use cases
            // acquireRawDepthImage provides sensor raw data, often needs more processing
            frame.acquireDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            Timber.v("Depth image not yet available.") // This is common, not necessarily an error
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire depth image.")
            null
        }
    }

    /**
     * Example: Extracts depth data for a specific pixel (u, v) in normalized image coordinates.
     * WARNING: This involves accessing the Image buffer, which can be slow if done frequently.
     * It's often better to process the whole depth map on the GPU or via native code if needed.
     *
     * @param depthImage The acquired depth image (must not be closed yet).
     * @param u Normalized horizontal coordinate (0.0 to 1.0).
     * @param v Normalized vertical coordinate (0.0 to 1.0).
     * @return Depth in millimeters, or null if coordinates are invalid or data unavailable.
     */
    fun getDepthMillimeters(depthImage: Image, u: Float, v: Float): Int? {
        if (depthImage.format != android.graphics.ImageFormat.DEPTH16) {
            Timber.w("Unexpected depth image format: ${depthImage.format}")
            return null
        }

        val plane = depthImage.planes.firstOrNull() ?: return null
        val buffer: ShortBuffer = plane.buffer.asShortBuffer()

        val width = depthImage.width
        val height = depthImage.height

        val x = (u * width).toInt()
        val y = (v * height).toInt()

        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null // Coordinates out of bounds
        }

        // Calculate index in the buffer. DEPTH16 has 1 pixel per element.
        // Pixel stride is usually 2 bytes, row stride depends on width alignment.
        val pixelStride = plane.pixelStride // Should be 2 for DEPTH16
        val rowStride = plane.rowStride
        val index = (y * rowStride / pixelStride) + x // More robust calculation using strides

        // Check index bounds carefully
        if (index < 0 || index >= buffer.capacity()) {
            Timber.w("Calculated index out of buffer bounds: $index (Capacity: ${buffer.capacity()})")
            return null
        }


        // Depth value is in millimeters (UNSIGNED short, 0-65535)
        // ShortBuffer gives signed shorts, need conversion.
        val depthSample = buffer.get(index)
        return depthSample.toInt() and 0xFFFF // Convert signed short to unsigned int
    }

    /**
     * Example: Get a copy of the depth data as a ShortArray.
     * This copies the entire buffer, potentially large and slow. Use with caution.
     * Useful for sending the raw depth data over the network (after serialization).
     *
     * @param depthImage The acquired depth image (must not be closed yet).
     * @return A ShortArray containing the depth data in row-major order, or null.
     */
    fun copyDepthData(depthImage: Image): ShortArray? {
        if (depthImage.format != android.graphics.ImageFormat.DEPTH16) {
            Timber.w("Unexpected depth image format: ${depthImage.format}")
            return null
        }
        val plane = depthImage.planes.firstOrNull() ?: return null
        val buffer = plane.buffer.asShortBuffer()
        val width = depthImage.width
        val height = depthImage.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride // Should be 2 for DEPTH16

        if (rowStride == width * pixelStride) {
            // If no padding, we can copy directly
            val data = ShortArray(buffer.remaining())
            buffer.get(data)
            return data
        } else {
            // Handle row padding: copy row by row
            val data = ShortArray(width * height)
            var dstIndex = 0
            var bufferIndex = 0
            for (y in 0 until height) {
                buffer.position(bufferIndex)
                // Copy 'width' shorts from the current row start in the buffer
                buffer.get(data, dstIndex, width)
                dstIndex += width
                bufferIndex += rowStride / pixelStride // Move to the start of the next row in the buffer
            }
            return data
        }
    }
}