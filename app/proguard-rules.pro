# --- OkHttp / Okio (ship their own rules; silence optional deps) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- PostHog analytics SDK (ships its own consumer rules; silence optional deps) ---
-dontwarn com.posthog.**

# --- Haze (frosted-glass) — defensive keep + silence ---
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# --- AndroidX Lifecycle: keep the Compose CompositionLocal providers so R8 doesn't strip
# LocalLifecycleOwner (defensive; also fixed by lifecycle 2.8.3+). ---
-keep class androidx.lifecycle.compose.** { *; }

# --- Shizuku: the user service is instantiated by name in a Shizuku-spawned process, and the
# AIDL stub crosses that process boundary, so both must survive R8. Shizuku's own classes ship
# consumer rules. ---
-keep class com.vmpro.app.util.ShizukuUserService { *; }
-keep interface com.vmpro.app.IShizukuUserService { *; }
-keep class com.vmpro.app.IShizukuUserService$Stub { *; }
-keep class com.vmpro.app.IShizukuUserService$Stub$* { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# Compose, AndroidX, and Kotlin coroutines ship their own consumer rules.
# App models are parsed manually (org.json), so no reflection keeps are needed.
