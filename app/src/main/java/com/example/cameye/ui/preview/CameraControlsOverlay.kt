package com.example.cameye.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cameye.camera.CameraState
import com.example.cameye.ui.components.CameraControlButton
import kotlin.math.roundToInt

@Composable
fun CameraControlsOverlay(
    modifier: Modifier = Modifier,
    cameraState: CameraState,
    onZoomChanged: (Float) -> Unit,
    // onExposureChanged: (Int) -> Unit, // Add callback for exposure slider
    onStopClicked: () -> Unit
) {
    var showZoomSlider by remember { mutableStateOf(false) }
    // Local state for slider position if needed for smooth updates
    var currentZoomSliderPosition by remember(cameraState.zoomRatio) { mutableFloatStateOf(cameraState.zoomRatio) }


    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Zoom Controls ---
        if (showZoomSlider && cameraState.maxZoom > cameraState.minZoom) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Min Zoom", tint = Color.White)
                Slider(
                    value = currentZoomSliderPosition,
                    onValueChange = { newValue ->
                        currentZoomSliderPosition = newValue
                        onZoomChanged(newValue) // Send updates continuously
                    },
                    valueRange = cameraState.minZoom..cameraState.maxZoom,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Icon(Icons.Default.ZoomIn, contentDescription = "Max Zoom", tint = Color.White)
                // Display current zoom level
                Text(
                    "${(currentZoomSliderPosition * 10).roundToInt() / 10.0}x", // Format to 1 decimal place
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Main Control Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom Toggle/Button
            CameraControlButton(
                icon = if (showZoomSlider) Icons.Default.ZoomOut /* Change icon? */ else Icons.Default.ZoomIn,
                contentDescription = if (showZoomSlider) "Hide Zoom" else "Show Zoom",
                onClick = { showZoomSlider = !showZoomSlider },
                // Disable if zoom not supported
                // enabled = cameraState.maxZoom > cameraState.minZoom
            )


            // Stop Button (Large center button)
            Button(
                onClick = onStopClicked,
                modifier = Modifier.size(64.dp), // Make stop button larger
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = Icons.Filled.StopCircle,
                    contentDescription = "Stop Streaming",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Placeholder for Exposure Control button
            CameraControlButton(
                // icon = Icons.Default.Exposure, // Need Exposure icon
                icon = Icons.Default.ZoomOut, // Placeholder icon
                contentDescription = "Exposure (Not Implemented)",
                onClick = { /* TODO: Show exposure slider */ },
                // enabled = cameraState.exposureRange.start != cameraState.exposureRange.endInclusive
            )
        }
    }
}