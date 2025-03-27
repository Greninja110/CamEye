package com.example.cameye.ui.viewmodel

import androidx.camera.core.Preview
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cameye.ar.ArTrackingState
import com.example.cameye.camera.CameraController
import com.example.cameye.camera.CameraManager
import com.example.cameye.camera.CameraState
import com.example.cameye.data.model.NetworkInfo
import com.example.cameye.di.IoDispatcher
import com.example.cameye.service.StreamingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraManager: CameraManager, // Injected CameraManager
    // Inject Service interaction layer if created, or handle service binding/interaction directly
    // For simplicity, directly accessing singleton states for now (assuming service updates them)
    private val savedStateHandle: SavedStateHandle, // For handling process death if needed
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    // Expose states collected from managers/service
    // These assume the underlying managers/service update their own StateFlows
    val cameraState: StateFlow<CameraState> = cameraManager.cameraState
    val arTrackingState: StateFlow<ArTrackingState> = cameraManager.arCoreSessionManager.trackingState // Access AR state via CameraManager -> ArCoreSessionManager

    // --- States potentially from StreamingService ---
    // This requires a mechanism to get the service instance or observe its state.
    // Options:
    // 1. Bind to the service in Activity/Fragment and pass ViewModel the binder.
    // 2. Use a Repository pattern that communicates with the service.
    // 3. (Simplest for now, but less robust) Assume service is running and access its state flows if exposed globally or via DI scope.
    // We'll simulate access here, assuming the flows are somehow available.
    // ** This needs proper implementation with Service Binding or a Repository **
    private val _mockIsStreaming = MutableStateFlow(true) // Placeholder
    val isStreaming: StateFlow<Boolean> = _mockIsStreaming // Replace with actual service state

    private val _mockNetworkInfo = MutableStateFlow(NetworkInfo()) // Placeholder
    val networkInfo: StateFlow<NetworkInfo> = _mockNetworkInfo // Replace with actual service state


    init {
        Timber.d("CameraViewModel initialized")
        // If using service binding, bind here or in init block
        // Example: observeServiceState()
    }

    // This is where you'd collect state from the actual service
    /*
    private fun observeServiceState() {
        viewModelScope.launch {
            // Assuming a way to get the service instance or its state flows
            val serviceFlows = ServiceRepository.getServiceFlows() // Example repository pattern
            serviceFlows.isStreaming.collect { _isStreaming.value = it }
            serviceFlows.networkInfo.collect { _networkInfo.value = it }
        }
    }
    */


    // --- Camera Control Actions ---

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        // This might trigger camera start if not already started by service
        // Or just ensure the preview is connected. Logic depends on service implementation.
        Timber.d("SurfaceProvider received in ViewModel. Forwarding to CameraManager if needed.")
        // cameraManager.connectPreview(surfaceProvider) // Example method needed in CameraManager
    }

    fun setZoomRatio(ratio: Float) {
        viewModelScope.launch(ioDispatcher) { // Use IO dispatcher for potentially blocking camera calls
            try {
                cameraManager.cameraController.setZoomRatio(ratio)
            } catch (e: Exception) {
                Timber.e(e, "Error setting zoom ratio from ViewModel")
                // Update UI state with error?
            }
        }
    }

    fun triggerFocus(x: Float, y: Float) {
        viewModelScope.launch(ioDispatcher) {
            try {
                cameraManager.cameraController.triggerFocus(x, y)
            } catch (e: Exception) {
                Timber.e(e, "Error triggering focus from ViewModel")
            }
        }
    }

    fun setExposure(ev: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                cameraManager.cameraController.setExposureCompensation(ev)
            } catch (e: Exception) {
                Timber.e(e, "Error setting exposure from ViewModel")
            }
        }
    }

    // --- Error Handling ---
    fun clearNetworkError() {
        // This should likely interact with the Service/Repository to clear the error state
        _mockNetworkInfo.update { it.copy(error = null) } // Placeholder update
    }
    fun clearCameraError() {
        // CameraManager might need a method to clear its error state
        // cameraManager.clearError() // Example method needed
        // For now, just clear the local copy if applicable, but source needs clearing
        // _cameraState.update { it.copy(error = null) } // Not ideal, state comes from manager
    }


    override fun onCleared() {
        Timber.d("CameraViewModel cleared")
        // Unbind from service if using binding
        super.onCleared()
    }
}
