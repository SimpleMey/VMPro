package com.vmpro.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

// Brand seed colour. Every tone in the Material 3 palette is generated from this, so the whole
// app stays on-brand and identical on every device and Android version (no wallpaper theming).
private val BrandSeed = Color(0xFF2979FF)

@Composable
fun VmproTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    // A full Material 3 tonal palette generated from the brand seed (Google's HCT algorithm,
    // same one Material You uses) — but always from our colour, never the device wallpaper.
    val colorScheme = rememberDynamicColorScheme(
        seedColor = BrandSeed,
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Persists the user's dark/light choice. Null (unset) means "follow the system". */
object ThemePrefs {
    private const val PREFS = "vmpro_prefs"
    private const val KEY = "dark_mode"

    fun get(context: Context): Boolean? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains(KEY)) p.getBoolean(KEY, false) else null
    }

    fun set(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, dark).apply()
    }
}
