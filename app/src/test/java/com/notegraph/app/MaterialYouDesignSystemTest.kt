package com.notegraph.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.MaterialYouTokens
import com.notegraph.app.ui.theme.ThemeManager
import com.notegraph.app.ui.theme.UiEngineMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialYouDesignSystemTest {

    @After
    fun restoreDefaults() {
        ThemeManager.setUiEngine(UiEngineMode.MATERIAL_YOU)
        ThemeManager.setDynamicColorEnabled(true)
        ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
        ThemeManager.resetVisualsToDefault()
    }

    @Test
    fun `ui engine mode defaults to material you and parses all valid ids`() {
        assertEquals(UiEngineMode.MATERIAL_YOU, UiEngineMode.fromIdOrDefault(null))
        assertEquals(UiEngineMode.MATERIAL_YOU, UiEngineMode.fromIdOrDefault(""))
        assertEquals(UiEngineMode.MATERIAL_YOU, UiEngineMode.fromIdOrDefault("unknown_id"))
        assertEquals(UiEngineMode.MATERIAL_YOU, UiEngineMode.fromIdOrDefault("material_you"))
        assertEquals(UiEngineMode.MATERIAL_YOU, UiEngineMode.fromIdOrDefault("MATERIAL_YOU"))
        assertEquals(UiEngineMode.BESPOKE_LUXURY, UiEngineMode.fromIdOrDefault("bespoke_luxury"))
        assertEquals(UiEngineMode.CLASSIC, UiEngineMode.fromIdOrDefault("classic"))

        assertEquals("Material You (Default)", UiEngineMode.MATERIAL_YOU.displayName)
        assertEquals("M3 YOU", UiEngineMode.MATERIAL_YOU.badgeLabel)
        assertEquals("Bespoke Luxury", UiEngineMode.BESPOKE_LUXURY.displayName)
        assertEquals("Classic UI", UiEngineMode.CLASSIC.displayName)
    }

    @Test
    fun `material you tokens match M3 tonal palette across dark, paper and slate modes`() {
        val amoled = MaterialYouTokens.AmoledDark
        assertEquals(Color(0xFF0F0E13), amoled.canvasBackground)
        assertEquals(Color(0xFF1E1A22), amoled.elevatedSurface)
        assertEquals(Color(0xFFD0BCFF), amoled.accent)
        assertEquals(Color(0xFF4F378B), amoled.containerAccent)
        assertEquals(Color(0xFFEADDFF), amoled.onContainerAccent)

        val parchment = MaterialYouTokens.KindleParchment
        assertEquals(Color(0xFFFFF8F4), parchment.canvasBackground)
        assertEquals(Color(0xFF825500), parchment.accent)
        assertEquals(Color(0xFFFFDDB3), parchment.containerAccent)
        assertEquals(Color(0xFF201A15), parchment.textPrimary)

        val matte = MaterialYouTokens.MatteMinimalist
        assertEquals(Color(0xFF101418), matte.canvasBackground)
        assertEquals(Color(0xFFA5C8FF), matte.accent)
        assertEquals(Color(0xFF004785), matte.containerAccent)
        assertEquals(Color(0xFFE1E2E8), matte.textPrimary)

        assertEquals(amoled, MaterialYouTokens.forMode(AppThemeMode.DEEP_PURPLE_AMOLED))
        assertEquals(parchment, MaterialYouTokens.forMode(AppThemeMode.KINDLE_PARCHMENT))
        assertEquals(matte, MaterialYouTokens.forMode(AppThemeMode.MATTE_STUDIO))
    }

    @Test
    fun `theme manager surfaces and obsidian tokens reflect material you when active`() {
        ThemeManager.setUiEngine(UiEngineMode.MATERIAL_YOU)
        ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)

        assertTrue(ThemeManager.isMaterialYou)
        assertFalse(ThemeManager.isLuxuryUi)
        assertFalse(ThemeManager.isClassicUi)

        assertTrue(ThemeManager.activeScreenBrush is SolidColor)
        assertEquals(Color(0xFF0F0E13), (ThemeManager.activeScreenBrush as SolidColor).value)
        assertTrue(ThemeManager.activeAccentBrush is SolidColor)
        assertEquals(Color(0xFFD0BCFF), (ThemeManager.activeAccentBrush as SolidColor).value)
        assertTrue(ThemeManager.activeCardBrush(highlighted = false) is SolidColor)
        assertTrue(ThemeManager.activeCardBrush(highlighted = true) is SolidColor)

        assertEquals(Color(0xFFE6E1E5), ObsidianTokens.TextPrimary)
        assertEquals(Color(0xFFCCC2DC), ObsidianTokens.TextSecondary)
        assertEquals(Color(0xFF4F378B), ObsidianTokens.IconTileSurface)

        // Runtime theme change under Material You
        ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
        assertEquals(Color(0xFFFFF8F4), ThemeManager.flatBackgroundColor)
        assertEquals(Color(0xFF201A15), ObsidianTokens.TextPrimary)
        assertEquals(Color(0xFFFFDDB3), ObsidianTokens.IconTileSurface)

        ThemeManager.setTheme(AppThemeMode.MATTE_STUDIO)
        assertEquals(Color(0xFF101418), ThemeManager.flatBackgroundColor)
        assertEquals(Color(0xFFE1E2E8), ObsidianTokens.TextPrimary)
        assertEquals(Color(0xFF004785), ObsidianTokens.IconTileSurface)
    }

    @Test
    fun `dynamic color toggle updates state reactively`() {
        ThemeManager.setDynamicColorEnabled(false)
        assertFalse(ThemeManager.dynamicColorEnabled)
        ThemeManager.setDynamicColorEnabled(true)
        assertTrue(ThemeManager.dynamicColorEnabled)
    }

    @Test
    fun `seamless runtime toggle across all three interface engines`() {
        // 1. Material You (Default)
        ThemeManager.setUiEngine(UiEngineMode.MATERIAL_YOU)
        assertTrue(ThemeManager.isMaterialYou)
        assertFalse(ThemeManager.isLuxuryUi)
        assertFalse(ThemeManager.isClassicUi)

        // 2. Bespoke Luxury
        ThemeManager.setUiEngine(UiEngineMode.BESPOKE_LUXURY)
        assertFalse(ThemeManager.isMaterialYou)
        assertTrue(ThemeManager.isLuxuryUi)
        assertFalse(ThemeManager.isClassicUi)

        // 3. Classic UI
        ThemeManager.setUiEngine(UiEngineMode.CLASSIC)
        ThemeManager.setGradientsEnabledState(true)
        assertFalse(ThemeManager.isMaterialYou)
        assertFalse(ThemeManager.isLuxuryUi)
        assertTrue(ThemeManager.isClassicUi)
        assertFalse(ThemeManager.activeScreenBrush is SolidColor)

        // 4. Return to Material You
        ThemeManager.setUiEngine(UiEngineMode.MATERIAL_YOU)
        assertTrue(ThemeManager.isMaterialYou)
        assertTrue(ThemeManager.activeScreenBrush is SolidColor)
    }
}
