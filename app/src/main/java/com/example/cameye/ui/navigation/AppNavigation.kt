package com.example.cameye.ui.navigation

// Sealed class to define navigation routes
sealed class Screen(val route: String) {
    object Permissions : Screen("permissions")
    object StreamOptions : Screen("stream_options")
    object CameraPreview : Screen("camera_preview")
    object Settings : Screen("settings") // Optional settings screen
}