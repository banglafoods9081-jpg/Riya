package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep Romantic Obsidian & Midnight Rose Palette
val ObsidianNight = Color(0xFF0B0514)
val PlumSurface = Color(0xFF150B24)
val PlumSurfaceElevated = Color(0xFF1E1033)
val PlumCard = Color(0xFF25143E)
val PlumCardBorder = Color(0x4D9F5AFD)

val SakuraRose = Color(0xFFFF5C93)
val SakuraRoseSoft = Color(0xFFFF8BB5)
val RoseContainer = Color(0xFF5E1137)
val OnRosePrimary = Color(0xFF2D0015)

val AmethystGlow = Color(0xFFB57BFF)
val AmethystContainer = Color(0xFF3E1E6B)
val OnAmethyst = Color(0xFF1C043B)

val RoseGoldWarm = Color(0xFFFFB4A2)
val RoseGoldContainer = Color(0xFF5C2820)

val TextPrimary = Color(0xFFFBF5FA)
val TextSecondary = Color(0xFFD2C1DC)
val TextMuted = Color(0xFF9C88AA)

val SuccessMint = Color(0xFF5EEAD4)
val WarningAmber = Color(0xFFFBBF24)
val ErrorCoral = Color(0xFFFF6B81)

object RiyaGradients {
  val BackgroundAura =
    Brush.verticalGradient(
      colors = listOf(Color(0xFF130824), ObsidianNight, Color(0xFF12061D))
    )

  val RomanticAccent =
    Brush.linearGradient(
      colors = listOf(SakuraRose, AmethystGlow),
      start = Offset.Zero,
      end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

  val OrbCore =
    Brush.radialGradient(
      colors = listOf(RoseGoldWarm, SakuraRose, AmethystGlow, Color(0xFF4A148C))
    )

  val CardBorder =
    Brush.linearGradient(
      colors = listOf(SakuraRose.copy(alpha = 0.45f), AmethystGlow.copy(alpha = 0.25f))
    )
}
