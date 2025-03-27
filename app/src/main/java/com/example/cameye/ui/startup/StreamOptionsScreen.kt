package com.example.cameye.ui.startup

// Ensure these imports are present (or androidx.compose.runtime.*)
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
// Other necessary imports...
import com.example.cameye.data.model.StreamMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cameye.data.model.StreamConfig
import com.example.cameye.ui.viewmodel.StreamOptionsViewModel


@Composable
fun StreamOptionsScreen(
    viewModel: StreamOptionsViewModel = hiltViewModel(),
    onConfigSelected: (StreamConfig) -> Unit
) {
    // Error 1: Ensure imports and/or Clean/Rebuild if this line still shows error
    val selectedMode by viewModel.selectedMode.collectAsState()

    // Add states for quality options if configurable here
    // val resolution by viewModel.resolution.collectAsState() ...

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Select Stream Mode", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Error 2 Fix: Use Triple instead of nested Pairs
        val options = listOf(
            // StreamMode.IMAGE_ONLY to "Image Only" to Icons.Default.PhotoCamera, // Not implemented
            Triple(StreamMode.AUDIO_ONLY, "Audio Only", Icons.Default.Mic),
            Triple(StreamMode.VIDEO_ONLY, "Video Only", Icons.Default.VideocamOff),
            Triple(StreamMode.VIDEO_AUDIO, "Video + Audio", Icons.Default.Videocam),
            Triple(StreamMode.VIDEO_AR, "Video + AR Data", Icons.Default.ViewInAr), // Need AR icon
            Triple(StreamMode.VIDEO_AUDIO_AR, "Video + Audio + AR", Icons.Default.ViewInAr) // Need better combined icon
        )

        // Error 2 & 3 Fix: Destructuring now works correctly with Triple
        options.forEach { (mode, label, icon) ->
            StreamOptionRow(
                text = label,   // Now correctly inferred as String
                icon = icon,    // Now correctly inferred as ImageVector
                selected = selectedMode == mode,
                onClick = { viewModel.selectMode(mode) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // TODO: Add Quality selection options here (Resolution, FPS, Bitrate)
        // Use Dropdowns, Sliders, or Radio buttons

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // Create config based on selection
                val config = StreamConfig(
                    mode = selectedMode,
                    // Get quality settings from ViewModel state if added
                    includeDepth = (selectedMode == StreamMode.VIDEO_AR || selectedMode == StreamMode.VIDEO_AUDIO_AR) // Enable depth for AR modes
                )
                onConfigSelected(config)
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text("Start Streaming", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StreamOptionRow(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface( // Use Surface for elevation and shape
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (selected) 4.dp else 1.dp, // Highlight selected
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null) // onClick handled by Row's selectable
        }
    }
}