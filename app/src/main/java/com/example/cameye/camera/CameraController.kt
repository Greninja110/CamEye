package com.example.cameye.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

// Interface for camera controls exposed to ViewModel/Service
interface CameraController {
    suspend fun setZoomRatio(ratio: Float)
    suspend fun triggerFocus(x: Float, y: Float) // Normalized coordinates (0-1)
    suspend fun setExposureCompensation(ev: Int)

    // Maybe add methods for flash, switching camera etc. later
}

// Implementation internal to CameraManager
internal class CameraControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope, // Scope for suspending control calls
    private val getCameraProvider: suspend () -> ProcessCameraProvider,
    private val getLifecycleOwner: () -> LifecycleOwner?,
    private val getCameraSelector: () -> CameraSelector?,
    private val updateCameraState: (CameraState) -> Unit, // Callback to update manager state
    private val getCurrentState: () -> CameraState // Function to get current state
) : CameraController {

    private var camera: Camera? = null // Hold reference to the bound camera

    // Call this when camera is bound
    fun setBoundCamera(boundCamera: Camera?) {
        this.camera = boundCamera
        updateZoomRange()
        updateExposureRange()
    }

    private fun updateZoomRange() {
        scope.launch(Dispatchers.Main) { // CameraInfo must be accessed on main thread
            try {
                val state = camera?.cameraInfo?.zoomState?.value
                if (state != null) {
                    updateCameraState(
                        getCurrentState().copy(
                            minZoom = state.minZoomRatio,
                            maxZoom = state.maxZoomRatio
                        )
                    )
                    Timber.d("Zoom range updated: [${state.minZoomRatio}, ${state.maxZoomRatio}]")
                } else {
                    Timber.w("Could not get zoom state")
                    updateCameraState(getCurrentState().copy(minZoom = 1.0f, maxZoom = 1.0f))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting zoom state")
            }
        }
    }

    private fun updateExposureRange() {
        scope.launch(Dispatchers.Main) { // CameraInfo must be accessed on main thread
            try {
                val state = camera?.cameraInfo?.exposureState
                if (state != null && state.isExposureCompensationSupported) {
                    updateCameraState(
                        getCurrentState().copy(exposureRange = state.exposureCompensationRange.lower..state.exposureCompensationRange.upper)
                    )
                    Timber.d("Exposure range updated: ${state.exposureCompensationRange}")
                } else {
                    Timber.w("Exposure compensation not supported or state unavailable.")
                    updateCameraState(getCurrentState().copy(exposureRange = 0..0))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting exposure state")
            }
        }
    }

    override suspend fun setZoomRatio(ratio: Float) {
        val cameraControl = camera?.cameraControl ?: return
        val currentState = getCurrentState()
        val clampedRatio = ratio.coerceIn(currentState.minZoom, currentState.maxZoom)

        Timber.d("Setting zoom ratio to $clampedRatio (requested $ratio)")
        try {
            cameraControl.setZoomRatio(clampedRatio).await() // Use await extension
            updateCameraState(currentState.copy(zoomRatio = clampedRatio))
            Timber.d("Zoom ratio set successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set zoom ratio")
            // Update state with error or revert?
        }
    }

    override suspend fun triggerFocus(x: Float, y: Float) {
        val cameraControl = camera?.cameraControl ?: return
        val cameraInfo = camera?.cameraInfo ?: return
        val lifecycleOwner = getLifecycleOwner() ?: return

        val meteringPointFactory = SurfaceOrientedMeteringPointFactory(1f, 1f) // Assume full surface
        val focusPoint = meteringPointFactory.createPoint(x, y)
        val meteringAction = FocusMeteringAction.Builder(focusPoint, FocusMeteringAction.FLAG_AF)
            // .addPoint(focusPoint, FocusMeteringAction.FLAG_AE) // Optionally meter AE too
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS) // Auto cancel after 3 seconds
            .build()

        Timber.d("Starting focus action at ($x, $y)")
        updateCameraState(getCurrentState().copy(isFocusing = true))
        try {
            // Observe the result - needs to run on main thread
            val focusResultDeferred = CompletableDeferred<FocusMeteringResult>()
            ContextCompat.getMainExecutor(context).execute {
                cameraControl.startFocusAndMetering(meteringAction).addListener({
                    try {
                        val result = cameraControl.startFocusAndMetering(meteringAction).get()
                        Timber.d("Focus metering result: Success=${result.isFocusSuccessful}")
                        focusResultDeferred.complete(result)
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting focus result")
                        focusResultDeferred.completeExceptionally(e)
                    } finally {
                        updateCameraState(getCurrentState().copy(isFocusing = false))
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            focusResultDeferred.await() // Wait for the focus operation to complete or fail

        } catch (e: CameraInfoUnavailableException) {
            Timber.e(e, "Cannot start focus, camera info unavailable")
            updateCameraState(getCurrentState().copy(isFocusing = false))
        } catch (e: Exception) {
            Timber.e(e, "Failed to trigger focus and metering")
            updateCameraState(getCurrentState().copy(isFocusing = false))
        }
    }


    override suspend fun setExposureCompensation(ev: Int) {
        val cameraControl = camera?.cameraControl ?: return
        val currentState = getCurrentState()
        val exposureState = camera?.cameraInfo?.exposureState ?: return

        if (!exposureState.isExposureCompensationSupported) {
            Timber.w("Exposure compensation not supported.")
            return
        }

        val clampedEv = ev.coerceIn(currentState.exposureRange)
        Timber.d("Setting exposure compensation to $clampedEv EV (requested $ev)")

        try {
            cameraControl.setExposureCompensationIndex(clampedEv).await()
            updateCameraState(currentState.copy(exposureValue = clampedEv))
            Timber.d("Exposure compensation set successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set exposure compensation")
            // Update state with error or revert?
        }
    }
}

// Suspending extension for Guava ListenableFuture
suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
    val deferred = CompletableDeferred<T>()
    addListener({
        try {
            deferred.complete(get())
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }, ContextCompat.getMainExecutor(ContextUtil.getApplicationContext())) // Use application context or appropriate executor
    return deferred.await()
}

// Helper to get context if needed for executor
object ContextUtil {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getApplicationContext(): Context {
        return appContext ?: throw IllegalStateException("ContextUtil not initialized")
    }
}