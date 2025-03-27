package com.example.cameye.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

// Helper extension function to get Parcelable extras (handles deprecation)
// Duplicate from Service, keep only one, perhaps in a shared Util module if project grows
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

// Add other useful extensions here