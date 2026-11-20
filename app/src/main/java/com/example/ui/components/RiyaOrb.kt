package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RiyaOrb(
  isActive: Boolean,
  amplitude: Float = 0.25f,
  size: Dp = 210.dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "riya_orb_transition")

  val breathe by
    transition.animateFloat(
      initialValue = 0.92f,
      targetValue = 1.08f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_breathe",
    )

  val orbitAngle by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 9000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "orb_orbit",
    )

  val primaryColor = if (isActive) SakuraRose else Color(0xFF6B5B7B)
  val secondaryColor = if (isActive) AmethystGlow else Color(0xFF423552)
  val coreHighlight = if (isActive) RoseGoldWarm else Color(0xFF8C7A9E)
  val interactionSource = remember { MutableInteractionSource() }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(size)
        .minimumInteractiveComponentSize()
        .clip(CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = SakuraRose),
          role = Role.Button,
          onClick = onClick,
        )
        .semantics {
          contentDescription =
            if (isActive) "Riya voice orb, active" else "Riya voice orb, paused"
        }
        .testTag("riya_orb_button"),
  ) {
    Canvas(modifier = Modifier.size(size)) {
      val center = Offset(this.size.width / 2f, this.size.height / 2f)
      val minDim = this.size.minDimension
      val boost = if (isActive) (1f + amplitude.coerceIn(0f, 1f) * 0.14f) else 0.9f
      val outerRadius = minDim * 0.46f * breathe * boost.coerceAtMost(1.06f)

      // Ambient radial aura
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                primaryColor.copy(alpha = if (isActive) 0.42f else 0.15f),
                secondaryColor.copy(alpha = if (isActive) 0.18f else 0.06f),
                Color.Transparent,
              ),
            center = center,
            radius = outerRadius,
          ),
        radius = outerRadius,
        center = center,
      )

      // Orbiting soundwave ring 1
      val ringRadius = minDim * 0.34f * (if (isActive) breathe else 0.96f)
      drawCircle(
        brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, coreHighlight, primaryColor)),
        radius = ringRadius,
        center = center,
        style = Stroke(width = minDim * 0.015f),
      )

      // Orbiting accent spark
      if (isActive) {
        val rad = Math.toRadians(orbitAngle.toDouble())
        val sparkOffset =
          Offset(
            x = center.x + (ringRadius * cos(rad)).toFloat(),
            y = center.y + (ringRadius * sin(rad)).toFloat(),
          )
        drawCircle(
          color = RoseGoldWarm,
          radius = minDim * 0.025f,
          center = sparkOffset,
        )
      }

      // Inner luminous orb sphere
      val innerRadius = minDim * 0.25f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                coreHighlight,
                primaryColor,
                secondaryColor.copy(alpha = 0.92f),
              ),
            center = Offset(center.x - innerRadius * 0.22f, center.y - innerRadius * 0.22f),
            radius = innerRadius * 1.35f,
          ),
        radius = innerRadius,
        center = center,
      )
    }

    Icon(
      imageVector = if (isActive) Icons.Rounded.GraphicEq else Icons.Rounded.MicOff,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.size(38.dp),
    )
  }
}
