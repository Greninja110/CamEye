package com.example.cameye.ui.preview

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER, // Adjust as needed
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(key1 = lifecycleOwner) { // Use lifecycleOwner as key
        Timber.d("CameraPreviewView DisposableEffect setup")
        previewView.scaleType = scaleType
        onSurfaceProviderReady(previewView.surfaceProvider)

        onDispose {
            Timber.d("CameraPreviewView DisposableEffect dispose")
            // SurfaceProvider might be released automatically by CameraX when lifecycle ends
            // No explicit release needed here usually.
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}