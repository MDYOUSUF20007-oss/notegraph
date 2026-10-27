package com.notegraph.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9E9E9E),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF212121),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFEEEEEE),
    outline = Color(0xFF424242)
)

@Composable
fun NoteGraphTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        content = content
    )
}