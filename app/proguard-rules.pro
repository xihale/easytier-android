# Keep JNI bridge classes (called from Rust via JNI)
-keep class com.easytier.jni.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.easytier.android.data.**$$serializer { *; }
-keepclassmembers class com.easytier.android.data.** { *** Companion; }
-keepclasseswithmembers class com.easytier.android.data.** { kotlinx.serialization.KSerializer serializer(...); }
