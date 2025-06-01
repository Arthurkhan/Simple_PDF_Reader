# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep PDF rendering classes
-keep class android.graphics.pdf.** { *; }

# Keep app classes
-keep class com.simplepdf.reader.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material Components
-keep class com.google.android.material.** { *; }
