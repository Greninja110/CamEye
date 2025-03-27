package com.example.cameye.camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraInfo

// Example state - expand as needed
data class CameraState(
    val isInitialized: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 1.0f,
    val isFocusing: Boolean = false,
    val exposureValue: Int = 0, // EV steps
    val exposureRange: ClosedRange<Int> = 0..0,
    val error: Throwable? = null
)
