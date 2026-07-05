package com.vmpro.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2979FF)
private val BrandDark = Color(0xFF1565C0)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF0F1419),
    surface = Color(0xFF161B22),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1E2530),
    primaryContainer = Color(0xFF13315C),
    onPrimaryContainer = Color(0xFFD6E7FF),
)

// Light scheme with a grey backdrop so white cards/top bar/nav actually stand out.
private val LightColors = lightColorScheme(
    primary = BrandDark,
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF141A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A20),
    surfaceVariant = Color(0xFFE7ECF2),
    onSurfaceVariant = Color(0xFF48525C),
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF0A2A50),
    outline = Color(0xFFC3CBD4),
)

@Composable
fun VmproTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
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
