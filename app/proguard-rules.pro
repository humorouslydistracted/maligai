# Room
-keep class androidx.room.** { *; }

# ML Kit Digital Ink
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep entity data classes (reflection-free, but safe for Room)
-keep class com.maligai.app.** { *; }
