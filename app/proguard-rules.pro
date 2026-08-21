# Keep JNI bridge classes (called from Rust via JNI)
-keep class com.easytier.android.core.jni.** { *; }
-keep class com.easytier.jni.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.easytier.android.**$$serializer { *; }
-keepclassmembers class com.easytier.android.** { *** Companion; }
-keepclasseswithmembers class com.easytier.android.** { kotlinx.serialization.KSerializer serializer(...); }
