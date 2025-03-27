package com.example.cameye.camera

import android.util.Size
import androidx.camera.core.ImageAnalysis
import java.util.concurrent.Executor

// Simple config holder, can be expanded
data class ImageAnalysisConfig(
    val targetResolution: Size?, // Null for default resolution
    val executor: Executor,
    val analyzer: ImageAnalysis.Analyzer
)