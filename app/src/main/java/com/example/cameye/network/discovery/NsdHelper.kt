package com.example.cameye.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.cameye.network.rtsp.WifiHelper
import timber.log.Timber
import javax.inject.Inject

class NsdHelper @Inject constructor(
    context: Context,
    private val wifiHelper: WifiHelper,
    private val servicePort: Int // Port your RTSP server runs on
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serviceName: String? = null

    companion object {
        const val SERVICE_TYPE = "_rtsp._tcp." // Standard for RTSP
        const val SERVICE_NAME_PREFIX = "CamEyeARStreamer" // Unique name for your app
    }

    fun registerService() {
        unregisterService() // Ensure no previous registration exists

        val currentIp = wifiHelper.getWifiIpAddress()
        if (currentIp == null) {
            Timber.w("Cannot register NSD service, no WiFi IP address.")
            return
        }

        // Create a unique service name (e.g., CamEyeARStreamer_DeviceName)
        serviceName = "$SERVICE_NAME_PREFIX" // _${android.os.Build.MODEL.replace(" ", "_")}" - Model might be too long

        Timber.d("Registering NSD service: Name='$serviceName', Type='$SERVICE_TYPE', Port=$servicePort")


        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = SERVICE_TYPE
            this.port = servicePort
            // setHost() // NsdManager typically figures out the host IP, but you can set it
            // setAttribute("path", "/") // Optional: Add attributes like stream path
            setAttribute("ar", "true") // Indicate AR capability
            setAttribute("depth", "true") // Indicate depth capability
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Save the service name. Android may have changed it to resolve conflicts.
                serviceName = NsdServiceInfo.serviceName
                Timber.i("NSD Service Registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD Service registration failed: Error code $errorCode, Service: ${serviceInfo.serviceName}")
                // Handle failure (e.g., retry, notify user)
                cleanupListener()
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Timber.i("NSD Service Unregistered: ${arg0.serviceName}")
                cleanupListener()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD Service unregistration failed: Error code $errorCode, Service: ${serviceInfo.serviceName}")
                // Handle failure
                cleanupListener()
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Timber.e(e, "Exception during nsdManager.registerService")
            cleanupListener()
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                Timber.d("Unregistering NSD service...")
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Timber.e(e, "Exception during nsdManager.unregisterService")
            } finally {
                cleanupListener()
            }
        }
    }

    private fun cleanupListener() {
        registrationListener = null
        serviceName = null
        Timber.d("NSD RegistrationListener cleaned up.")
    }
}