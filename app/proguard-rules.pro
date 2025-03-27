# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# by the Android plugin.
# You can edit this file to add your own optimization rules.

# Keep ARCore specific classes (Important!)
-keep class com.google.ar.** { *; }
-keep class org.tensorflow.** { *; } # If ARCore uses TF Lite internally

# Keep Kotlin specific things
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep Hilt generated classes
-keep class * extends androidx.hilt.lifecycle.ViewModelInjectFactory
-keep class * implements dagger.hilt.internal.GeneratedComponent

# Keep NanoHTTPD classes if used
-keep class org.nanohttpd.** { *; }

# Keep Serialization generated classes
-keep class **$$serializer { *; }
-keepnames class kotlinx.serialization.SerializersKt
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
    *** Companion;
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}

# Keep CameraX classes that might be accessed via reflection
-keep public class androidx.camera.core.impl.** { *; }
-keep public class androidx.camera.camera2.internal.** { *; }

# Your application classes (adjust if needed)
-keep public class com.example.cameye.** { *; }
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application

# Keep resource names for reflection if necessary (less common with Compose)
# -keepclassmembers class **.R$* {
#    public static <fields>;
# }