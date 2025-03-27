package com.example.cameye.di

import android.content.Context
import com.example.cameye.network.discovery.NsdHelper
import com.example.cameye.network.rtsp.DataMuxer
import com.example.cameye.network.rtsp.RtspServer
import com.example.cameye.network.serialization.ArDataSerializer
import com.example.cameye.network.web.WebServer
import com.example.cameye.network.rtsp.WifiHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Typically network components are singletons
object NetworkModule {

    @Singleton
    @Provides
    fun provideWifiHelper(@ApplicationContext context: Context): WifiHelper {
        return WifiHelper(context)
    }

    @Singleton
    @Provides
    fun provideNsdHelper(@ApplicationContext context: Context, wifiHelper: WifiHelper): NsdHelper {
        // Port needs to be defined, perhaps from config or constants
        val rtspPort = 8086 // Example RTSP Port
        return NsdHelper(context, wifiHelper, rtspPort)
    }

    @Singleton
    @Provides
    fun provideArDataSerializer(): ArDataSerializer {
        return ArDataSerializer() // Uses Kotlinx Serialization JSON by default
    }

    // --- Placeholder Providers ---
    // Replace these with providers for your actual RTSP library / implementation

    @Singleton
    @Provides
    fun provideDataMuxer(serializer: ArDataSerializer): DataMuxer {
        // The muxer needs the serializer to handle AR data
        return DataMuxer(serializer)
    }

    @Singleton
    @Provides
    fun provideRtspServer(
        @ApplicationContext context: Context,
        wifiHelper: WifiHelper,
        muxer: DataMuxer
    ): RtspServer {
        // The actual server would take configuration (port, etc.)
        val rtspPort = 8086 // Example RTSP Port - Should be consistent
        return RtspServer(context, wifiHelper, muxer, rtspPort)
    }

    @Singleton
    @Provides
    fun provideWebServer(
        @ApplicationContext context: Context,
        wifiHelper: WifiHelper
    ): WebServer {
        val webPort = 8080 // Example Web Port
        return WebServer(context, wifiHelper, webPort)
    }

    // --- End Placeholder Providers ---
}