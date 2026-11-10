package com.notegraph.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.NoteGraphGradients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianThemeTest {

    @Test
    fun `obsidian tokens match frosted two-tier specification`() {
        assertEquals(Color(0xFF8B5CF6), ObsidianTokens.DefaultAccent)
        assertEquals(Color(0xFF1A1A1E), ObsidianTokens.SurfaceTop)
        assertEquals(Color(0xFF0C0C0E), ObsidianTokens.SurfaceBottom)
        assertEquals(Color(0xFF050507).copy(alpha = 0.85f), ObsidianTokens.FooterSurface)
    }

    @Test
    fun `default amoled preset uses NoteGraph purple and preserves other palettes`() {
        val defaultAmoled = NoteGraphGradients.presetsFor(AppThemeMode.DEEP_PURPLE_AMOLED).first()
        assertEquals("obsidian_violet", defaultAmoled.id)
        assertEquals(Color(0xFF8B5CF6), defaultAmoled.accentStart)

        assertTrue(
            "Expected Obsidian Gold preset in AMOLED presets",
            NoteGraphGradients.amoledPresets.any { it.id == "obsidian_gold" }
        )
    }

    @Test
    fun `resolveAccent preserves vibrant accents and lifts dark ink accents`() {
        val purple = Color(0xFF8B5CF6)
        val gold = Color(0xFFF59E0B)
        val cyan = Color(0xFF38BDF8)
        assertEquals(purple, ObsidianTokens.resolveAccent(purple))
        assertEquals(gold, ObsidianTokens.resolveAccent(gold))
        assertEquals(cyan, ObsidianTokens.resolveAccent(cyan))

        val darkInk = Color(0xFF44403C)
        val resolvedDark = ObsidianTokens.resolveAccent(darkInk)
        assertTrue(
            "Dark ink should be lightened for dark obsidian cards",
            resolvedDark.luminance() > darkInk.luminance()
        )
    }

    @Test
    fun `badgeForegroundOn switches contrast cleanly based on luminance`() {
        assertEquals(Color.White, ObsidianTokens.badgeForegroundOn(Color(0xFF8B5CF6)))
        assertEquals(Color(0xFF0B0B0E), ObsidianTokens.badgeForegroundOn(Color(0xFFF59E0B)))
        assertEquals(Color(0xFF0B0B0E), ObsidianTokens.badgeForegroundOn(Color(0xFF38BDF8)))
    }
}
