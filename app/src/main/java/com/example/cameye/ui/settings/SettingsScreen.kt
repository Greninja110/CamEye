package com.example.cameye.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cameye.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    // Add navigation callback if needed
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            Text("Streaming Quality", style = MaterialTheme.typography.titleMedium)
            // Add controls for Resolution, FPS, Bitrate etc.
            // These would likely update SharedPreferences or a data store
            // which the StreamingService reads when starting.
            Spacer(modifier = Modifier.height(16.dp))
            Text("AR Settings", style = MaterialTheme.typography.titleMedium)
            // Add controls for enabling/disabling depth, etc.
            Spacer(modifier = Modifier.height(16.dp))
            Text("Settings screen placeholder.")
            Text("Implement controls to adjust resolution, bitrate, FPS, AR options.")

        }
    }
}