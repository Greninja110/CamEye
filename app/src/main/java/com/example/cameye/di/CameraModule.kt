package com.example.cameye.di

import com.example.cameye.camera.CameraManager // Assuming CameraManager is Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    // Provide CameraManager as a Singleton if it manages global camera state
    // If its lifecycle is tied to something shorter, adjust the Component
    // @Singleton // Provided by default via @Inject constructor in CameraManager
    // @Provides
    // fun provideCameraManager(...): CameraManager { ... }
}