package com.example.cameye.ar

import android.content.Context
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Session

class DisplayRotationHelper(context: Context) {
    private val display: Display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    fun updateSessionIfNeeded(session: Session) {
        session.setDisplayGeometry(display.rotation, display.width, display.height)
    }

    fun onResume() {
        // Handle resume logic if needed
    }

    fun onPause() {
        // Handle pause logic if needed
    }
}
