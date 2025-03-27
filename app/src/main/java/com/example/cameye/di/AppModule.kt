package com.example.cameye.di

import android.content.Context
import com.example.cameye.util.ResourceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Singleton
    @Provides
    fun provideResourceProvider(@ApplicationContext context: Context): ResourceProvider {
        return ResourceProvider(context)
    }

    // Provide a singleton CoroutineScope for background tasks if needed globally
    @Singleton
    @Provides
    @AppCoroutineScope
    fun provideAppCoroutineScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope {
        // SupervisorJob ensures failure of one child doesn't cancel others
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

// Qualifier annotations for different dispatchers and scopes
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppCoroutineScope