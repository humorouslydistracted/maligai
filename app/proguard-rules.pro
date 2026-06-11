# Lifecycle / Activity Compose — required for LocalLifecycleOwner in release builds
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keepattributes *Annotation*

# Room
-keep class androidx.room.** { *; }

# ML Kit Digital Ink
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep entity data classes (reflection-free, but safe for Room)
-keep class com.maligai.app.** { *; }
