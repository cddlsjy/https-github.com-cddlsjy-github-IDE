# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions

# Keep Gson
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }
-keep class com.github.filemanager.data.model.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
