package com.notegraph.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.ThemeManager

/**
 * Design tokens for NoteGraph cards and architectural surfaces.
 * Automatically adapts between the legacy frosted obsidian tokens in Classic UI
 * and curated flat 1px-bordered luxury tokens in Bespoke Luxury mode.
 */
object ObsidianTokens {
    val DefaultAccent = Color(0xFF8B5CF6)
    val SurfaceTop = Color(0xFF1A1A1E)
    val SurfaceBottom = Color(0xFF0C0C0E)
    val FooterSurface = Color(0xFF050507).copy(alpha = 0.85f)

    val IconTileSurface: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.recessedSurface else Color(0xFF0A0A0D)

    val StatTileSurface: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.recessedSurface else Color(0xFF0E0E13)

    val DividerRule: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.dividerRule else Color.White.copy(alpha = 0.08f)

    val TextPrimary: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.textPrimary else Color(0xFFF8FAFC)

    val TextSecondary: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.textSecondary else Color(0xFFCBD5E1)

    val TextMuted: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.textMuted else Color(0xFF94A3B8)

    val FooterText: Color
        get() = if (ThemeManager.isLuxuryUi) ThemeManager.luxuryTokens.footerText else Color(0xFFA1A1AA)

    /**
     * Ensures ultra-dark accents (such as dark charcoal paper presets) remain luminous
     * when illuminating dark obsidian borders and glows, while keeping vibrant accents untouched.
     */
    fun resolveAccent(rawAccent: Color): Color {
        return if (rawAccent.luminance() < 0.12f) {
            lerp(rawAccent, Color.White, 0.36f)
        } else {
            rawAccent
        }
    }

    /**
     * Produces a legible accent ink adjusted for the active card surface luminance.
     */
    fun readableAccentInk(accent: Color): Color {
        if (ThemeManager.isLuxuryUi && ThemeManager.currentTheme == AppThemeMode.KINDLE_PARCHMENT) {
            return if (accent.luminance() > 0.30f) lerp(accent, Color(0xFF1E1B18), 0.38f) else accent
        }
        return if (accent.luminance() < 0.28f) {
            lerp(accent, Color.White, 0.24f)
        } else {
            accent
        }
    }

    fun badgeForegroundOn(accent: Color): Color {
        return if (accent.luminance() > 0.36f) Color(0xFF0B0B0E) else Color.White
    }
}

/**
 * Adaptive surface card for NoteGraph:
 * - Bespoke Luxury (Default): Zero drop shadows, flat theme surface, razor-thin 1px border, clean monospace footer.
 * - Classic UI: Legacy frosted two-tier obsidian glass with glow flare, specular highlight & floating shadow.
 */
@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    accentColor: Color = ObsidianTokens.DefaultAccent,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 6.dp,
    headerPadding: Dp = 16.dp,
    headerContent: @Composable ColumnScope.() -> Unit,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (ThemeManager.isLuxuryUi) {
        val t = ThemeManager.luxuryTokens
        val shape = RoundedCornerShape(8.dp)
        var luxModifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
            .clip(shape)
            .background(t.elevatedSurface)
            .border(width = 1.dp, color = t.borderHairline, shape = shape)

        if (onClick != null) {
            luxModifier = luxModifier.clickable(onClick = onClick)
        }

        Column(modifier = luxModifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(headerPadding)
            ) {
                CompositionLocalProvider(LocalContentColor provides t.textPrimary) {
                    headerContent()
                }
            }

            if (footerContent != null) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = t.dividerRule
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.recessedSurface)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides t.footerText) {
                        footerContent()
                    }
                }
            }
        }
        return
    }

    val effectiveAccent = ObsidianTokens.resolveAccent(accentColor)
    val shape = RoundedCornerShape(16.dp)
    val specularCore = lerp(effectiveAccent, Color.White, 0.58f)

    var cardModifier = modifier
        .fillMaxWidth()
        .padding(vertical = verticalPadding)
        .shadow(
            elevation = 14.dp,
            shape = shape,
            ambientColor = effectiveAccent.copy(alpha = 0.28f),
            spotColor = effectiveAccent.copy(alpha = 0.28f)
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ObsidianTokens.SurfaceTop,
                    ObsidianTokens.SurfaceBottom
                )
            )
        )
        .drawBehind {
            val glowRadius = (size.minDimension * 0.95f).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        effectiveAccent.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.16f, size.height * 0.36f),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(size.width * 0.16f, size.height * 0.36f)
            )
        }
        .border(
            width = 1.5.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    effectiveAccent.copy(alpha = 0.55f),
                    effectiveAccent.copy(alpha = 0.20f)
                )
            ),
            shape = shape
        )

    if (onClick != null) {
        cardModifier = cardModifier.clickable(onClick = onClick)
    }

    Box(modifier = cardModifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tier 1: Frosted Obsidian Header / Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(headerPadding)
            ) {
                CompositionLocalProvider(LocalContentColor provides ObsidianTokens.TextPrimary) {
                    headerContent()
                }
            }

            // Tier 2: Darker Recessed Footer Bar (Unclipped / Untruncated Metadata)
            if (footerContent != null) {
                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = ObsidianTokens.DividerRule
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianTokens.FooterSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides ObsidianTokens.FooterText) {
                        footerContent()
                    }
                }
            }
        }

        // Top Specular Metallic Highlight Line along upper rim
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.75.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            effectiveAccent.copy(alpha = 0.68f),
                            specularCore,
                            effectiveAccent.copy(alpha = 0.68f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Icon frame adapting between crisp hairline architectural frame (Luxury)
 * and illuminated rounded icon emblem (Classic).
 */
@Composable
fun ObsidianIconTile(
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp
) {
    if (ThemeManager.isLuxuryUi) {
        val t = ThemeManager.luxuryTokens
        val tileShape = RoundedCornerShape(6.dp)
        Box(
            modifier = modifier
                .size(size)
                .clip(tileShape)
                .background(t.recessedSurface)
                .border(width = 1.dp, color = t.borderHairline, shape = tileShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (accentColor == t.accent) t.accent else ObsidianTokens.readableAccentInk(accentColor),
                modifier = Modifier.size(iconSize)
            )
        }
        return
    }

    val effectiveAccent = ObsidianTokens.resolveAccent(accentColor)
    val tileShape = RoundedCornerShape((size.value * 0.28f).dp)
    val rimTop = lerp(effectiveAccent, Color.White, 0.32f)

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 10.dp,
                shape = tileShape,
                ambientColor = effectiveAccent.copy(alpha = 0.45f),
                spotColor = effectiveAccent.copy(alpha = 0.45f)
            )
            .clip(tileShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ObsidianTokens.IconTileSurface,
                        Color(0xFF050507)
                    )
                )
            )
            .border(
                width = 1.25.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        rimTop.copy(alpha = 0.85f),
                        effectiveAccent.copy(alpha = 0.25f)
                    )
                ),
                shape = tileShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ObsidianTokens.readableAccentInk(effectiveAccent),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Compact tag badge: sharp hairline monospace tag in Luxury mode,
 * metallic sheen gradient pill in Classic mode.
 */
@Composable
fun ObsidianMetallicBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (ThemeManager.isLuxuryUi) {
        val t = ThemeManager.luxuryTokens
        val pillShape = RoundedCornerShape(4.dp)
        val upper = text.uppercase()
        val isAccentState = upper == "ACTIVE" || upper == "DEFAULT" || upper == "PIN" || upper == "HUB" || upper == "LUXURY"
        val borderColor = if (isAccentState) t.accent.copy(alpha = 0.55f) else t.borderHairline
        val ink = if (isAccentState) ObsidianTokens.readableAccentInk(t.accent) else t.textSecondary

        Box(
            modifier = modifier
                .clip(pillShape)
                .background(t.recessedSurface)
                .border(width = 1.dp, color = borderColor, shape = pillShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = upper,
                color = ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.75.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        return
    }

    val effectiveAccent = ObsidianTokens.resolveAccent(accentColor)
    val pillShape = RoundedCornerShape(6.dp)
    val startSheen = lerp(effectiveAccent, Color.White, 0.34f)
    val endTone = lerp(effectiveAccent, Color.Black, 0.14f)
    val textColor = ObsidianTokens.badgeForegroundOn(effectiveAccent)

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(startSheen, effectiveAccent, endTone)
                )
            )
            .border(
                width = 0.75.dp,
                color = lerp(effectiveAccent, Color.White, 0.52f).copy(alpha = 0.7f),
                shape = pillShape
            )
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            letterSpacing = 0.7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Right-side stat readout module: hairline flat technical tile in Luxury mode,
 * illuminated glowing counter module in Classic mode.
 */
@Composable
fun ObsidianStatTile(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (ThemeManager.isLuxuryUi) {
        val t = ThemeManager.luxuryTokens
        val tileShape = RoundedCornerShape(6.dp)
        Column(
            modifier = modifier
                .clip(tileShape)
                .background(t.recessedSurface)
                .border(width = 1.dp, color = t.borderHairline, shape = tileShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = t.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label.uppercase(),
                color = t.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    val effectiveAccent = ObsidianTokens.resolveAccent(accentColor)
    val tileShape = RoundedCornerShape(12.dp)
    val ink = ObsidianTokens.readableAccentInk(effectiveAccent)

    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = tileShape,
                ambientColor = effectiveAccent.copy(alpha = 0.34f),
                spotColor = effectiveAccent.copy(alpha = 0.34f)
            )
            .clip(tileShape)
            .background(ObsidianTokens.StatTileSurface)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        effectiveAccent.copy(alpha = 0.65f),
                        effectiveAccent.copy(alpha = 0.25f)
                    )
                ),
                shape = tileShape
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = ink,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label.uppercase(),
            color = ObsidianTokens.TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun NoteGraphInspectorSample(
    accentColor: Color = ObsidianTokens.DefaultAccent
) {
    ObsidianCard(
        accentColor = accentColor,
        onClick = {},
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ObsidianIconTile(
                    icon = Icons.Rounded.Share,
                    accentColor = accentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NoteGraph Architecture",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianTokens.TextPrimary
                        )
                        ObsidianMetallicBadge(
                            text = "ACTIVE",
                            accentColor = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "@vault • 3 Connected Nodes",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ObsidianTokens.TextMuted
                    )
                }
                ObsidianStatTile(
                    value = "4 pages",
                    label = "TARGET",
                    accentColor = accentColor
                )
            }
        },
        footerContent = {
            Text(
                text = "Synced via Room Database • Local storage active • Last updated Today 14:22",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ObsidianTokens.FooterText
            )
        }
    )
}
