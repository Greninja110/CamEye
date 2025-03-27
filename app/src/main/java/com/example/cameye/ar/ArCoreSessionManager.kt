package com.example.cameye.ar

import android.app.Activity
import android.content.Context
import android.opengl.GLES30 // Use GLES30 for ARCore
import android.view.Display
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArCoreSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    private val sessionCoroutineScope = CoroutineScope(Dispatchers.IO + Job()) // IO for potential blocking calls

    var session: Session? = null
        private set

    private var installRequested = false
    private var displayRotationHelper: DisplayRotationHelper? = null

    private val _trackingState = MutableStateFlow(ArTrackingState.INITIALIZING)
    val trackingState: StateFlow<ArTrackingState> = _trackingState.asStateFlow()

    private val _latestArFrame = MutableStateFlow<Frame?>(null)
    val latestArFrame: StateFlow<Frame?> = _latestArFrame.asStateFlow() // Expose for processor


    // Call this when the Activity/Fragment resumes
    override fun onResume(owner: LifecycleOwner) {
        Timber.d("ARCoreSessionManager onResume")
        if (session == null) {
            sessionCoroutineScope.launch {
                createSession()
            }
        } else {
            resumeSession()
        }
        displayRotationHelper?.onResume()
    }

    // Call this when the Activity/Fragment pauses
    override fun onPause(owner: LifecycleOwner) {
        Timber.d("ARCoreSessionManager onPause")
        displayRotationHelper?.onPause()
        pauseSession()
    }

    // Call this when the manager is no longer needed (e.g., service destroy)
    fun destroy() {
        Timber.d("ARCoreSessionManager destroy")
        sessionCoroutineScope.cancel("ARCoreSessionManager destroyed")
        session?.close()
        session = null
    }

    private suspend fun createSession() {
        Timber.d("Attempting to create ARCore session")
        // Check if ARCore is supported and installed.
        if (!checkArCoreAvailability()) {
            _trackingState.value = ArTrackingState.UNSUPPORTED
            return
        }

        try {
            // Create the ARCore session.
            withContext(Dispatchers.Main){ // Session creation might need main thread access internally
                session = Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA)) // Use SHARED_CAMERA with CameraX
            }


            // Configure the session. Enable depth mode.
            val config = Config(session)
            if (session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) {
                Timber.i("Depth mode AUTOMATIC is supported.")
                config.depthMode = Config.DepthMode.AUTOMATIC
                // Use DEPTH_POINT_CLOUD for raw point cloud data if needed
                // Use DEPTH_TEXTURE for CPU/GPU accessible depth map
            } else {
                Timber.w("Depth mode AUTOMATIC is NOT supported.")
                // Handle case where depth is not supported if it's critical
                config.depthMode = Config.DepthMode.DISABLED
            }

            // Set other configurations as needed (e.g., lighting estimation, plane finding)
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR // Good for realism
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL // Find surfaces
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE // Process frames ASAP

            // Check configuration support
            if (!session!!.isSupported(config)) {
                Timber.e("ARCore config not supported!")
                // Try a fallback configuration?
                config.depthMode = Config.DepthMode.DISABLED // Example fallback
                if (!session!!.isSupported(config)) {
                    Timber.e("Fallback ARCore config also not supported!")
                    _trackingState.value = ArTrackingState.ERROR
                    session?.close()
                    session = null
                    return
                } else {
                    Timber.w("Using fallback ARCore configuration (no depth).")
                }
            }

            session?.configure(config)
            Timber.i("ARCore session configured: DepthMode=${config.depthMode}, LightEstimation=${config.lightEstimationMode}")

            // Initialize DisplayRotationHelper here, requires activity context ideally
            // For now, create it here but it might need adjustment if called from service
            displayRotationHelper = DisplayRotationHelper(context)

            resumeSession() // Resume after creation and configuration

        } catch (e: UnavailableArcoreNotInstalledException) {
            Timber.e(e, "ARCore not installed.")
            requestArCoreInstallation()
            _trackingState.value = ArTrackingState.UNSUPPORTED
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Timber.e(e, "User declined ARCore installation.")
            _trackingState.value = ArTrackingState.UNSUPPORTED
        } catch (e: UnavailableApkTooOldException) {
            Timber.e(e, "ARCore APK too old.")
            _trackingState.value = ArTrackingState.UNSUPPORTED
        } catch (e: UnavailableSdkTooOldException) {
            Timber.e(e, "ARCore SDK too old.")
            _trackingState.value = ArTrackingState.ERROR
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Timber.e(e, "Device not compatible with ARCore.")
            _trackingState.value = ArTrackingState.UNSUPPORTED
        } catch (e: CameraNotAvailableException) {
            Timber.e(e, "Camera not available for ARCore.")
            _trackingState.value = ArTrackingState.ERROR // Or maybe NOT_TRACKING?
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException during ARCore session creation (Permissions?).")
            _trackingState.value = ArTrackingState.ERROR
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ARCore session")
            _trackingState.value = ArTrackingState.ERROR
            session?.close()
            session = null
        }
    }

    fun setCameraTextureName(textureId: Int) {
        session?.setCameraTextureName(textureId)
    }

    // Call this frequently (e.g., on every CameraX frame analysis or render loop)
    // Needs the camera frame timestamp.
    fun updateSession(frameTimeNanos: Long, frameSize: android.util.Size) {
        if (session == null) return

        try {
            // Notify ARCore session of display rotation changes.
            displayRotationHelper?.updateSessionIfNeeded(session!!)

            // Set projection matrix - needed if rendering AR content, maybe not just for data streaming
            // val camera = frame.camera
            // camera.getProjectionMatrix(projmtx, 0, Z_NEAR, Z_FAR)
            // camera.getViewMatrix(viewmtx, 0)

            // Update the ARCore session. This consumes the latest camera image / texture.
            val frame = session?.update()

            if(frame == null) {
                Timber.v("ARCore frame is null")
                // Consider updating tracking state if consistently null
                return
            }

            _latestArFrame.value = frame // Emit the latest frame

            // Update tracking state based on the frame's camera tracking state
            val cameraTrackingState = frame.camera?.trackingState
            when (cameraTrackingState) {
                TrackingState.TRACKING -> _trackingState.value = ArTrackingState.TRACKING
                TrackingState.PAUSED -> _trackingState.value = ArTrackingState.NOT_TRACKING
                TrackingState.STOPPED -> _trackingState.value = ArTrackingState.NOT_TRACKING
                null -> _trackingState.value = ArTrackingState.INITIALIZING // Or ERROR?
            }

            // Handle tracking failures
            if (cameraTrackingState == TrackingState.PAUSED || cameraTrackingState == TrackingState.STOPPED) {
                val failureReason = frame.camera?.trackingFailureReason
                if (failureReason != TrackingFailureReason.NONE) {
                    Timber.w("AR Tracking Failure: $failureReason")
                    // Potentially show user feedback based on the reason
                }
            }

        } catch (e: CameraNotAvailableException) {
            Timber.e(e, "Camera not available during ARCore update")
            _trackingState.value = ArTrackingState.ERROR
            // Handle error, potentially stop session or notify user
        } catch (e: DeadlineExceededException) {
            Timber.w(e, "ARCore update deadline exceeded.")
            // This can happen under heavy load. Might need optimization.
        } catch (e: ResourceExhaustedException) {
            Timber.e(e, "ARCore resource exhausted.")
            _trackingState.value = ArTrackingState.ERROR
            // Might need to reduce load or restart session.
        }
        catch (e: Exception) {
            Timber.e(e, "Exception during ARCore session update")
            _trackingState.value = ArTrackingState.ERROR // Generic error
        }
    }


    private fun resumeSession() {
        sessionCoroutineScope.launch {
            try {
                session?.resume()
                Timber.i("ARCore session resumed.")
                // Initial state might still be initializing until first frame tracked
                if (_trackingState.value != ArTrackingState.ERROR && _trackingState.value != ArTrackingState.UNSUPPORTED) {
                    _trackingState.value = ArTrackingState.INITIALIZING
                }
            } catch (e: CameraNotAvailableException) {
                Timber.e(e, "Failed to resume ARCore session: Camera not available.")
                _trackingState.value = ArTrackingState.ERROR
                session = null // Session is likely invalid now
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to resume ARCore session: Security Exception (Permissions?).")
                _trackingState.value = ArTrackingState.ERROR
                session = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume ARCore session")
                _trackingState.value = ArTrackingState.ERROR
                // Consider cleaning up session state here
            }
        }
    }

    private fun pauseSession() {
        session?.pause()
        Timber.i("ARCore session paused.")
        if (_trackingState.value == ArTrackingState.TRACKING || _trackingState.value == ArTrackingState.INITIALIZING) {
            _trackingState.value = ArTrackingState.NOT_TRACKING // Paused means not tracking
        }
    }

    private fun checkArCoreAvailability(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            // Re-check availability later.
            sessionCoroutineScope.launch {
                kotlinx.coroutines.delay(200) // Wait briefly
                checkArCoreAvailability() // Recursive call, be careful
            }
            return false // Not available yet
        }
        return if (availability.isSupported) {
            Timber.i("ARCore is supported and installed.")
            true
        } else { // Unsupported or requires installation.
            Timber.w("ARCore not supported or not installed: $availability")
            if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
                Timber.e("ARCore is unsupported on this device.")
            } else if (availability == ArCoreApk.Availability.UNKNOWN_CHECKING || availability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT) {
                Timber.w("ARCore availability check inconclusive, retrying later.")
                // Potentially schedule another check
            } else { // Likely needs installation
                requestArCoreInstallation()
            }
            false
        }
    }

    private fun requestArCoreInstallation() {
        if (installRequested) return // Avoid multiple requests
        try {
            when (ArCoreApk.getInstance().requestInstall(context as Activity, true /* User requested */)) {
                ArCoreApk.InstallStatus.INSTALLED -> {
                    Timber.i("ARCore installed successfully after request.")
                    // Installation successful. Create the session (will happen on next resume/check)
                    installRequested = false // Reset flag
                }
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    Timber.i("ARCore installation requested. Waiting for result.")
                    installRequested = true // Prevent new requests until resolved. Handled by ARCore system activity.
                }
            }
        } catch (e: UnavailableException) {
            Timber.e(e, "Failed to request ARCore installation.")
            _trackingState.value = ArTrackingState.UNSUPPORTED // Treat as unsupported if install fails
        } catch (e: ClassCastException) {
            Timber.e(e, "Cannot request ARCore install - Context is not an Activity.")
            // This is a limitation if called directly from a Service context
            _trackingState.value = ArTrackingState.UNSUPPORTED
        }
    }
}