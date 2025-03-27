package com.example.cameye.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    // Inject SharedPreferences or DataStore repository
) : ViewModel() {
    // Load current settings into StateFlows
    // Provide functions to update settings
}