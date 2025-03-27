plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // Kapt for Hilt
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") // Apply serialization plugin
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.cameye"
    compileSdk = 35 // Target Android 14 (allows using API 33 features)

    defaultConfig {
        applicationId = "com.example.cameye"
        minSdk = 26 // ARCore requires minSdk 24, Depth API might need higher
        targetSdk = 35 // Target latest stable
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Enable minification
            isShrinkResources = true // Shrink resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig= true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Check compatibility with Kotlin version
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Required by ARCore library
    androidResources {
        noCompress += "**.tflite" // Prevent compression of TensorFlow Lite models used by ARCore
    }
}

// Allow Hilt kapt annotation processing
kapt {
    correctErrorTypes = true
}

dependencies {

    // Core Android & Kotlin
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.03.00")) // Use latest Compose BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation ("com.jakewharton.timber:timber:5.0.1")
    implementation ("androidx.lifecycle:lifecycle-service:2.6.2")// Use the latest version
    implementation ("androidx.compose.material:material-icons-extended:1.5.0")
    implementation ("com.google.android.material:material:1.11.0")


    // CameraX
    val cameraxVersion = "1.4.1" // Check for latest stable version
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-video:${cameraxVersion}") // If you want VideoCapture UseCase
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}") // For specific device effects

    // ARCore
    implementation("com.google.ar:core:1.48.0") // Check for latest ARCore SDK version

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.50") // Match plugin version
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // Hilt integration for Compose Navigation

    // Networking
    implementation("org.nanohttpd:nanohttpd:2.3.1") // Simple HTTP Server for web interface
    // ** IMPORTANT: Add a real RTSP Server library here **
    // Example placeholder (does not exist): implementation("com.example:rtsp-server-lib:1.0.0")
    // Or integrate libstreaming: https://github.com/fyhertz/libstreaming (requires more setup)

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // UI Helpers
    implementation("com.google.accompanist:accompanist-permissions:0.34.0") // Permissions handling

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}