package com.example.cameye.network.rtsp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class WifiHelper @Inject constructor(@ApplicationContext private val context: Context) {

    fun getWifiIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            // Modern way (preferred but needs WifiManager)
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connectionInfo = wifiManager?.connectionInfo
            val ipAddressInt = connectionInfo?.ipAddress ?: 0

            // Deprecated Formatter method still commonly used for this conversion
            @Suppress("DEPRECATION")
            val ipAddressString = Formatter.formatIpAddress(ipAddressInt)

            // Check for invalid address
            if (ipAddressString != null && ipAddressString != "0.0.0.0") {
                Timber.v("WiFi IP Address: $ipAddressString")
                return ipAddressString
            } else {
                Timber.w("Got invalid WiFi IP address: $ipAddressString")
                // Fallback or alternative methods might be needed in some cases
                return null
            }

        } else {
            Timber.w("Active network is not WiFi.")
            return null
        }
    }

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}