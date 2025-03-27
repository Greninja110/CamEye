package com.example.cameye.ui.preview

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cameye.ar.ArTrackingState
import com.example.cameye.data.model.NetworkInfo
import com.example.cameye.ui.components.ArStatusIndicator
import com.example.cameye.ui.components.LoadingIndicator
import com.example.cameye.ui.components.StreamQuality
import com.example.cameye.ui.components.StreamQualityIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.cameye.ui.viewmodel.CameraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CameraPreviewScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onStopStreaming: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe StateFlows from ViewModel
    val cameraState by viewModel.cameraState.collectAsState()
    val arState by viewModel.arTrackingState.collectAsState()
    val networkInfo by viewModel.networkInfo.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideControlsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Simple flag to prevent multiple taps while focusing
    var allowTapToFocus by remember { mutableStateOf(true) }


    // Function to schedule hiding controls
    fun scheduleHideControls() {
        hideControlsJob?.cancel() // Cancel previous hide job
        hideControlsJob = scope.launch {
            delay(3000) // Hide after 3 seconds of inactivity
            showControls = false
        }
    }

    // Start hide schedule initially
    LaunchedEffect(Unit) {
        scheduleHideControls()
    }

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = { Text("CamEye Streamer") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    actions = {
                        // TODO: Add settings icon if needed
                        // IconButton(onClick = { /* Navigate to Settings */ }) {
                        //     Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        // }
                    }
                )
            }
        },
        containerColor = Color.Black // Ensure background is black for camera preview
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Detect taps on the preview area
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            showControls = true // Show controls on tap
                            scheduleHideControls() // Reschedule hide
                            true // Consume event
                        }
                        MotionEvent.ACTION_UP -> {
                            // Trigger focus on tap up if allowed
                            if (allowTapToFocus && !cameraState.isFocusing) {
                                allowTapToFocus = false // Prevent rapid taps
                                val x = event.x / (context.resources.displayMetrics.widthPixels) // Normalize
                                val y = event.y / (context.resources.displayMetrics.heightPixels)
                                Timber.d("Tap detected at ($x, $y), triggering focus.")
                                viewModel.triggerFocus(x, y)
                                scope.launch {
                                    delay(500) // Debounce focus trigger
                                    allowTapToFocus = true
                                }
                            }
                            true // Consume event
                        }
                        else -> false // Don't consume other events
                    }
                }
        ) {
            // Camera Preview Viewfinder
            if (isStreaming) { // Only show preview if streaming is active (service running)
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceProviderReady = { surfaceProvider ->
                        viewModel.setSurfaceProvider(surfaceProvider)
                    }
                )
            } else {
                // Show loading or stopped state if service isn't streaming
                LoadingIndicator() // Or a message indicating stopped state
            }


            // Status Indicators (Top Right)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ArStatusIndicator(trackingState = arState)
                Spacer(modifier = Modifier.height(8.dp))
                // Determine quality based on network info (example logic)
                val quality = when {
                    networkInfo.clientCount == 0 -> StreamQuality.UNKNOWN // No clients, quality N/A
                    networkInfo.error != null -> StreamQuality.POOR
                    // TODO: Add more sophisticated quality logic based on bitrate, latency, packet loss
                    networkInfo.currentBitrateKbps > 1500 -> StreamQuality.GOOD
                    networkInfo.currentBitrateKbps > 500 -> StreamQuality.MEDIUM
                    else -> StreamQuality.POOR
                }
                StreamQualityIndicator(
                    quality = quality,
                    bitrateKbps = networkInfo.currentBitrateKbps
                )
                // Display IP and Port when controls are visible
                if (showControls && networkInfo.isServerRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${networkInfo.ipAddress}:${networkInfo.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Camera Controls Overlay
            if (showControls) {
                CameraControlsOverlay(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f)) // Semi-transparent background
                        .padding(16.dp),
                    cameraState = cameraState,
                    onZoomChanged = { ratio -> viewModel.setZoomRatio(ratio) },
                    // onExposureChanged = { ev -> viewModel.setExposure(ev) }, // Add slider if needed
                    onStopClicked = onStopStreaming
                )
            }

            // Display error messages
            networkInfo.error?.let { errorMsg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    action = {
                        Button(onClick = { viewModel.clearNetworkError() }) { Text("Dismiss") }
                    }
                ) {
                    Text(text = "Error: $errorMsg")
                }
            }
            cameraState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp), // Avoid overlap with other snackbar/topbar
                    action = {
                        Button(onClick = { viewModel.clearCameraError() }) { Text("Dismiss") }
                    }
                ) {
                    Text(text = "Camera Error: ${error.localizedMessage}")
                }
            }
            if(arState == ArTrackingState.ERROR || arState == ArTrackingState.UNSUPPORTED) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 140.dp, start = 16.dp, end = 16.dp),
                    action = {
                        // Maybe add retry logic?
                        // Button(onClick = { /* viewModel.retryArInitialization() */ }) { Text("Retry") }
                    }
                ) {
                    Text(text = if (arState == ArTrackingState.UNSUPPORTED) "ARCore Unsupported/Not Installed" else "ARCore Error")
                }
            }
        }
    }
}