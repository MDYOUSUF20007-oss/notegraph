package com.notegraph.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Global theme manager supporting AMOLED Pure Black (#000000) vs Standard Dark.
 */
object ThemeManager {
    var isAmoledMode by mutableStateOf(true)
}

val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF212121),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFEEEEEE),
    outline = Color(0xFF424242)
)

val StandardDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF00344F),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF616161)
)

@Composable
fun NoteGraphTheme(
    isAmoled: Boolean = ThemeManager.isAmoledMode,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isAmoled) AmoledDarkColorScheme else StandardDarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
