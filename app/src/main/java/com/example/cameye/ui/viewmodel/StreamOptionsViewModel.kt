package com.example.cameye.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.example.cameye.data.model.StreamConfig
import com.example.cameye.data.model.StreamMode

@HiltViewModel
class StreamOptionsViewModel @Inject constructor() : ViewModel() {

    // Default selection
    private val _selectedMode = MutableStateFlow(StreamMode.VIDEO_AUDIO_AR)
    val selectedMode: StateFlow<StreamMode> = _selectedMode.asStateFlow()

    fun selectMode(mode: StreamMode) {
        _selectedMode.value = mode
    }


    // Add functions to update quality settings
    // fun selectResolution(...) { ... }
}