// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.1" apply false // Use your AS version
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Use compatible Kotlin version
    id("com.google.dagger.hilt.android") version "2.50" apply false // Hilt version
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false // Serializatio//
    // id("com.google.protobuf") version "0.9.4" apply false // If using Protobuf later

}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Hilt classpath (check latest version)
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.50")
        classpath ("com.android.tools.build:gradle:8.3.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}