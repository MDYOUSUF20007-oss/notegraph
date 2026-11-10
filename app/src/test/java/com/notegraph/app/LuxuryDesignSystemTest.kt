package com.notegraph.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.graph.GraphBlueprintSpec
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.LuxuryTokens
import com.notegraph.app.ui.theme.ThemeManager
import com.notegraph.app.ui.theme.UiEngineMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxuryDesignSystemTest {

    @After
    fun restoreThemeDefaults() {
        ThemeManager.setUiEngine(UiEngineMode.BESPOKE_LUXURY)
        ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
        ThemeManager.resetVisualsToDefault()
    }

    @Test
    fun `luxury tokens match strict AMOLED Black, Kindle Parchment and Matte Minimalist palette`() {
        val amoled = LuxuryTokens.AmoledBlack
        assertEquals(Color(0xFF000000), amoled.canvasBackground)
        assertEquals(Color(0xFF121212), amoled.elevatedSurface)
        assertEquals(Color(0xFF262626), amoled.borderHairline)
        assertEquals(Color(0xFF8B5CF6), amoled.accent)

        val parchment = LuxuryTokens.KindleParchment
        assertEquals(Color(0xFFF4F1EA), parchment.canvasBackground)
        assertEquals(Color(0xFF1E1B18), parchment.textPrimary)
        assertEquals(Color(0xFF8B5CF6), parchment.accent)

        val matte = LuxuryTokens.MatteMinimalist
        assertEquals(Color(0xFF1E2229), matte.canvasBackground)
        assertEquals(Color(0xFF8B5CF6), matte.accent)

        assertEquals(amoled, LuxuryTokens.forMode(AppThemeMode.DEEP_PURPLE_AMOLED))
        assertEquals(parchment, LuxuryTokens.forMode(AppThemeMode.KINDLE_PARCHMENT))
        assertEquals(matte, LuxuryTokens.forMode(AppThemeMode.MATTE_STUDIO))
    }

    @Test
    fun `ui engine mode defaults to bespoke luxury and parses saved ids`() {
        assertEquals(UiEngineMode.BESPOKE_LUXURY, UiEngineMode.fromIdOrDefault(null))
        assertEquals(UiEngineMode.BESPOKE_LUXURY, UiEngineMode.fromIdOrDefault(""))
        assertEquals(UiEngineMode.BESPOKE_LUXURY, UiEngineMode.fromIdOrDefault("bespoke_luxury"))
        assertEquals(UiEngineMode.CLASSIC, UiEngineMode.fromIdOrDefault("classic"))
        assertEquals(UiEngineMode.CLASSIC, UiEngineMode.fromIdOrDefault("CLASSIC"))

        assertEquals("Classic UI", UiEngineMode.CLASSIC.displayName)
        assertEquals("Bespoke Luxury (Default)", UiEngineMode.BESPOKE_LUXURY.displayName)
    }

    @Test
    fun `runtime engine toggle switches between flat SolidColor luxury surfaces and classic gradients`() {
        ThemeManager.setUiEngine(UiEngineMode.BESPOKE_LUXURY)
        ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)

        assertTrue(ThemeManager.isLuxuryUi)
        assertTrue(ThemeManager.activeScreenBrush is SolidColor)
        assertEquals(Color(0xFF000000), (ThemeManager.activeScreenBrush as SolidColor).value)
        assertTrue(ThemeManager.activeAccentBrush is SolidColor)
        assertEquals(Color(0xFF8B5CF6), (ThemeManager.activeAccentBrush as SolidColor).value)
        assertTrue(ThemeManager.activeCardBrush(highlighted = false) is SolidColor)
        assertTrue(ThemeManager.activeCardBrush(highlighted = true) is SolidColor)
        assertEquals(Color(0xFF262626), ThemeManager.cardBorderColor)

        // Runtime switch to Kindle Parchment updates ink polarity cleanly
        ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
        assertEquals(Color(0xFFF4F1EA), ThemeManager.flatBackgroundColor)
        assertEquals(LuxuryTokens.KindleParchment.textPrimary, ObsidianTokens.TextPrimary)

        // Runtime switch to Matte Minimalist
        ThemeManager.setTheme(AppThemeMode.MATTE_STUDIO)
        assertEquals(Color(0xFF1E2229), ThemeManager.flatBackgroundColor)

        // Runtime toggle back to Classic UI
        ThemeManager.setUiEngine(UiEngineMode.CLASSIC)
        ThemeManager.setGradientsEnabledState(true)
        assertFalse(ThemeManager.isLuxuryUi)
        assertFalse(ThemeManager.activeScreenBrush is SolidColor)
        assertFalse(ThemeManager.activeAccentBrush is SolidColor)
        assertEquals(Color(0xFFF8FAFC), ObsidianTokens.TextPrimary)
    }

    @Test
    fun `graph blueprint spec preserves 1px 30pct curved edges and non-overlapping labels`() {
        assertEquals(0.30f, GraphBlueprintSpec.UNSELECTED_EDGE_ALPHA, 0.0001f)
        assertEquals(1f, GraphBlueprintSpec.BASE_EDGE_STROKE_PX, 0.0001f)
        assertEquals(Color(0xFF8B5CF6), GraphBlueprintSpec.SignatureAccent)

        val a = Offset(100f, 100f)
        val b = Offset(300f, 220f)
        val midpoint = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
        val ctrlAB = GraphBlueprintSpec.bezierControlPoint(a, b)
        val ctrlBA = GraphBlueprintSpec.bezierControlPoint(b, a)

        assertEquals(ctrlAB, ctrlBA)
        assertNotEquals(midpoint, ctrlAB)

        val occupied = listOf(Rect(left = 50f, top = 50f, right = 180f, bottom = 80f))
        val overlapping = Rect(left = 175f, top = 60f, right = 260f, bottom = 90f)
        val spaced = Rect(left = 200f, top = 60f, right = 290f, bottom = 90f)

        assertFalse(
            GraphBlueprintSpec.canPlaceLabelWithoutOverlap(
                candidateScreenBox = overlapping,
                occupiedScreenBoxes = occupied,
                minPaddingPx = 8f,
                isHighPriority = false
            )
        )
        assertTrue(
            GraphBlueprintSpec.canPlaceLabelWithoutOverlap(
                candidateScreenBox = overlapping,
                occupiedScreenBoxes = occupied,
                minPaddingPx = 8f,
                isHighPriority = true
            )
        )
        assertTrue(
            GraphBlueprintSpec.canPlaceLabelWithoutOverlap(
                candidateScreenBox = spaced,
                occupiedScreenBoxes = occupied,
                minPaddingPx = 8f,
                isHighPriority = false
            )
        )

        assertEquals("[42,-15]", GraphBlueprintSpec.formatCoordinateTag(42.7f, -15.3f))
    }
}
