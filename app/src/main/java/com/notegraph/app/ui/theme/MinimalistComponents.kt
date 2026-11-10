package com.notegraph.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ambient backdrop applying the active theme's vertical gradient and top atmospheric glow.
 * Wrap transparent-background Scaffolds inside this composable.
 */
@Composable
fun MinimalistBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val preset = ThemeManager.currentGradient
    val gradientsOn = ThemeManager.gradientsEnabled

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeManager.activeScreenBrush)
    ) {
        if (gradientsOn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                preset.accentStart.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        content()
    }
}

/**
 * Horizontal row of choosable circular gradient orbs scoped to a specific [AppThemeMode].
 */
@Composable
fun GradientSwatchRow(
    mode: AppThemeMode,
    selectedId: String,
    onSelect: (ThemeGradientPreset) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val presets = ThemeManager.presetsFor(mode)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isSelected = preset.id == selectedId
            val ringColor by animateColorAsState(
                targetValue = if (isSelected) labelColor else Color.Transparent,
                label = "swatchRing"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .border(2.dp, ringColor, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(preset.accentStart, preset.accentEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected gradient",
                            tint = preset.onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = preset.name.substringBefore(' '),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) labelColor else secondaryLabelColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Interactive minimalist Theme & Gradient Studio bottom sheet content.
 */
@Composable
fun ThemeStudioSheetContent(
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val activeTheme = ThemeManager.currentTheme
    val activePreset = ThemeManager.currentGradient
    val gradientsOn = ThemeManager.gradientsEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ThemeManager.activeAccentBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = activePreset.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${activeTheme.displayName} • ${activePreset.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = {
                    onClose()
                    onOpenSettings()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "All Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Theme Mode Segmented Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppThemeMode.values().forEach { mode ->
                val selected = mode == activeTheme
                val modePreset = ThemeManager.presetFor(mode)
                val icon = when (mode) {
                    AppThemeMode.DEEP_PURPLE_AMOLED -> Icons.Rounded.DarkMode
                    AppThemeMode.KINDLE_PARCHMENT -> Icons.Rounded.MenuBook
                    AppThemeMode.MATTE_STUDIO -> Icons.Rounded.LaptopMac
                }
                Surface(
                    onClick = { ThemeManager.setTheme(mode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) modePreset.cardTint else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 0.75.dp,
                        color = if (selected) modePreset.accentStart else ThemeManager.cardBorderColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.displayName,
                            tint = if (selected) modePreset.accentStart else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Per-Theme Choosable Gradient Orbs
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${activeTheme.shortLabel} Accent Gradient",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GradientSwatchRow(
                mode = activeTheme,
                selectedId = ThemeManager.selectedGradientIdFor(activeTheme),
                onSelect = { chosen ->
                    ThemeManager.setGradient(activeTheme, chosen.id)
                }
            )
        }

        // Gradient Finish vs Flat Minimal Toggle
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ambient Gradients",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (gradientsOn) "Atmospheric gradient cards & controls" else "Clean solid classic surfaces",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = gradientsOn,
                    onCheckedChange = { ThemeManager.setGradientsEnabledState(it) }
                )
            }
        }
    }
}

/**
 * Minimalist gradient-backed capsule FAB.
 */
@Composable
fun GradientFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val preset = ThemeManager.currentGradient
    val shape = if (label != null) RoundedCornerShape(20.dp) else CircleShape
    Box(
        modifier = modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(ThemeManager.activeAccentBrush)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (label != null) 18.dp else 16.dp,
                vertical = 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides preset.onAccent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = preset.onAccent
                    )
                }
            }
        }
    }
}
