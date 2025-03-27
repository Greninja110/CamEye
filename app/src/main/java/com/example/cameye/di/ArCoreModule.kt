package com.example.cameye.di

import com.example.cameye.ar.ArCoreSessionManager // Assuming Singleton
import com.example.cameye.ar.ArFrameProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArCoreModule {

    // Provide ArCoreSessionManager as Singleton
    // @Singleton // Provided by default via @Inject constructor
    // @Provides
    // fun provideArCoreSessionManager(...): ArCoreSessionManager { ... }


    // ArFrameProcessor might not need to be Singleton if created on demand
    @Provides
    fun provideArFrameProcessor(): ArFrameProcessor {
        return ArFrameProcessor()
    }
}