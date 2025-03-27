package com.example.cameye.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionStatus
import timber.log.Timber

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionManager(
    permissionState: MultiplePermissionsState,
    navigateToSettingsScreen: () -> Unit, // Callback to navigate to app settings
    requestPermissions: () -> Unit // Callback to trigger permission request
) {
    // Trigger request on initial composition if needed
    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted && !permissionState.shouldShowRationale) {
            Timber.d("PermissionManager: Initial permission request.")
            requestPermissions()
        }
    }

    if (!permissionState.allPermissionsGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val revokedPermissions = permissionState.permissions.filter {
                it.status != PermissionStatus.Granted
            }

            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "This app requires the following permissions to function correctly:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            revokedPermissions.forEach { perm ->
                val permissionText = when (perm.permission) {
                    android.Manifest.permission.CAMERA -> "Camera: To capture video and AR."
                    android.Manifest.permission.RECORD_AUDIO -> "Microphone: To stream audio."
                    android.Manifest.permission.INTERNET -> "Internet: To stream data."
                    android.Manifest.permission.ACCESS_NETWORK_STATE -> "Network State: To check connectivity."
                    android.Manifest.permission.ACCESS_WIFI_STATE -> "WiFi State: To get local IP address."
                    android.Manifest.permission.WAKE_LOCK -> "Wake Lock: To keep streaming active."
                    android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications: To show streaming status."
                    else -> "${perm.permission.substringAfterLast('.')}: For core functionality."
                }
                Text("• $permissionText", modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (permissionState.shouldShowRationale) {
                Timber.d("PermissionManager: Should show rationale.")
                // Explain why the permissions are needed
                Text(
                    "Please grant these permissions to enable AR streaming.",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = requestPermissions) {
                    Text("Grant Permissions")
                }
            } else {
                Timber.d("PermissionManager: Rationale not shown (permissions denied permanently or first request).")
                // If rationale shouldn't be shown (denied permanently), guide to settings
                val permissionsPermanentlyDenied = revokedPermissions.any { permission ->
                    val deniedStatus = permission.status as? PermissionStatus.Denied
                    deniedStatus?.let { !it.shouldShowRationale } ?: false
                }

                if(permissionsPermanentlyDenied) {
                    Text(
                        "Some permissions were permanently denied. You need to enable them in the app settings.",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = navigateToSettingsScreen) {
                        Text("Open Settings")
                    }
                } else {
                    // This might be shown briefly during initial request before dialog appears
                    Text("Requesting necessary permissions...", modifier = Modifier.padding(bottom = 16.dp))
                    // Button might still be useful as a fallback if initial request fails silently
                    Button(onClick = requestPermissions) {
                        Text("Retry Request")
                    }
                }

            }
        }
    }
    // When all permissions are granted, this composable becomes empty,
    // allowing the LaunchedEffect in MainActivity to navigate away.
}