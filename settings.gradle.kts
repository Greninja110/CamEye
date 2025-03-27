pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add JitPack if using libraries like libstreaming
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CamEye"
include(":app")