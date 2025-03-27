package com.example.cameye.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Rational
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.cameye.ar.ArCoreSessionManager
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    public val arCoreSessionManager: ArCoreSessionManager // Inject ARCore manager
) {
    private val cameraScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private var previewUseCase: Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    // private var videoCaptureUseCase: VideoCapture<Recorder>? = null // Optional

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null // Should be activity/fragment lifecycle

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    val cameraController: CameraController by lazy {
        CameraControllerImpl(
            context,
            cameraScope,
            { getCameraProvider() },
            { lifecycleOwner },
            { cameraSelector },
            { newState -> _cameraState.value = newState },
            { _cameraState.value }
        )
    }

    // Temporary listener for video frames (replace with proper encoding/muxing later)
    var onFrameAvailableListener: ((ImageProxy) -> Unit)? = null

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
        ContextUtil.initialize(context) // Initialize context util for extensions
    }

    fun initialize(owner: LifecycleOwner) {
        Timber.d("CameraManager initializing...")
        this.lifecycleOwner = owner
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                _cameraState.value = _cameraState.value.copy(isInitialized = true)
                Timber.d("CameraProvider initialized.")
                // Optionally bind camera immediately if config is known
                // bindCameraUseCases()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get CameraProvider")
                _cameraState.value = _cameraState.value.copy(error = e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider {
        if (cameraProvider == null) {
            cameraProvider = cameraProviderFuture.await() // Use suspending extension
        }
        return cameraProvider ?: throw IllegalStateException("CameraProvider not available")
    }


    @SuppressLint("WrongConstant") // For setTargetResolution, aspect ratio if needed
    suspend fun startCamera(
        surfaceProvider: Preview.SurfaceProvider,
        targetResolution: Size? = null, // e.g., Size(1280, 720)
        requireAudio: Boolean = false // Placeholder for audio config
    ) {
        val owner = lifecycleOwner ?: run {
            Timber.e("LifecycleOwner is null, cannot start camera.")
            _cameraState.value = _cameraState.value.copy(error = IllegalStateException("LifecycleOwner is null"))
            return
        }
        if (!_cameraState.value.isInitialized) {
            Timber.e("CameraProvider not initialized, cannot start camera.")
            _cameraState.value = _cameraState.value.copy(error = IllegalStateException("CameraProvider not initialized"))
            return
        }

        Timber.i("Starting camera with resolution: $targetResolution, Audio: $requireAudio")


        withContext(Dispatchers.Main) { // CameraX binding must happen on the main thread
            try {
                val provider = getCameraProvider()
                provider.unbindAll() // Unbind previous use cases first

                // --- Preview UseCase ---
                previewUseCase = Preview.Builder().apply {
                    targetResolution?.let { setTargetResolution(it) }
                    // setTargetAspectRatio(AspectRatio.RATIO_16_9) // Or aspect ratio
                }.build().also {
                    it.setSurfaceProvider(surfaceProvider)
                    Timber.d("Preview UseCase created.")
                }

                // --- ImageAnalysis UseCase ---
                // This analyzer will run for every frame
                val analyzer = ImageAnalysis.Analyzer { imageProxy ->
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val timestamp = imageProxy.imageInfo.timestamp // Use this timestamp

                    // 1. Pass necessary info to ARCore
                    // ARCore might use the texture ID from Preview, or need CPU image data.
                    // For SharedCamera mode, ARCore often uses the Preview's texture.
                    // We still might need the timestamp from ImageAnalysis.
                    // We also need the imageProxy buffer for video encoding.

                    // Update ARCore session with the timestamp. Size might be needed too.
                    // Convert imageProxy width/height, potentially compensating for rotation
                    val imageSize = Size(imageProxy.width, imageProxy.height) // TODO: Handle rotation correctly for size if needed
                    arCoreSessionManager.updateSession(timestamp, imageSize)


                    // 2. Provide imageProxy for Video Encoding / Data Streaming
                    // IMPORTANT: You MUST call imageProxy.close() eventually!
                    // The listener is responsible for closing it after processing.
                    onFrameAvailableListener?.invoke(imageProxy)
                        ?: imageProxy.close() // Close immediately if no listener is set
                }

                imageAnalysisUseCase = ImageAnalysis.Builder().apply {
                    // Use a resolution that balances performance and quality for analysis/encoding
                    // Maybe lower than preview? e.g., Size(640, 480) or Size(1280, 720)
                    targetResolution?.let { setTargetResolution(it) } // Match preview for simplicity first
                    setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Drop frames if analysis is slow
                    setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Common format for encoders
                }.build().also {
                    it.setAnalyzer(cameraExecutor, analyzer) // Run analysis on the dedicated thread
                    Timber.d("ImageAnalysis UseCase created.")
                }


                // --- Bind UseCases ---
                camera = provider.bindToLifecycle(
                    owner,
                    cameraSelector,
                    previewUseCase,
                    imageAnalysisUseCase
                    // videoCaptureUseCase // Add if needed
                )
                Timber.i("Camera bound to lifecycle.")

                // Notify controller about the bound camera to update ranges etc.
                (cameraController as? CameraControllerImpl)?.setBoundCamera(camera)

                // Update state
                val currentLensFacing = camera?.cameraInfo?.lensFacing ?: CameraSelector.LENS_FACING_BACK
                _cameraState.value = _cameraState.value.copy(
                    lensFacing = currentLensFacing,
                    error = null // Clear previous error
                )


            } catch (e: Exception) {
                Timber.e(e, "Failed to bind camera use cases")
                _cameraState.value = _cameraState.value.copy(error = e)
                // Attempt to clean up
                try { getCameraProvider().unbindAll() } catch (ignore: Exception) {}
                previewUseCase = null
                imageAnalysisUseCase = null
                camera = null
                (cameraController as? CameraControllerImpl)?.setBoundCamera(null)
            }
        }
    }

    fun stopCamera() {
        cameraScope.launch(Dispatchers.Main) { // unbind must be on main thread
            try {
                Timber.i("Stopping camera...")
                getCameraProvider().unbindAll()
                previewUseCase = null
                imageAnalysisUseCase = null
                camera = null
                (cameraController as? CameraControllerImpl)?.setBoundCamera(null)
                _cameraState.value = CameraState() // Reset state (or keep some parts?)
                Timber.i("Camera stopped and unbound.")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping camera")
                // State might be inconsistent here
                _cameraState.value = _cameraState.value.copy(error = e)
            }
        }
    }

    fun release() {
        Timber.d("CameraManager releasing...")
        stopCamera() // Ensure camera is stopped
        cameraScope.cancel("CameraManager released") // Cancel coroutines
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        Timber.d("CameraManager released.")
    }

    // --- Helper Methods ---

    // Example: Switch camera
    suspend fun switchCamera() {
        val newSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Check if the new lens facing is available
        try {
            if (getCameraProvider().hasCamera(newSelector)) {
                cameraSelector = newSelector
                Timber.i("Switching camera to: ${if (newSelector == CameraSelector.DEFAULT_BACK_CAMERA) "BACK" else "FRONT"}")
                // Re-bind use cases - requires existing surface provider etc.
                // Need to carefully manage state and potentially re-pass surface provider
                // For simplicity, might require stopCamera() then startCamera() with new selector
                // This is a simplified example:
                // stopCamera() // Or just unbind
                // startCamera(previewUseCase?.surfaceProvider!!) // Needs careful handling of state
                Timber.w("Rebinding camera after switch is complex, requires restart in this example.")

            } else {
                Timber.w("Camera selector $newSelector not available.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check camera availability for switching")
        }
    }
}