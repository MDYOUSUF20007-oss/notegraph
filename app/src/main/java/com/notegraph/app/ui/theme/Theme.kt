package com.notegraph.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Supported luxury theme modes for NoteGraph.
 * Tri-Theme Matrix specified in NoteGraph Luxury UI Blueprint.
 */
enum class AppThemeMode(val displayName: String) {
    DEEP_PURPLE_AMOLED("Deep Purple AMOLED"),
    KINDLE_PARCHMENT("Kindle Parchment"),
    MATTE_STUDIO("Matte Studio")
}

// ─── Exact Color Constants ───────────────────────────────────────

object NoteGraphColors {
    // 1. Deep Purple AMOLED (True Black #000000, Royal Night #150A26, Electric Violet #A855F7, Pure White #FFFFFF)
    val DeepPurpleAmoledBackground = Color(0xFF000000) // True OLED black
    val DeepPurpleAmoledCardSurface = Color(0xFF150A26) // Royal Night
    val DeepPurpleAmoledCardBorder = Color(0xFF2A2238)
    val DeepPurpleAmoledAccent = Color(0xFFA855F7)     // Electric Violet
    val DeepPurpleAmoledAccentDark = Color(0xFF7C3AED) // Royal Purple
    val DeepPurpleAmoledText = Color(0xFFFFFFFF)       // Pure White

    // 2. Kindle Parchment (Warm Paper #F4F1EA, Soft Linen #EAE4D7, Charcoal Ink #2C2C2C, Warm Sepia #B45309)
    val KindleParchmentBackground = Color(0xFFF4F1EA)  // Warm Paper
    val KindleParchmentCardSurface = Color(0xFFEAE4D7) // Soft Linen
    val KindleParchmentCharcoalText = Color(0xFF2C2C2C)// Charcoal Ink
    val KindleParchmentSepiaDivider = Color(0xFFDCD6CD)// Ruled Sepia Lines
    val KindleParchmentAccent = Color(0xFFB45309)      // Warm Sepia

    // 3. Matte Studio (Slate Matte #1E2229, Graphite Box #262B35, Crisp Silver #E2E8F0, Studio Cyan #38BDF8)
    val MatteStudioBackground = Color(0xFF1E2229)      // Slate Matte
    val MatteStudioCardSurface = Color(0xFF262B35)     // Graphite Box
    val MatteStudioGridBorder = Color(0xFF3B4252)      // Hairline Grid
    val MatteStudioAccent = Color(0xFF38BDF8)          // Studio Cyan
    val MatteStudioText = Color(0xFFE2E8F0)            // Crisp Silver

    // Rainbow Gradient Constants (for Icon & Vanishing Pen)
    val RainbowCyan = Color(0xFF00FFFF)
    val RainbowViolet = Color(0xFF8B5CF6)
    val RainbowMagenta = Color(0xFFEC4899)
    val RainbowWarmOrange = Color(0xFFF59E0B)
}

/**
 * Global reactive theme manager with persistent SharedPreferences backing.
 */
object ThemeManager {
    private var prefs: SharedPreferences? = null

    var currentTheme by mutableStateOf(AppThemeMode.DEEP_PURPLE_AMOLED)

    fun init(context: Context) {
        val sp = context.getSharedPreferences("notegraph_prefs", Context.MODE_PRIVATE)
        prefs = sp
        val savedName = sp.getString("app_theme", AppThemeMode.DEEP_PURPLE_AMOLED.name)
        currentTheme = try {
            AppThemeMode.valueOf(savedName ?: AppThemeMode.DEEP_PURPLE_AMOLED.name)
        } catch (e: Exception) {
            AppThemeMode.DEEP_PURPLE_AMOLED
        }
    }

    fun setTheme(mode: AppThemeMode) {
        currentTheme = mode
        prefs?.edit()?.putString("app_theme", mode.name)?.apply()
    }

    fun cycleTheme(): AppThemeMode {
        val modes = AppThemeMode.values()
        val nextIdx = (modes.indexOf(currentTheme) + 1) % modes.size
        val nextMode = modes[nextIdx]
        setTheme(nextMode)
        return nextMode
    }

    // Backward-compatibility property
    var isAmoledMode: Boolean
        get() = currentTheme == AppThemeMode.DEEP_PURPLE_AMOLED
        set(value) {
            setTheme(if (value) AppThemeMode.DEEP_PURPLE_AMOLED else AppThemeMode.MATTE_STUDIO)
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
    primaryContainer = Color(0xFF2C1948),
    onPrimaryContainer = Color(0xFFE9D8FD),
    secondary = NoteGraphColors.DeepPurpleAmoledAccentDark,
    onSecondary = Color.White,
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
    surfaceVariant = NoteGraphColors.KindleParchmentCardSurface,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val isLightPaper = themeMode == AppThemeMode.KINDLE_PARCHMENT
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLightPaper
                isAppearanceLightNavigationBars = isLightPaper
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
