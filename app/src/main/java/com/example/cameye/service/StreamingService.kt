package com.example.cameye.service

import android.annotation.SuppressLint // Import SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources // Import Resources needed for exception catch
import android.graphics.BitmapFactory
// import android.hardware.display.DisplayManager // No longer directly used here
// import android.media.Image // Using android.media.Image - Not directly used here, ImageProxy is used
import android.media.MediaCodec // For encoding (requires more setup)
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder // Easier audio encoding option
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcelable // Import Parcelable for the helper function
import android.os.PowerManager
// import android.view.Display // No longer directly used here
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview // Import Preview
// import androidx.camera.core.SurfaceRequest // Import SurfaceRequest (though the dummy provider using it was removed) - Removed unused import
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat // Import ContextCompat (needed if dummySurfaceProvider lambda were kept)
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.cameye.MainActivity
import com.example.cameye.R
// Corrected WifiHelper import path based on typical structure
import com.example.cameye.network.rtsp.WifiHelper
import com.example.cameye.ar.ArCoreSessionManager
import com.example.cameye.ar.ArFrameProcessor
import com.example.cameye.ar.ArTrackingState
import com.example.cameye.camera.CameraManager
import com.example.cameye.data.model.ArDataPacket
import com.example.cameye.data.model.NetworkInfo
import com.example.cameye.data.model.StreamConfig
import com.example.cameye.data.model.StreamData
import com.example.cameye.di.IoDispatcher
import com.example.cameye.network.discovery.NsdHelper
import com.example.cameye.network.rtsp.RtspServer
import com.example.cameye.network.web.WebServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext // Not explicitly used here anymore
import kotlinx.coroutines.runBlocking // Import runBlocking (used in stopStreaming)
import kotlinx.coroutines.CancellationException // Import CancellationException
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import java.io.Serializable // Import Serializable

@AndroidEntryPoint
class StreamingService : LifecycleService() {

    companion object {
        const val ACTION_START_STREAMING = "com.example.cameye.ACTION_START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.example.cameye.ACTION_STOP_STREAMING"
        const val EXTRA_STREAM_CONFIG = "com.example.cameye.EXTRA_STREAM_CONFIG"

        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_CHANNEL_ID = "CamEyeStreamingChannel"
    }

    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var arCoreSessionManager: ArCoreSessionManager
    @Inject lateinit var arFrameProcessor: ArFrameProcessor
    @Inject lateinit var rtspServer: RtspServer
    @Inject lateinit var webServer: WebServer
    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var wifiHelper: WifiHelper // Inject WifiHelper
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    // --- State Flows for UI Observation ---
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamConfig = MutableStateFlow<StreamConfig?>(null)
    val streamConfig: StateFlow<StreamConfig?> = _streamConfig.asStateFlow()

    // Combine network info from RTSP and Web servers
    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> get() = _networkInfo.asStateFlow()

    val arTrackingState: StateFlow<ArTrackingState> get() = arCoreSessionManager.trackingState
    val cameraState: StateFlow<com.example.cameye.camera.CameraState> get() = cameraManager.cameraState


    private val binder = LocalBinder()
    private var serviceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var streamConfigInternal: StreamConfig? = null

    // --- Encoding related (Simplified Placeholders) ---
    private var videoEncoder: MediaCodec? = null
    private var audioRecorder: MediaRecorder? = null // Using MediaRecorder for simplicity
    private var videoFormat: MediaFormat? = null
    // private var audioEncoder: MediaCodec? = null // More complex audio encoding


    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Timber.d("Service onBind")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("StreamingService onCreate")
        // Initialize managers that need lifecycle awareness
        lifecycle.addObserver(arCoreSessionManager) // Let AR manager handle its lifecycle
        cameraManager.initialize(this) // Service is a LifecycleOwner

        // Observe server states to update combined NetworkInfo
        collectServerStates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.d("onStartCommand received: Action = ${intent?.action}")

        when (intent?.action) {
            ACTION_START_STREAMING -> {
                // Use helper function for safer parcelable retrieval
                val config = intent.parcelable<StreamConfig>(EXTRA_STREAM_CONFIG)
                if (config != null) {
                    streamConfigInternal = config
                    _streamConfig.value = config
                    Timber.i("Requesting START STREAMING with config: $config")
                    startStreaming(config)
                } else {
                    Timber.e("Start streaming action received without valid config.")
                    stopSelf() // Stop if config is missing
                }
            }
            ACTION_STOP_STREAMING -> {
                Timber.i("Requesting STOP STREAMING")
                stopStreaming()
            }
            else -> {
                Timber.w("Unknown action received or null intent")
                // If service is killed and restarted, intent might be null.
                // Decide if you want to auto-restart streaming (e.g., using START_STICKY)
                // or stop (using START_NOT_STICKY). For manual start/stop, NOT_STICKY is often better.
                if (_isStreaming.value) {
                    Timber.w("Service restarted while streaming, stopping.")
                    stopStreaming() // Stop if restarted unexpectedly
                } else {
                    // If not streaming, maybe just stop the service if it's lingering
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY // Don't restart automatically if killed
    }

    private fun startStreaming(config: StreamConfig) {
        if (_isStreaming.value) {
            Timber.w("Streaming already active.")
            return
        }
        Timber.i("Starting streaming process...")

        // Acquire WakeLock
        acquireWakeLock()

        // Start Foreground Service
        // Requires POST_NOTIFICATIONS permission handling before calling this on Android 13+
        try {
            startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service. Check POST_NOTIFICATIONS permission on Android 13+.")
            // Handle error - maybe stop, maybe notify UI differently?
            releaseWakeLock() // Release wakelock if startup fails here
            stopSelf()
            return
        }


        // Update State
        _isStreaming.value = true
        _networkInfo.value = NetworkInfo(isServerRunning = true) // Initial assumption
        streamConfigInternal = config // Ensure internal config is set

        // Launch the main streaming logic in a coroutine
        serviceJob = lifecycleScope.launch(ioDispatcher) { // Use IO dispatcher for blocking calls
            try {
                // 1. Start Network Servers (RTSP, Web)
                Timber.d("Starting network servers...")
                // Assume RtspServer has a public/internal getRtspUrl() method
                webServer.rtspUrlProvider = { rtspServer.getRtspUrl() } // Link RTSP URL provider
                webServer.start() // Start web server first (less critical)
                // Assume RtspServer start is public/internal
                rtspServer.start(config) // Start RTSP server (placeholder)

                // Wait briefly for servers to potentially start listening
                kotlinx.coroutines.delay(500)

                // 2. Register Network Service Discovery
                Timber.d("Registering NSD service...")
                nsdHelper.registerService()

                // 3. Setup Encoders (Video/Audio) - Placeholder logic
                Timber.d("Setting up encoders...")
                setupEncoders(config) // Setup MediaCodec/MediaRecorder

                // 4. Start Camera with appropriate SurfaceProvider
                Timber.d("Starting camera...")

                // Set the listener for ImageAnalysis frames *before* starting camera
                // Use lifecycleScope to launch the suspend function processFrame
                cameraManager.onFrameAvailableListener = { imageProxy ->
                    // Ensure config is not null before launching processing
                    streamConfigInternal?.let { currentConfig ->
                        lifecycleScope.launch(ioDispatcher) { // Launch in coroutine on IO dispatcher
                            processFrame(imageProxy, currentConfig) // Process frame for encoding and AR
                        }
                    } ?: run {
                        Timber.w("streamConfigInternal is null in onFrameAvailableListener, closing frame.")
                        try { imageProxy.close() } catch(e: Exception) {} // Close proxy if config is unexpectedly null
                    }
                }

                // Start CameraX
                cameraManager.startCamera(
                    // Tell CameraX we won't provide a preview surface
                    surfaceProvider = { request -> request.willNotProvideSurface() } ,
                    targetResolution = android.util.Size(config.videoResolutionWidth, config.videoResolutionHeight),
                    requireAudio = config.hasAudio // Pass audio requirement hint
                )

                Timber.i("Streaming service fully started.")
                updateNotification("Streaming active: ${config.mode}")

                // Coroutine stays alive while streaming components are active

            } catch (e: CancellationException) {
                Timber.i("Streaming job cancelled: ${e.message}") // Expected when stopping normally
                // Cleanup should be handled by ensuring stopStreamingInternal runs if job is cancelled
            } catch (e: Exception) {
                Timber.e(e, "Error during streaming service startup or operation")
                updateNotification("Error: ${e.localizedMessage}")
                _networkInfo.value = _networkInfo.value.copy(isServerRunning = false, error = e.localizedMessage) // Update status on error
                // Ensure full cleanup happens on error
                stopStreamingInternal() // Call cleanup directly
            } finally {
                // Ensure cleanup runs even if cancellation wasn't caught (e.g. external scope cancellation)
                Timber.v("Coroutine finally block reached.")
            }
        }

        // Handle job completion/cancellation for cleanup (alternative to finally)
        serviceJob?.invokeOnCompletion { throwable ->
            val cause = throwable ?: CancellationException("Job completed normally")
            Timber.i("Streaming job completed: $cause")
            if (_isStreaming.value){ // Prevent cleanup if already stopped
                stopStreamingInternal()
            }
        }
    }

    // This function is called by the CameraManager's ImageAnalysis.Analyzer callback
    // It now runs inside a coroutine launched from the listener.
    // IMPORTANT: imageProxy MUST be closed.
    // Mark as suspend because it calls suspend function arFrameProcessor.processFrame
    private suspend fun processFrame(imageProxy: ImageProxy, config: StreamConfig) {
        val startTime = System.nanoTime()
        try {
            // Check streaming state again inside the coroutine, though launch might be cancelled too
            if (!_isStreaming.value) {
                // imageProxy.close() // Close is handled in finally block
                return
            }
            // Timber.v("Processing frame: ${imageProxy.width}x${imageProxy.height}, TS: ${imageProxy.imageInfo.timestamp}")

            // --- AR Data Processing ---
            var arPacket: ArDataPacket? = null
            if (config.hasAr && arCoreSessionManager.trackingState.value == ArTrackingState.TRACKING) {
                val latestArFrame = arCoreSessionManager.latestArFrame.value
                // Match timestamps if possible (best effort)
                if (latestArFrame != null /* && latestArFrame.timestamp == imageProxy.imageInfo.timestamp */) {
                    // Call the suspend function directly as we are in a suspend context
                    arPacket = arFrameProcessor.processFrame(latestArFrame, config.includeDepth)
                }
            }

            // --- Video Encoding ---
            var encodedVideo: ByteBuffer? = null
            var videoTimestampUs: Long? = null
            var isKeyFrame = false
            if (config.hasVideo && videoEncoder != null) {
                // TODO: Feed YUV data from imageProxy into videoEncoder
                // This is complex: involves color format conversion (YUV_420_888 to encoder format),
                // handling input/output buffers asynchronously.
                // encodedVideo = encodeVideoFrame(imageProxy) // Placeholder function call
                // videoTimestampUs = imageProxy.imageInfo.timestamp / 1000 // Convert ns to us
                // isKeyFrame = ... // Determined by encoder output flags
                Timber.v("Video encoding placeholder - frame received")
            }


            // --- Audio Encoding ---
            // Audio is usually handled separately. MediaRecorder handles this if used.
            var encodedAudio: ByteBuffer? = null
            var audioTimestampUs: Long? = null
            // Placeholder: Assume audio encoder provides data periodically
            // encodedAudio = getEncodedAudioChunk()
            // audioTimestampUs = ...


            // --- Muxing and Sending ---
            if (encodedVideo != null || encodedAudio != null || arPacket != null) {
                // Assume RtspServer.dataMuxer is accessible (public/internal)
                // Add null check or handle potential NPE if not initialized properly
                rtspServer.dataMuxer?.processData(
                    encodedVideo, videoTimestampUs, isKeyFrame,
                    encodedAudio, audioTimestampUs,
                    arPacket
                )?.let { streamData ->
                    // Assume RtspServer.pushData is accessible (public/internal)
                    rtspServer.pushData(streamData)
                }

            }

        } catch (e: Exception) {
            // Don't propagate CancellationException if the job was cancelled normally
            if (e !is CancellationException) {
                Timber.e(e, "Error processing frame")
            } else {
                Timber.v("Frame processing cancelled.") // Normal during shutdown
                throw e // Re-throw cancellation
            }
            // Handle other errors appropriately
        } finally {
            // Prevent potential crash if imageProxy was already closed somehow
            try {
                imageProxy.close() // VERY IMPORTANT: Close the ImageProxy
            } catch (e: IllegalStateException) {
                Timber.w("ImageProxy already closed in processFrame.")
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            // Avoid logging excessively in production for performance
            if (durationMs > 50) { // Log only if processing takes significant time
                Timber.w("Frame processing took ${durationMs}ms")
            } else {
                Timber.v("Frame processing took ${durationMs}ms")
            }
            // If duration is consistently high, optimizations are needed!
        }
    }

    // Placeholder - Replace with actual MediaCodec implementation
    private fun setupEncoders(config: StreamConfig) {
        Timber.d("Setting up encoders for config: $config")
        releaseEncoders() // Clean up previous ones

        // --- Video Encoder Setup (MediaCodec - Complex) ---
        if (config.hasVideo) {
            try {
                videoFormat = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, // H.264
                    config.videoResolutionWidth,
                    config.videoResolutionHeight
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Key frame interval in seconds (e.g., 1 second)
                    // Add other parameters like profile/level if needed
                }
                videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                videoEncoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                // Get input surface if using surface input, or prepare for buffer input
                // videoEncoder?.setInputSurface(...)
                videoEncoder?.start()
                Timber.i("Video Encoder (H.264) configured and started.")
            } catch (e: IOException) {
                Timber.e(e, "Failed to setup video encoder")
                _networkInfo.value = _networkInfo.value.copy(error = "Video Encoder setup failed")
                // Handle error - maybe stop streaming? Propagate?
                throw e // Re-throw to be caught by startStreaming's catch block
            } catch (e: IllegalStateException){
                Timber.e(e, "Illegal state during video encoder setup")
                _networkInfo.value = _networkInfo.value.copy(error = "Video Encoder setup failed (State)")
                throw e // Re-throw
            } catch(e: MediaCodec.CodecException) {
                Timber.e(e, "MediaCodec exception during video encoder setup (Check parameters/formats)")
                _networkInfo.value = _networkInfo.value.copy(error = "Video Codec setup failed (${e.diagnosticInfo})")
                throw e
            }
        }

        // --- Audio Encoder Setup (MediaRecorder - Simpler) ---
        if (config.hasAudio) {
            try {
                // MediaRecorder is simpler for audio capture & encoding
                audioRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                audioRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC) // Requires RECORD_AUDIO permission
                    setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS) // Suitable for streaming AAC
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(config.audioSampleRate) // Use config value
                    setAudioEncodingBitRate(config.audioBitrate)
                    setAudioChannels(config.audioChannels) // Use config value
                    // Output to a file descriptor or pipe that the RTSP server can read.
                    // This requires specific integration with the RtspServer implementation.
                    // Using "/dev/null" is a placeholder and won't work for actual streaming.
                    // You might need ParcelFileDescriptor.createPipe() or similar.
                    setOutputFile("/dev/null") // <<< Placeholder: Needs replacement for streaming
                    prepare()
                    start()
                    Timber.i("Audio Recorder (AAC via MediaRecorder) prepared and started.")
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to setup audio recorder")
                _networkInfo.value = _networkInfo.value.copy(error = "Audio Recorder setup failed")
                releaseEncoders() // Clean up video encoder if audio fails
                throw e // Re-throw
            } catch (e: IllegalStateException) {
                Timber.e(e, "Illegal state during audio recorder setup (Check order of calls, MIC permission)")
                // Common causes: setAudioSource after setOutputFormat, permission denied.
                _networkInfo.value = _networkInfo.value.copy(error = "Audio Recorder setup failed (State)")
                releaseEncoders()
                throw e // Re-throw
            } catch (e: RuntimeException) {
                Timber.e(e, "Runtime exception during audio recorder setup (Device specific?)")
                _networkInfo.value = _networkInfo.value.copy(error = "Audio Recorder setup failed (Runtime)")
                releaseEncoders()
                throw e
            }
        }
    }


    private fun releaseEncoders() {
        Timber.d("Releasing encoders...")
        // Video Encoder
        videoEncoder?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Timber.w(e,"Exception stopping video encoder (might be already stopped/released)")
            } finally {
                try {
                    it.release()
                } catch (e: Exception) {
                    // Ignore? Log?
                    Timber.e(e, "Exception releasing video encoder")
                }
            }
        }
        videoEncoder = null
        videoFormat = null

        // Audio Recorder
        audioRecorder?.let {
            try {
                // Order for MediaRecorder: stop, reset, release
                it.stop()
            } catch (e: IllegalStateException) {
                Timber.w(e, "Exception stopping audio recorder (might be already stopped?)")
            } catch (e: RuntimeException) {
                // stop() can throw RuntimeException if called at wrong time
                Timber.w(e, "Runtime exception stopping audio recorder")
            } finally {
                try {
                    it.reset() // Use reset before release for MediaRecorder
                } catch (e: Exception){
                    Timber.e(e, "Exception resetting audio recorder")
                } finally {
                    try {
                        it.release()
                    } catch (e: Exception) {
                        Timber.e(e, "Exception releasing audio recorder")
                    }
                }
            }
        }
        audioRecorder = null
        Timber.d("Encoders released.")
    }

    // Public stop function requested by user/action
    private fun stopStreaming() {
        if (serviceJob?.isActive != true && !_isStreaming.value) {
            Timber.w("Streaming not active or already stopping, cannot stop.")
            return
        }
        Timber.i("Request received to stop streaming...")
        // Cancel the main service job, completion handler or finally block should trigger cleanup
        serviceJob?.cancel(CancellationException("Streaming stopped by user request"))
        // Don't nullify job here, let completion handler do it after cleanup
        // Call internal stop just in case job cancellation doesn't run/completes immediately
        // Added delay might cause race condition, better rely on cancellation propagation
        // stopStreamingInternal() // Avoid calling directly here, rely on invokeOnCompletion/finally
    }

    // Internal function containing the actual cleanup logic - make it thread-safe
    @Synchronized // Ensure only one thread executes cleanup at a time
    private fun stopStreamingInternal() {
        if (!_isStreaming.value) {
            Timber.v("stopStreamingInternal: Already stopped or stopping.")
            return
        }
        Timber.i("Executing streaming stop cleanup...")
        _isStreaming.value = false // Set state immediately to prevent redundant calls/race conditions

        // Release WakeLock FIRST
        releaseWakeLock()

        // Explicitly cancel job again just to be safe
        if (serviceJob?.isActive == true) {
            serviceJob?.cancel(CancellationException("Streaming stop cleanup invoked"))
        }
        // Nullify job reference here AFTER cancellation attempt
        serviceJob = null

        // Stop Camera and clear listener
        // Add try-catch as camera interaction can sometimes throw state exceptions during shutdown
        try {
            cameraManager.onFrameAvailableListener = null // Clear listener immediately
            cameraManager.stopCamera()
            Timber.d("Camera stopped.")
        } catch(e: Exception) {
            Timber.e(e, "Error stopping camera during cleanup.")
        }


        // Release Encoders
        // Run on IO dispatcher might be problematic if called from main thread during onDestroy syncronously
        // runBlocking is okay if not called from main UI thread directly.
        // Since this can be called from job completion (likely background) or onDestroy (main thread),
        // Avoid runBlocking if possible. releaseEncoders() should be relatively quick.
        try {
            releaseEncoders()
        } catch(e: Exception) {
            Timber.e(e, "Error releasing encoders.")
        }


        // Stop Network Components
        try {
            nsdHelper.unregisterService()
            // Assume stop() is public/internal and handles internal state safely
            rtspServer.stop()
            webServer.stop()
            Timber.d("Network components stopped.")
        } catch(e: Exception) {
            Timber.e(e, "Error stopping network components.")
        }


        // Stop Foreground Service and remove notification
        stopForeground(STOP_FOREGROUND_REMOVE) // stopForeground(true) is deprecated

        // Update State - Already set _isStreaming = false earlier
        streamConfigInternal = null // Clear internal config
        _streamConfig.value = null
        // Only reset network info if no error caused the stop, otherwise keep error info
        // Check can be added based on whether cleanup was triggered by error or normal stop
        _networkInfo.value = NetworkInfo() // Reset network info

        Timber.i("Streaming service stop cleanup finished.")

        // Consider stopping the service itself if no longer needed
        stopSelf()
    }


    private fun collectServerStates() {
        lifecycleScope.launch {
            // Define the flow for IP address separately for clarity
            val ipAddressFlow: Flow<String?> = flow { emit(wifiHelper.getWifiIpAddress()) }
                .flowOn(ioDispatcher) // Ensure getWifiIpAddress is called off the main thread if needed
                .onStart { Timber.v("IP Flow started collection") }
                .catch { e ->
                    Timber.e(e, "Error fetching IP address")
                    emit("Error") // Emit an error string or null
                }


            // *** DEBUGGING STEP: Check flow types explicitly ***
            Timber.d("Flow types: rtsp.isRunning=${rtspServer.isRunning::class}, web.isRunning=${webServer.isRunning::class}, rtsp.clientCount=${rtspServer.clientCount::class}")

            // Explicitly specify the generic types for combine - this can sometimes help inference
            combine(
                rtspServer.isRunning,      // Flow<Boolean>
                rtspServer.clientCount,    // Flow<Int>
                rtspServer.error,          // Flow<String?>
                webServer.isRunning,       // Flow<Boolean>
                webServer.error,           // Flow<String?>
                ipAddressFlow              // Flow<String?>
            )
            // Keep the lambda without parameter types for inference, OR specify them EXACTLY
            { values -> // The lambda receives an array if generic types aren't listed individually
                // Extract values from the array based on the order they were passed to combine
                val rtspRunning = values[0] as Boolean
                val clients = values[1] as Int
                val rtspErr = values[2] as String?
                val webRunning = values[3] as Boolean
                val webErr = values[4] as String?
                val ip = values[5] as String?


                val combinedError = listOfNotNull(rtspErr, webErr).joinToString("; ").ifEmpty { null }
                // Cast values[0] and values[3] to Boolean if needed here for the OR check,
                // though they should already be Boolean from the Array typing above.
                val serverRunning = rtspRunning || webRunning

                // Access 'port' - Ensure RtspServer.kt/WebServer.kt expose this (internal/public or getter)
                val currentPort = when {
                    rtspRunning -> rtspServer.port
                    webRunning -> webServer.port
                    else -> -1
                }

                NetworkInfo(
                    ipAddress = if (ip == "Error") "N/A" else (ip ?: "N/A"),
                    port = currentPort,
                    isServerRunning = serverRunning,
                    clientCount = clients,
                    error = combinedError,
                    currentBitrateKbps = 0,
                    latencyMs = 0f
                )
            }.catch { e ->
                Timber.e(e, "Error in server state collection flow (combine or upstream)")
                // Don't flip isServerRunning here, just report error
                _networkInfo.value = _networkInfo.value.copy(error = "State collection failed: ${e.message}")
            }.collectLatest { newState ->
                _networkInfo.value = newState
                if (!_isStreaming.value && newState.isServerRunning) {
                    // If service stopped but flows report running, update UI state.
                    // This indicates a possible state mismatch, log it.
                    Timber.w("State mismatch: Service stopped but server state reports running.")
                    // Optionally force network info to reflect stopped state:
                    // _networkInfo.value = NetworkInfo()
                } else if (!_isStreaming.value) {
                    return@collectLatest // Skip notification update if service is stopped.
                }

                // Logic for updating notification text remains the same...
                val statusPrefix = streamConfigInternal?.mode?.toString() ?: "Streaming"
                val portString = if (newState.port != -1) ":${newState.port}" else ""
                val notificationText = when {
                    newState.error != null -> "Error: ${newState.error}"
                    newState.isServerRunning && newState.ipAddress != "N/A" && newState.ipAddress != "Error" ->
                        "$statusPrefix: ${newState.clientCount} client(s) @ ${newState.ipAddress}$portString"
                    newState.isServerRunning ->
                        "$statusPrefix: ${newState.clientCount} client(s) @ Starting..."
                    else -> "$statusPrefix: Initializing..."
                }
                updateNotification(notificationText)
            }
        }
    }


    // --- Notification Handling ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Streaming Service",
                NotificationManager.IMPORTANCE_LOW // Use LOW to minimize interruption
            ).apply {
                description = "Notification channel for CamEye streaming status"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            if(manager == null) {
                Timber.e("NotificationManager not found.")
                return
            }
            try {
                manager.createNotificationChannel(channel)
                Timber.d("Notification channel created.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create notification channel")
            }
        }
    }

    // Add a default placeholder drawable resource ID
    private val defaultSmallIconRes = android.R.drawable.sym_def_app_icon // A standard system icon as fallback


    private fun createNotification(contentText: String): Notification {
        createNotificationChannel() // Ensure channel exists

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val largeIcon = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load large notification icon (R.mipmap.ic_launcher)")
            null
        }

        // *** ACTION REQUIRED for R.drawable.ic_videocam ***
        // Ensure this drawable exists in `app/src/main/res/drawable/`
        val smallIconResId = try {
            R.drawable.ic_videocam
        } catch (e: Resources.NotFoundException) {
            Timber.e("Small notification icon 'ic_videocam' not found! Using fallback.")
            defaultSmallIconRes
        } catch (e: NoClassDefFoundError) { // Catch if R class generation failed
            Timber.e(e, "Small notification icon 'ic_videocam' could not be referenced (R class issue?). Using fallback.")
            defaultSmallIconRes
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CamEye AR Streamer")
            .setContentText(contentText)
            .setSmallIcon(smallIconResId) // Use the determined or fallback icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Make it non-dismissable while foreground
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // Show immediately
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use low priority
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // Hide sensitive info on lock screen

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String) {
        if (!_isStreaming.value && !contentText.startsWith("Error:")) { // Don't update if stopped, unless showing final error
            Timber.v("Skipping notification update as service is stopped.")
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) {
            Timber.e("NotificationManager not found, cannot update notification.")
            return
        }
        try {
            val notification = createNotification(contentText)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/notify notification")
        }
    }


    // --- WakeLock Handling ---
    // Suppress the lint warning about timeout, as foreground services manage their lifecycle
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        // Ensure wakelock operations don't happen on main thread if called from there
        if (wakeLock == null || wakeLock?.isHeld == false) { // Check if null OR not held
            try {
                Timber.d("Acquiring WakeLock...")
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamEye::StreamingWakeLock").apply {
                    acquire() // No timeout needed for foreground service
                }
                Timber.i("Partial WakeLock acquired.")
            } catch(e: Exception) {
                Timber.e(e, "Failed to acquire WakeLock.")
                wakeLock = null // Ensure it's null if acquire fails
            }
        } else {
            Timber.w("WakeLock already held.")
        }
    }

    private fun releaseWakeLock() {
        if(wakeLock?.isHeld == true) { // Only release if non-null and held
            try {
                Timber.d("Releasing WakeLock...")
                wakeLock?.release()
                Timber.i("WakeLock released.")
            } catch (e: RuntimeException) {
                Timber.w(e, "Error releasing WakeLock (might be already released).")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error releasing WakeLock.")
            } finally {
                wakeLock = null // Set to null after attempting release
            }
        } else if (wakeLock != null){
            Timber.w("Attempted to release WakeLock that was not held.")
            wakeLock = null // Set to null if it exists but wasn't held
        } else {
            Timber.v("ReleaseWakeLock called but WakeLock was already null.")
        }
    }

    override fun onDestroy() {
        Timber.w("StreamingService onDestroy executing") // Use warning level to highlight destruction
        // Call cleanup, ensuring it doesn't run redundantly if already stopped
        stopStreamingInternal()
        // Clean up non-streaming resources AFTER ensuring streaming is stopped
        Timber.d("onDestroy: Releasing managers and observers...")
        // Use try-catch for external components that might throw on destroy/release
        try {
            lifecycle.removeObserver(arCoreSessionManager)
        } catch (e: Exception) { Timber.e(e, "Error removing AR observer") }
        try {
            cameraManager.release()
        } catch (e: Exception) { Timber.e(e, "Error releasing CameraManager") }
        try {
            arCoreSessionManager.destroy()
        } catch (e: Exception) { Timber.e(e, "Error destroying ARCoreSessionManager") }
        try {
            rtspServer.release() // Assuming release methods exist
        } catch (e: Exception) { Timber.e(e, "Error releasing RtspServer") }
        try {
            webServer.release()
        } catch (e: Exception) { Timber.e(e, "Error releasing WebServer") }
        // Release wakelock as final step in case cleanup failed to release it
        releaseWakeLock()

        super.onDestroy() // Call superclass method last in onDestroy
        Timber.i("StreamingService fully destroyed.")
    }
}

// Helper extension function to get Parcelable extras (handles deprecation)
// Keep this helper function as it's useful and correct
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T? // Add nullable T? for safety
}