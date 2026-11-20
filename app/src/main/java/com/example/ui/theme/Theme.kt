package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RiyaDarkColorScheme =
  darkColorScheme(
    primary = SakuraRose,
    onPrimary = OnRosePrimary,
    primaryContainer = RoseContainer,
    onPrimaryContainer = TextPrimary,
    secondary = AmethystGlow,
    onSecondary = OnAmethyst,
    secondaryContainer = AmethystContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = RoseGoldWarm,
    onTertiary = OnRosePrimary,
    tertiaryContainer = RoseGoldContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianNight,
    onBackground = TextPrimary,
    surface = PlumSurface,
    onSurface = TextPrimary,
    surfaceVariant = PlumCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = PlumSurface,
    surfaceContainer = PlumSurfaceElevated,
    surfaceContainerHigh = PlumCard,
    outline = PlumCardBorder,
    outlineVariant = TextMuted.copy(alpha = 0.28f),
    error = ErrorCoral,
    onError = ObsidianNight,
  )

val RiyaShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun RiyaTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = RiyaDarkColorScheme,
    typography = RiyaTypography,
    shapes = RiyaShapes,
    content = content,
  )
}
