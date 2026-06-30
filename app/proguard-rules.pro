# --- OkHttp / Okio (ship their own rules; silence optional deps) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Aptabase analytics SDK ---
-keep class com.aptabase.** { *; }
-dontwarn com.aptabase.**

# --- AndroidX Lifecycle: keep the Compose CompositionLocal providers so R8 doesn't strip
# LocalLifecycleOwner (defensive; also fixed by lifecycle 2.8.3+). ---
-keep class androidx.lifecycle.compose.** { *; }

# Compose, AndroidX, and Kotlin coroutines ship their own consumer rules.
# App models are parsed manually (org.json), so no reflection keeps are needed.
