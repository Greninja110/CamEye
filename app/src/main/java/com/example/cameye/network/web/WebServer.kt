package com.example.cameye.network.web

import android.content.Context
import com.example.cameye.network.rtsp.WifiHelper
import fi.iki.elonen.NanoHTTPD // Import NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class WebServer @Inject constructor(
    private val context: Context, // May need context for assets later
    private val wifiHelper: WifiHelper,
    public val port: Int
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nanoHttpd: NanoHTTPD? = null
    private var serverJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Allow observing the RTSP URL (fetched separately)
    var rtspUrlProvider: () -> String? = { null }

    fun start() {
        if (nanoHttpd?.isAlive == true) {
            Timber.w("Web server already running.")
            return
        }
        val ipAddress = wifiHelper.getWifiIpAddress()
        if (ipAddress == null) {
            Timber.e("Cannot start Web server without WiFi IP.")
            _error.value = "No WiFi Connection"
            _isRunning.value = false
            return
        }


        serverJob = serverScope.launch {
            try {
                Timber.i("Starting Web Server on port $port")
                nanoHttpd = object : NanoHTTPD(port) {
                    override fun serve(session: IHTTPSession): Response {
                        Timber.d("Web Server received request: ${session.method} ${session.uri}")
                        // Simple response showing IP and RTSP URL
                        val rtspUrl = rtspUrlProvider() ?: "N/A"
                        val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <title>CamEye AR Streamer</title>
                                    <meta name="viewport" content="width=device-width, initial-scale=1">
                                    <style>
                                        body { font-family: sans-serif; padding: 20px; }
                                        .url { font-family: monospace; background-color: #eee; padding: 5px; border-radius: 3px; word-break: break-all; }
                                    </style>
                                </head>
                                <body>
                                    <h1>CamEye AR Streamer</h1>
                                    <p>Status: Running</p>
                                    <p>Device IP: <span class="url">${wifiHelper.getWifiIpAddress() ?: "N/A"}</span></p>
                                    <p>RTSP Stream URL: <span class="url">$rtspUrl</span></p>
                                    <p><i>(Note: This is a basic status page. Full web UI not implemented.)</i></p>
                                </body>
                                </html>
                            """.trimIndent()
                        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
                    }
                }.apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } // Start non-daemon

                _isRunning.value = true
                _error.value = null
                Timber.i("Web Server started successfully at http://$ipAddress:$port")

                // Keep coroutine alive while server is running
                while (isActive && nanoHttpd?.isAlive == true) {
                    kotlinx.coroutines.delay(1000)
                }

            } catch (e: Exception) {
                if (isActive) {
                    Timber.e(e, "Web Server error")
                    _error.value = e.localizedMessage ?: "Unknown web server error"
                }
            } finally {
                Timber.i("Web Server stopping...")
                nanoHttpd?.stop()
                nanoHttpd = null
                _isRunning.value = false
                Timber.i("Web Server stopped.")
            }
        }
    }

    fun stop() {
        Timber.d("Stopping Web Server...")
        // Stopping NanoHTTPD happens in the finally block when the coroutine is cancelled
        serverJob?.cancel("Web Server stopped externally")
        serverJob = null
    }

    fun getWebUrl(): String? {
        val ip = wifiHelper.getWifiIpAddress()
        return if (ip != null && _isRunning.value) {
            "http://$ip:$port"
        } else {
            null
        }
    }

    fun release() {
        Timber.d("Releasing Web Server resources...")
        stop()
        serverScope.cancel("WebServer released")
    }
}