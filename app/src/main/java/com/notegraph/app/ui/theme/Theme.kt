package com.notegraph.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Runtime-switchable interface architecture engine.
 */
enum class UiEngineMode(
    val id: String,
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    CLASSIC(
        id = "classic",
        displayName = "Classic UI",
        badgeLabel = "CLASSIC",
        description = "Legacy glowing Obsidian cards, gradient flares & framed studio surfaces"
    ),
    BESPOKE_LUXURY(
        id = "bespoke_luxury",
        displayName = "Bespoke Luxury (Default)",
        badgeLabel = "LUXURY",
        description = "Strict flat editorial surfaces, 1px hairline borders, blueprint graph & edge-to-edge canvas"
    );

    companion object {
        fun fromIdOrDefault(raw: String?): UiEngineMode =
            entries.firstOrNull { it.id == raw || it.name == raw } ?: BESPOKE_LUXURY
    }
}

val LocalUiEngine = compositionLocalOf { UiEngineMode.BESPOKE_LUXURY }

/**
 * Curated luxury surface tokens with zero generic gradient fills.
 */
data class LuxuryPalette(
    val canvasBackground: Color,
    val elevatedSurface: Color,
    val recessedSurface: Color,
    val borderHairline: Color,
    val dividerRule: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val footerText: Color
)

object LuxuryTokens {
    val SignatureAccent = Color(0xFF8B5CF6)
    val AmoledBorder = Color(0xFF262626)

    val AmoledBlack = LuxuryPalette(
        canvasBackground = Color(0xFF000000),
        elevatedSurface = Color(0xFF121212),
        recessedSurface = Color(0xFF0A0A0A),
        borderHairline = AmoledBorder,
        dividerRule = Color(0xFF222222),
        accent = SignatureAccent,
        onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A),
        footerText = Color(0xFF8E8E93)
    )

    val KindleParchment = LuxuryPalette(
        canvasBackground = Color(0xFFF4F1EA),
        elevatedSurface = Color(0xFFFAF7F0),
        recessedSurface = Color(0xFFEBE5D8),
        borderHairline = Color(0xFFD5CEC1),
        dividerRule = Color(0xFFDCD5C9),
        accent = SignatureAccent,
        onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF1E1B18),
        textSecondary = Color(0xFF4E483F),
        textMuted = Color(0xFF787166),
        footerText = Color(0xFF696258)
    )

    val MatteMinimalist = LuxuryPalette(
        canvasBackground = Color(0xFF1E2229),
        elevatedSurface = Color(0xFF252A33),
        recessedSurface = Color(0xFF181B21),
        borderHairline = Color(0xFF323844),
        dividerRule = Color(0xFF2D333E),
        accent = SignatureAccent,
        onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFE6EDF5),
        textSecondary = Color(0xFFA0AAB8),
        textMuted = Color(0xFF6F7A89),
        footerText = Color(0xFF8C96A6)
    )

    fun forMode(mode: AppThemeMode): LuxuryPalette = when (mode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> AmoledBlack
        AppThemeMode.KINDLE_PARCHMENT -> KindleParchment
        AppThemeMode.MATTE_STUDIO -> MatteMinimalist
    }
}

/**
 * Supported minimalist theme modes for NoteGraph.
 */
enum class AppThemeMode(
    val displayName: String,
    val shortLabel: String,
    val luxuryName: String,
    val luxurySubtitle: String
) {
    DEEP_PURPLE_AMOLED("AMOLED", "AMOLED", "AMOLED Black", "#000000 · #121212"),
    KINDLE_PARCHMENT("Kindle Paper", "Kindle", "Kindle Parchment", "#F4F1EA Paper"),
    MATTE_STUDIO("Matte / Mac", "Matte", "Matte Minimalist", "#1E2229 Slate");

    fun labelFor(isLuxury: Boolean): String = if (isLuxury) luxuryName else displayName
    fun shortLabelFor(isLuxury: Boolean): String = when {
        !isLuxury -> shortLabel
        this == KINDLE_PARCHMENT -> "Parchment"
        else -> shortLabel
    }
}

/**
 * A choosable gradient palette scoped to an [AppThemeMode].
 */
data class ThemeGradientPreset(
    val id: String,
    val name: String,
    val accentStart: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val backgroundColors: List<Color>,
    val cardSurface: Color,
    val cardTint: Color,
    val borderColor: Color
)

// ─── Exact Color & Legacy Constants (Preserved for Compatibility) ───

object NoteGraphColors {
    // 1. Deep Purple AMOLED
    val DeepPurpleAmoledBackground = Color(0xFF000000)
    val DeepPurpleAmoledCardSurface = Color(0xFF150A26)
    val DeepPurpleAmoledCardBorder = Color(0xFF2A2238)
    val DeepPurpleAmoledAccent = Color(0xFFA855F7)
    val DeepPurpleAmoledAccentDark = Color(0xFF7C3AED)
    val DeepPurpleAmoledText = Color(0xFFFFFFFF)

    // 2. Kindle Parchment
    val KindleParchmentBackground = Color(0xFFF4F1EA)
    val KindleParchmentCardSurface = Color(0xFFEAE4D7)
    val KindleParchmentCharcoalText = Color(0xFF22201B)
    val KindleParchmentSepiaDivider = Color(0xFFDCD6CD)
    val KindleParchmentAccent = Color(0xFFB45309)

    // 3. Matte Studio
    val MatteStudioBackground = Color(0xFF1E2229)
    val MatteStudioCardSurface = Color(0xFF262B35)
    val MatteStudioGridBorder = Color(0xFF3B4252)
    val MatteStudioAccent = Color(0xFF38BDF8)
    val MatteStudioText = Color(0xFFE2E8F0)

    // Vanishing Laser Gradient Constants
    val RainbowCyan = Color(0xFF00FFFF)
    val RainbowViolet = Color(0xFF8B5CF6)
    val RainbowMagenta = Color(0xFFEC4899)
    val RainbowWarmOrange = Color(0xFFF59E0B)
}

object NoteGraphGradients {
    val amoledPresets: List<ThemeGradientPreset> = listOf(
        ThemeGradientPreset(
            id = "obsidian_violet",
            name = "Obsidian Violet",
            accentStart = Color(0xFF8B5CF6),
            accentEnd = Color(0xFFA855F7),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFF140726), Color(0xFF05010A), Color(0xFF000000)),
            cardSurface = Color(0xFF0C0C0E),
            cardTint = Color(0xFF1A1A1E),
            borderColor = Color(0xFF2B1C45)
        ),
        ThemeGradientPreset(
            id = "obsidian_gold",
            name = "Obsidian Gold",
            accentStart = Color(0xFFF59E0B),
            accentEnd = Color(0xFFD97706),
            onAccent = Color(0xFF140D02),
            backgroundColors = listOf(Color(0xFF191206), Color(0xFF070502), Color(0xFF000000)),
            cardSurface = Color(0xFF0C0C0E),
            cardTint = Color(0xFF1A1A1E),
            borderColor = Color(0xFF423112)
        ),
        ThemeGradientPreset(
            id = "aurora_cyan",
            name = "Aurora Cyan",
            accentStart = Color(0xFF0EA5E9),
            accentEnd = Color(0xFF6366F1),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFF051824), Color(0xFF02070E), Color(0xFF000000)),
            cardSurface = Color(0xFF07111A),
            cardTint = Color(0xFF0D2032),
            borderColor = Color(0xFF17354D)
        ),
        ThemeGradientPreset(
            id = "crimson_velvet",
            name = "Crimson Velvet",
            accentStart = Color(0xFFF43F5E),
            accentEnd = Color(0xFFA855F7),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFF210712), Color(0xFF080206), Color(0xFF000000)),
            cardSurface = Color(0xFF14080E),
            cardTint = Color(0xFF260D19),
            borderColor = Color(0xFF3E182B)
        ),
        ThemeGradientPreset(
            id = "emerald_flux",
            name = "Emerald Flux",
            accentStart = Color(0xFF10B981),
            accentEnd = Color(0xFF06B6D4),
            onAccent = Color(0xFF03140F),
            backgroundColors = listOf(Color(0xFF041C16), Color(0xFF010806), Color(0xFF000000)),
            cardSurface = Color(0xFF061310),
            cardTint = Color(0xFF0B241E),
            borderColor = Color(0xFF143A30)
        ),
        ThemeGradientPreset(
            id = "solar_ember",
            name = "Solar Ember",
            accentStart = Color(0xFFF59E0B),
            accentEnd = Color(0xFFEC4899),
            onAccent = Color(0xFF1C0C02),
            backgroundColors = listOf(Color(0xFF1F0E04), Color(0xFF080304), Color(0xFF000000)),
            cardSurface = Color(0xFF130B06),
            cardTint = Color(0xFF24140B),
            borderColor = Color(0xFF3B2214)
        ),
        ThemeGradientPreset(
            id = "stealth_mono",
            name = "OLED Stealth",
            accentStart = Color(0xFFCBD5E1),
            accentEnd = Color(0xFF94A3B8),
            onAccent = Color(0xFF0B0D11),
            backgroundColors = listOf(Color(0xFF0B0D11), Color(0xFF020203), Color(0xFF000000)),
            cardSurface = Color(0xFF0D0E12),
            cardTint = Color(0xFF161920),
            borderColor = Color(0xFF252A34)
        )
    )

    val kindlePresets: List<ThemeGradientPreset> = listOf(
        ThemeGradientPreset(
            id = "warm_sepia",
            name = "Warm Sepia",
            accentStart = Color(0xFFB45309),
            accentEnd = Color(0xFFD97706),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFFF7F4EC), Color(0xFFF3EEE2), Color(0xFFEBE4D4)),
            cardSurface = Color(0xFFFCF9F2),
            cardTint = Color(0xFFF3EADC),
            borderColor = Color(0xFFD9CFC0)
        ),
        ThemeGradientPreset(
            id = "sage_folio",
            name = "Sage Folio",
            accentStart = Color(0xFF2D6A4F),
            accentEnd = Color(0xFF528C5C),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFFF3F6EF), Color(0xFFEDF2E7), Color(0xFFE3EAD9)),
            cardSurface = Color(0xFFFAFDF7),
            cardTint = Color(0xFFE8EFE2),
            borderColor = Color(0xFFCCD7C5)
        ),
        ThemeGradientPreset(
            id = "editorial_rose",
            name = "Dusty Rose",
            accentStart = Color(0xFF9D4452),
            accentEnd = Color(0xFFBE6A73),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFFF8F2F1), Color(0xFFF2E9E7), Color(0xFFE8DCDA)),
            cardSurface = Color(0xFFFDF9F8),
            cardTint = Color(0xFFF1E3E2),
            borderColor = Color(0xFFDCCAC8)
        ),
        ThemeGradientPreset(
            id = "oxford_ink",
            name = "Oxford Ink",
            accentStart = Color(0xFF2B4C7E),
            accentEnd = Color(0xFF476A9E),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFFF2F5F9), Color(0xFFEBEFF6), Color(0xFFE0E7F1)),
            cardSurface = Color(0xFFFAFBFD),
            cardTint = Color(0xFFE4EBF4),
            borderColor = Color(0xFFC9D3E0)
        ),
        ThemeGradientPreset(
            id = "classic_paper",
            name = "Classic Ink",
            accentStart = Color(0xFF44403C),
            accentEnd = Color(0xFF78716C),
            onAccent = Color.White,
            backgroundColors = listOf(Color(0xFFF4F1EA), Color(0xFFF4F1EA), Color(0xFFEEE9DF)),
            cardSurface = Color(0xFFFAF7F1),
            cardTint = Color(0xFFEDE8DE),
            borderColor = Color(0xFFDCD6CD)
        )
    )

    val mattePresets: List<ThemeGradientPreset> = listOf(
        ThemeGradientPreset(
            id = "studio_cyan",
            name = "Studio Cyan",
            accentStart = Color(0xFF38BDF8),
            accentEnd = Color(0xFF6366F1),
            onAccent = Color(0xFF0A1320),
            backgroundColors = listOf(Color(0xFF1A2230), Color(0xFF1E2229), Color(0xFF15181F)),
            cardSurface = Color(0xFF232833),
            cardTint = Color(0xFF2B3446),
            borderColor = Color(0xFF343C4C)
        ),
        ThemeGradientPreset(
            id = "nord_indigo",
            name = "Nord Indigo",
            accentStart = Color(0xFF818CF8),
            accentEnd = Color(0xFFA78BFA),
            onAccent = Color(0xFF0E1022),
            backgroundColors = listOf(Color(0xFF1E2030), Color(0xFF1D202A), Color(0xFF161820)),
            cardSurface = Color(0xFF242736),
            cardTint = Color(0xFF2E3249),
            borderColor = Color(0xFF393E54)
        ),
        ThemeGradientPreset(
            id = "titanium_sage",
            name = "Titanium Sage",
            accentStart = Color(0xFF34D399),
            accentEnd = Color(0xFF2DD4BF),
            onAccent = Color(0xFF061B16),
            backgroundColors = listOf(Color(0xFF182423), Color(0xFF1C2326), Color(0xFF151A1C)),
            cardSurface = Color(0xFF21292B),
            cardTint = Color(0xFF273936),
            borderColor = Color(0xFF334342)
        ),
        ThemeGradientPreset(
            id = "terracotta_slate",
            name = "Warm Copper",
            accentStart = Color(0xFFFB923C),
            accentEnd = Color(0xFFEC4899),
            onAccent = Color(0xFF1D0D07),
            backgroundColors = listOf(Color(0xFF261F22), Color(0xFF202127), Color(0xFF17181D)),
            cardSurface = Color(0xFF27262E),
            cardTint = Color(0xFF382C33),
            borderColor = Color(0xFF423A44)
        ),
        ThemeGradientPreset(
            id = "anodized_silver",
            name = "Anodized Gray",
            accentStart = Color(0xFFCBD5E1),
            accentEnd = Color(0xFF94A3B8),
            onAccent = Color(0xFF101318),
            backgroundColors = listOf(Color(0xFF22252C), Color(0xFF1E2229), Color(0xFF171A20)),
            cardSurface = Color(0xFF252932),
            cardTint = Color(0xFF2F3440),
            borderColor = Color(0xFF3B414F)
        )
    )

    fun presetsFor(mode: AppThemeMode): List<ThemeGradientPreset> = when (mode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> amoledPresets
        AppThemeMode.KINDLE_PARCHMENT -> kindlePresets
        AppThemeMode.MATTE_STUDIO -> mattePresets
    }

    fun defaultIdFor(mode: AppThemeMode): String = presetsFor(mode).first().id
}

/**
 * Global reactive theme & gradient manager backed by SharedPreferences.
 */
object ThemeManager {
    private var prefs: SharedPreferences? = null

    private var _uiEngine by mutableStateOf(UiEngineMode.BESPOKE_LUXURY)
    val uiEngine: UiEngineMode
        get() = _uiEngine

    val isLuxuryUi: Boolean
        get() = _uiEngine == UiEngineMode.BESPOKE_LUXURY

    var currentTheme by mutableStateOf(AppThemeMode.DEEP_PURPLE_AMOLED)
        private set

    var gradientsEnabled by mutableStateOf(true)
        private set

    var amoledGradientId by mutableStateOf(NoteGraphGradients.amoledPresets.first().id)
        private set

    var kindleGradientId by mutableStateOf(NoteGraphGradients.kindlePresets.first().id)
        private set

    var matteGradientId by mutableStateOf(NoteGraphGradients.mattePresets.first().id)
        private set

    fun init(context: Context) {
        val sp = context.getSharedPreferences("notegraph_prefs", Context.MODE_PRIVATE)
        prefs = sp
        _uiEngine = UiEngineMode.fromIdOrDefault(sp.getString("ui_engine", UiEngineMode.BESPOKE_LUXURY.id))
        val savedName = sp.getString("app_theme", AppThemeMode.DEEP_PURPLE_AMOLED.name)
        currentTheme = try {
            AppThemeMode.valueOf(savedName ?: AppThemeMode.DEEP_PURPLE_AMOLED.name)
        } catch (_: Exception) {
            AppThemeMode.DEEP_PURPLE_AMOLED
        }
        gradientsEnabled = sp.getBoolean("gradients_enabled", true)
        amoledGradientId = sp.getString("grad_DEEP_PURPLE_AMOLED", NoteGraphGradients.defaultIdFor(AppThemeMode.DEEP_PURPLE_AMOLED))
            ?: NoteGraphGradients.defaultIdFor(AppThemeMode.DEEP_PURPLE_AMOLED)
        kindleGradientId = sp.getString("grad_KINDLE_PARCHMENT", NoteGraphGradients.defaultIdFor(AppThemeMode.KINDLE_PARCHMENT))
            ?: NoteGraphGradients.defaultIdFor(AppThemeMode.KINDLE_PARCHMENT)
        matteGradientId = sp.getString("grad_MATTE_STUDIO", NoteGraphGradients.defaultIdFor(AppThemeMode.MATTE_STUDIO))
            ?: NoteGraphGradients.defaultIdFor(AppThemeMode.MATTE_STUDIO)
    }

    fun setUiEngine(engine: UiEngineMode) {
        _uiEngine = engine
        prefs?.edit()?.putString("ui_engine", engine.id)?.apply()
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

    fun presetsFor(mode: AppThemeMode = currentTheme): List<ThemeGradientPreset> =
        NoteGraphGradients.presetsFor(mode)

    fun selectedGradientIdFor(mode: AppThemeMode = currentTheme): String = when (mode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> amoledGradientId
        AppThemeMode.KINDLE_PARCHMENT -> kindleGradientId
        AppThemeMode.MATTE_STUDIO -> matteGradientId
    }

    fun presetFor(mode: AppThemeMode = currentTheme): ThemeGradientPreset {
        val list = presetsFor(mode)
        val id = selectedGradientIdFor(mode)
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    val luxuryTokens: LuxuryPalette
        get() = LuxuryTokens.forMode(currentTheme)

    fun luxuryPresetFor(mode: AppThemeMode = currentTheme): ThemeGradientPreset {
        val p = LuxuryTokens.forMode(mode)
        return ThemeGradientPreset(
            id = "luxury_${mode.name.lowercase()}",
            name = mode.luxuryName,
            accentStart = p.accent,
            accentEnd = p.accent,
            onAccent = p.onAccent,
            backgroundColors = listOf(p.canvasBackground, p.canvasBackground, p.canvasBackground),
            cardSurface = p.elevatedSurface,
            cardTint = p.elevatedSurface,
            borderColor = p.borderHairline
        )
    }

    val currentGradient: ThemeGradientPreset
        get() = if (isLuxuryUi) luxuryPresetFor(currentTheme) else presetFor(currentTheme)

    fun setGradient(mode: AppThemeMode, presetId: String) {
        val valid = presetsFor(mode).firstOrNull { it.id == presetId }?.id ?: return
        when (mode) {
            AppThemeMode.DEEP_PURPLE_AMOLED -> amoledGradientId = valid
            AppThemeMode.KINDLE_PARCHMENT -> kindleGradientId = valid
            AppThemeMode.MATTE_STUDIO -> matteGradientId = valid
        }
        prefs?.edit()?.putString("grad_${mode.name}", valid)?.apply()
    }

    fun setGradientsEnabledState(enabled: Boolean) {
        gradientsEnabled = enabled
        prefs?.edit()?.putBoolean("gradients_enabled", enabled)?.apply()
    }

    fun resetVisualsToDefault() {
        amoledGradientId = NoteGraphGradients.defaultIdFor(AppThemeMode.DEEP_PURPLE_AMOLED)
        kindleGradientId = NoteGraphGradients.defaultIdFor(AppThemeMode.KINDLE_PARCHMENT)
        matteGradientId = NoteGraphGradients.defaultIdFor(AppThemeMode.MATTE_STUDIO)
        gradientsEnabled = true
        prefs?.edit()
            ?.putString("grad_DEEP_PURPLE_AMOLED", amoledGradientId)
            ?.putString("grad_KINDLE_PARCHMENT", kindleGradientId)
            ?.putString("grad_MATTE_STUDIO", matteGradientId)
            ?.putBoolean("gradients_enabled", true)
            ?.apply()
    }

    val flatBackgroundColor: Color
        get() = if (isLuxuryUi) {
            luxuryTokens.canvasBackground
        } else when (currentTheme) {
            AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledBackground
            AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentBackground
            AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioBackground
        }

    val activeScreenBrush: Brush
        get() {
            if (isLuxuryUi) {
                return SolidColor(luxuryTokens.canvasBackground)
            }
            val g = currentGradient
            return if (gradientsEnabled) {
                Brush.verticalGradient(g.backgroundColors)
            } else {
                SolidColor(flatBackgroundColor)
            }
        }

    val activeAccentBrush: Brush
        get() {
            if (isLuxuryUi) {
                return SolidColor(luxuryTokens.accent)
            }
            val g = currentGradient
            return if (gradientsEnabled) {
                Brush.linearGradient(listOf(g.accentStart, g.accentEnd))
            } else {
                SolidColor(g.accentStart)
            }
        }

    fun activeCardBrush(highlighted: Boolean = false): Brush {
        if (isLuxuryUi) {
            val t = luxuryTokens
            return SolidColor(if (highlighted) t.recessedSurface else t.elevatedSurface)
        }
        val g = currentGradient
        return if (!gradientsEnabled) {
            val solid = if (highlighted) g.cardTint else g.cardSurface
            SolidColor(solid)
        } else if (highlighted) {
            Brush.linearGradient(
                listOf(
                    g.cardTint,
                    g.cardSurface
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    g.cardTint.copy(alpha = 0.65f),
                    g.cardSurface
                )
            )
        }
    }

    // Backward-compatibility property
    var isAmoledMode: Boolean
        get() = currentTheme == AppThemeMode.DEEP_PURPLE_AMOLED
        set(value) {
            setTheme(if (value) AppThemeMode.DEEP_PURPLE_AMOLED else AppThemeMode.MATTE_STUDIO)
        }

    val cardBorderColor: Color
        get() = if (isLuxuryUi) {
            luxuryTokens.borderHairline
        } else {
            currentGradient.borderColor.copy(alpha = 0.72f)
        }
}

// ─── Dynamic Color Schemes ───────────────────────────────────────

private fun buildLuxuryColorScheme(mode: AppThemeMode): ColorScheme {
    val t = LuxuryTokens.forMode(mode)
    return if (mode == AppThemeMode.KINDLE_PARCHMENT) {
        lightColorScheme(
            primary = t.accent,
            onPrimary = t.onAccent,
            primaryContainer = t.accent.copy(alpha = 0.12f),
            onPrimaryContainer = t.textPrimary,
            secondary = t.accent,
            onSecondary = t.onAccent,
            secondaryContainer = t.recessedSurface,
            onSecondaryContainer = t.textSecondary,
            background = t.canvasBackground,
            onBackground = t.textPrimary,
            surface = t.canvasBackground,
            onSurface = t.textPrimary,
            surfaceVariant = t.elevatedSurface,
            onSurfaceVariant = t.textSecondary,
            outline = t.borderHairline,
            outlineVariant = t.dividerRule
        )
    } else {
        darkColorScheme(
            primary = t.accent,
            onPrimary = t.onAccent,
            primaryContainer = t.accent.copy(alpha = 0.16f),
            onPrimaryContainer = t.textPrimary,
            secondary = t.accent,
            onSecondary = t.onAccent,
            secondaryContainer = t.recessedSurface,
            onSecondaryContainer = t.textSecondary,
            background = t.canvasBackground,
            onBackground = t.textPrimary,
            surface = t.canvasBackground,
            onSurface = t.textPrimary,
            surfaceVariant = t.elevatedSurface,
            onSurfaceVariant = t.textSecondary,
            outline = t.borderHairline,
            outlineVariant = t.dividerRule
        )
    }
}

private fun buildColorScheme(
    mode: AppThemeMode,
    preset: ThemeGradientPreset,
    gradientsOn: Boolean
): ColorScheme {
    val bgTop = if (gradientsOn) preset.backgroundColors.last() else when (mode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledBackground
        AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentBackground
        AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioBackground
    }

    return when (mode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> darkColorScheme(
            primary = preset.accentStart,
            onPrimary = preset.onAccent,
            primaryContainer = preset.cardTint,
            onPrimaryContainer = Color(0xFFF3ECFF),
            secondary = preset.accentEnd,
            onSecondary = preset.onAccent,
            secondaryContainer = preset.cardSurface,
            onSecondaryContainer = Color(0xFFE5E7EB),
            background = Color.Black,
            onBackground = Color(0xFFF8FAFC),
            surface = Color.Black,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = preset.cardSurface,
            onSurfaceVariant = Color(0xFFD4D4D8),
            outline = preset.borderColor,
            outlineVariant = preset.borderColor.copy(alpha = 0.55f)
        )

        AppThemeMode.KINDLE_PARCHMENT -> lightColorScheme(
            primary = preset.accentStart,
            onPrimary = preset.onAccent,
            primaryContainer = preset.cardTint,
            onPrimaryContainer = Color(0xFF22201B),
            secondary = preset.accentEnd,
            onSecondary = preset.onAccent,
            secondaryContainer = preset.cardTint,
            onSecondaryContainer = Color(0xFF3E3830),
            background = bgTop,
            onBackground = Color(0xFF22201B),
            surface = bgTop,
            onSurface = Color(0xFF22201B),
            surfaceVariant = preset.cardSurface,
            onSurfaceVariant = Color(0xFF4A4338),
            outline = preset.borderColor,
            outlineVariant = preset.borderColor.copy(alpha = 0.55f)
        )

        AppThemeMode.MATTE_STUDIO -> darkColorScheme(
            primary = preset.accentStart,
            onPrimary = preset.onAccent,
            primaryContainer = preset.cardTint,
            onPrimaryContainer = Color(0xFFEFF6FF),
            secondary = preset.accentEnd,
            onSecondary = preset.onAccent,
            secondaryContainer = preset.cardSurface,
            onSecondaryContainer = Color(0xFFE2E8F0),
            background = bgTop,
            onBackground = Color(0xFFE2E8F0),
            surface = bgTop,
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = preset.cardSurface,
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = preset.borderColor,
            outlineVariant = preset.borderColor.copy(alpha = 0.55f)
        )
    }
}

val DeepPurpleAmoledColorScheme: ColorScheme
    get() = buildColorScheme(
        AppThemeMode.DEEP_PURPLE_AMOLED,
        NoteGraphGradients.amoledPresets.first(),
        true
    )

val KindleParchmentColorScheme: ColorScheme
    get() = buildColorScheme(
        AppThemeMode.KINDLE_PARCHMENT,
        NoteGraphGradients.kindlePresets.first(),
        true
    )

val MatteStudioColorScheme: ColorScheme
    get() = buildColorScheme(
        AppThemeMode.MATTE_STUDIO,
        NoteGraphGradients.mattePresets.first(),
        true
    )

private val MinimalistShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LuxuryShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val MinimalistTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp
    )
)

private val LuxuryTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.45.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.55.sp
    )
)

@Composable
fun NoteGraphTheme(
    themeMode: AppThemeMode = ThemeManager.currentTheme,
    uiEngine: UiEngineMode = ThemeManager.uiEngine,
    content: @Composable () -> Unit
) {
    val isLuxury = uiEngine == UiEngineMode.BESPOKE_LUXURY
    val activePreset = ThemeManager.presetFor(themeMode)
    val gradientsOn = !isLuxury && ThemeManager.gradientsEnabled
    val colorScheme = if (isLuxury) {
        buildLuxuryColorScheme(themeMode)
    } else {
        buildColorScheme(themeMode, activePreset, gradientsOn)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val isLightPaper = themeMode == AppThemeMode.KINDLE_PARCHMENT
            val luxuryBg = LuxuryTokens.forMode(themeMode).canvasBackground
            val topBarCol = when {
                isLuxury -> luxuryBg
                gradientsOn -> activePreset.backgroundColors.first()
                else -> ThemeManager.flatBackgroundColor
            }
            val navBarCol = when {
                isLuxury -> luxuryBg
                gradientsOn -> activePreset.backgroundColors.last()
                else -> ThemeManager.flatBackgroundColor
            }
            window.statusBarColor = topBarCol.toArgb()
            window.navigationBarColor = navBarCol.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLightPaper
                isAppearanceLightNavigationBars = isLightPaper
            }
        }
    }

    CompositionLocalProvider(LocalUiEngine provides uiEngine) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = if (isLuxury) LuxuryShapes else MinimalistShapes,
            typography = if (isLuxury) LuxuryTypography else MinimalistTypography,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
