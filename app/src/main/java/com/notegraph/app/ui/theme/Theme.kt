package com.notegraph.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Supported luxury theme modes for NoteGraph.
 */
enum class AppThemeMode(val displayName: String) {
    DEEP_PURPLE_AMOLED("Deep Purple AMOLED"),
    KINDLE_PARCHMENT("Kindle Parchment"),
    MATTE_STUDIO("Matte Studio")
}

// ─── Exact Color Constants ───────────────────────────────────────

object NoteGraphColors {
    // 1. Deep Purple AMOLED
    val DeepPurpleAmoledBackground = Color(0xFF000000) // True OLED black
    val DeepPurpleAmoledCardSurface = Color(0xFF121212)
    val DeepPurpleAmoledCardBorder = Color(0xFF2A2238)
    val DeepPurpleAmoledAccent = Color(0xFF8B5CF6)     // Electric Violet
    val DeepPurpleAmoledText = Color(0xFFE0E0E0)

    // 2. Kindle Parchment
    val KindleParchmentBackground = Color(0xFFF4F1EA)
    val KindleParchmentCharcoalText = Color(0xFF2C2C2C)
    val KindleParchmentSepiaDivider = Color(0xFFDCD6CD)
    val KindleParchmentAccent = Color(0xFF8B4513)

    // 3. Matte Studio
    val MatteStudioBackground = Color(0xFF1E2229)
    val MatteStudioCardSurface = Color(0xFF262B35)
    val MatteStudioGridBorder = Color(0xFF3B4252)
    val MatteStudioAccent = Color(0xFF81A1C1)
    val MatteStudioText = Color(0xFFD8DEE9)

    // Rainbow Gradient Constants (for Icon & Vanishing Pen)
    val RainbowCyan = Color(0xFF00FFFF)
    val RainbowViolet = Color(0xFF8B5CF6)
    val RainbowMagenta = Color(0xFFEC4899)
    val RainbowWarmOrange = Color(0xFFF59E0B)
}

/**
 * Global reactive theme manager.
 */
object ThemeManager {
    var currentTheme by mutableStateOf(AppThemeMode.DEEP_PURPLE_AMOLED)

    // Backward-compatibility property for amoled toggle
    var isAmoledMode: Boolean
        get() = currentTheme == AppThemeMode.DEEP_PURPLE_AMOLED
        set(value) {
            currentTheme = if (value) AppThemeMode.DEEP_PURPLE_AMOLED else AppThemeMode.MATTE_STUDIO
        }

    val cardBorderColor: Color
        get() = when (currentTheme) {
            AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledCardBorder
            AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentSepiaDivider
            AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioGridBorder
        }
}

// ─── Color Schemes ───────────────────────────────────────────────

val DeepPurpleAmoledColorScheme = darkColorScheme(
    primary = NoteGraphColors.DeepPurpleAmoledAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2D54),
    onPrimaryContainer = Color(0xFFE9D8FD),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color.Black,
    background = NoteGraphColors.DeepPurpleAmoledBackground,
    onBackground = NoteGraphColors.DeepPurpleAmoledText,
    surface = NoteGraphColors.DeepPurpleAmoledBackground,
    onSurface = NoteGraphColors.DeepPurpleAmoledText,
    surfaceVariant = NoteGraphColors.DeepPurpleAmoledCardSurface,
    onSurfaceVariant = NoteGraphColors.DeepPurpleAmoledText,
    outline = NoteGraphColors.DeepPurpleAmoledCardBorder
)

val KindleParchmentColorScheme = lightColorScheme(
    primary = NoteGraphColors.KindleParchmentAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADBCE),
    onPrimaryContainer = Color(0xFF3E1F07),
    secondary = Color(0xFFA0522D),
    onSecondary = Color.White,
    background = NoteGraphColors.KindleParchmentBackground,
    onBackground = NoteGraphColors.KindleParchmentCharcoalText,
    surface = NoteGraphColors.KindleParchmentBackground,
    onSurface = NoteGraphColors.KindleParchmentCharcoalText,
    surfaceVariant = Color(0xFFECE7DE),
    onSurfaceVariant = NoteGraphColors.KindleParchmentCharcoalText,
    outline = NoteGraphColors.KindleParchmentSepiaDivider
)

val MatteStudioColorScheme = darkColorScheme(
    primary = NoteGraphColors.MatteStudioAccent,
    onPrimary = Color(0xFF1E2229),
    primaryContainer = Color(0xFF2E3440),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF88C0D0),
    onSecondary = Color(0xFF1E2229),
    background = NoteGraphColors.MatteStudioBackground,
    onBackground = NoteGraphColors.MatteStudioText,
    surface = NoteGraphColors.MatteStudioBackground,
    onSurface = NoteGraphColors.MatteStudioText,
    surfaceVariant = NoteGraphColors.MatteStudioCardSurface,
    onSurfaceVariant = NoteGraphColors.MatteStudioText,
    outline = NoteGraphColors.MatteStudioGridBorder
)

@Composable
fun NoteGraphTheme(
    themeMode: AppThemeMode = ThemeManager.currentTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> DeepPurpleAmoledColorScheme
        AppThemeMode.KINDLE_PARCHMENT -> KindleParchmentColorScheme
        AppThemeMode.MATTE_STUDIO -> MatteStudioColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
