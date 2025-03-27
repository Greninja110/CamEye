package com.example.cameye

import android.app.Application
import com.example.cameye.BuildConfig // <-- Add this import
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber // Using Timber for logging (optional but recommended)

@HiltAndroidApp
class ARCameraStreamerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize logging (e.g., Timber)
        // Access BuildConfig ONLY after adding the import
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("Cameye created")
    }
}