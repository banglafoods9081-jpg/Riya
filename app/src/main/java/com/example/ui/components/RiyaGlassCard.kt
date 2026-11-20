package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlumCard
import com.example.ui.theme.RiyaGradients

@Composable
fun RiyaGlassCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val border = BorderStroke(1.dp, RiyaGradients.CardBorder)
  val colors =
    CardDefaults.cardColors(
      containerColor = PlumCard.copy(alpha = 0.85f),
      contentColor = MaterialTheme.colorScheme.onSurface,
    )

  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = colors,
      border = border,
    ) {
      Column(modifier = Modifier.padding(18.dp), content = content)
    }
  } else {
    Card(
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = colors,
      border = border,
    ) {
      Column(modifier = Modifier.padding(18.dp), content = content)
    }
  }
}
