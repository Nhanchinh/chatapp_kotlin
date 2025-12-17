# ============================================================================
# ProGuard/R8 Rules for ChatApp E2EE
# ============================================================================
# This file contains rules to protect the app from reverse engineering
# while ensuring all functionality works correctly.
# ============================================================================

# ============================================================================
# GENERAL SETTINGS
# ============================================================================

# Keep source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================================
# REMOVE DEBUG LOGS IN RELEASE BUILD
# ============================================================================

# Remove verbose and debug logs (keep warning/error for debugging crashes)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ============================================================================
# KOTLIN
# ============================================================================

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================================
# RETROFIT & OKHTTP
# ============================================================================

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================================
# MOSHI JSON
# ============================================================================

-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepnames @com.squareup.moshi.JsonClass class *
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>(...);
    <fields>;
}
-dontwarn com.squareup.moshi.**

# ============================================================================
# FIREBASE
# ============================================================================

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ============================================================================
# ZXING (QR CODE)
# ============================================================================

-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# ============================================================================
# ZEGO (VIDEO CALLING)
# ============================================================================

-keep class im.zego.** { *; }
-keep class com.zego.** { *; }
-dontwarn im.zego.**
-dontwarn com.zego.**

# ============================================================================
# ANDROID SECURITY CRYPTO (EncryptedSharedPreferences)
# ============================================================================

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**

# ============================================================================
# APP-SPECIFIC: API MODELS (MUST KEEP FOR JSON SERIALIZATION)
# ============================================================================

# Keep all API request/response models
-keep class com.example.chatapp.data.remote.model.** { *; }
-keepclassmembers class com.example.chatapp.data.remote.model.** { *; }

# ============================================================================
# APP-SPECIFIC: E2EE ENCRYPTION CLASSES (CRITICAL - DO NOT OBFUSCATE)
# ============================================================================

# Keep encryption-related classes for proper functionality
-keep class com.example.chatapp.data.encryption.** { *; }
-keepclassmembers class com.example.chatapp.data.encryption.** { *; }

# ============================================================================
# APP-SPECIFIC: VIEWMODELS
# ============================================================================

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.example.chatapp.viewmodel.** { *; }

# ============================================================================
# COMPOSE
# ============================================================================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================================================================
# COIL (IMAGE LOADING)
# ============================================================================

-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================================
# SUPPRESS WARNINGS
# ============================================================================

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.naming.**