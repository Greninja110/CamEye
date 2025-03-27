package com.example.cameye

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cameye.data.model.StreamConfig
import com.example.cameye.permissions.PermissionManager
import com.example.cameye.service.StreamingService
import com.example.cameye.ui.navigation.Screen
import com.example.cameye.ui.preview.CameraPreviewScreen
import com.example.cameye.ui.startup.StreamOptionsScreen
import com.example.cameye.ui.theme.CamEyeTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity onCreate")
        setContent {
            CamEyeTheme { // Apply Material 3 theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Consider stopping the service if the activity is destroyed,
        // depending on desired background behavior.
        // stopStreamingService()
        Timber.d("MainActivity onDestroy")
    }

    private fun startStreamingService(config: StreamConfig) {
        Timber.d("Attempting to start StreamingService with config: $config")
        Intent(applicationContext, StreamingService::class.java).also { intent ->
            intent.action = StreamingService.ACTION_START_STREAMING
            intent.putExtra(StreamingService.EXTRA_STREAM_CONFIG, config)
            ContextCompat.startForegroundService(applicationContext, intent)
        }
    }

    private fun stopStreamingService() {
        Timber.d("Attempting to stop StreamingService")
        Intent(applicationContext, StreamingService::class.java).also { intent ->
            intent.action = StreamingService.ACTION_STOP_STREAMING
            // No need for startForegroundService when stopping
            applicationContext.startService(intent)
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        val context = LocalContext.current

        NavHost(navController = navController, startDestination = Screen.Permissions.route) {

            composable(Screen.Permissions.route) {
                PermissionScreen(
                    onPermissionsGranted = {
                        navController.navigate(Screen.StreamOptions.route) {
                            // Clear back stack so user can't go back to permissions
                            popUpTo(Screen.Permissions.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.StreamOptions.route) {
                StreamOptionsScreen(
                    onConfigSelected = { config ->
                        startStreamingService(config)
                        navController.navigate(Screen.CameraPreview.route) {
                            // Optional: Clear stream options from back stack
                            popUpTo(Screen.StreamOptions.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.CameraPreview.route) {
                // CameraPreviewScreen will likely observe state from a ViewModel
                // which gets data from the running StreamingService
                CameraPreviewScreen(
                    onStopStreaming = {
                        stopStreamingService()
                        // Navigate back to options or exit?
                        navController.navigate(Screen.StreamOptions.route) {
                            popUpTo(Screen.CameraPreview.route) { inclusive = true }
                        }
                    }
                )
            }
            // Add composable(Screen.Settings.route) { ... } if implementing settings
        }
    }


    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun PermissionScreen(onPermissionsGranted: () -> Unit) {
        var doNotShowRationale by remember { mutableStateOf(false) }

        // Define permissions based on potential StreamConfig options
        val permissions = remember {
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.RECORD_AUDIO, // Request audio even if not always used
                Manifest.permission.WAKE_LOCK
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Needed for foreground service notifications on Android 13+
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                // Foreground service permissions added in AndroidManifest directly for newer APIs
            }.toList()
        }

        val permissionState = rememberMultiplePermissionsState(permissions = permissions)

        LaunchedEffect(key1 = permissionState.allPermissionsGranted) {
            if (permissionState.allPermissionsGranted) {
                Timber.d("All permissions granted.")
                onPermissionsGranted()
            } else {
                Timber.w("Not all permissions granted. Status: ${permissionState.permissions.map { it.permission to it.status }}")
            }
        }

        PermissionManager(
            permissionState = permissionState,
            navigateToSettingsScreen = { /* TODO: Implement intent to app settings */ }
        ) {
            // This lambda is called when permissions need to be requested
            Timber.d("Requesting permissions...")
            permissionState.launchMultiplePermissionRequest()
        }
    }
}