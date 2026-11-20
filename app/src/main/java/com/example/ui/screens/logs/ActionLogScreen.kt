package com.example.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActionLogScreen(viewModel: ActionLogViewModel = hiltViewModel()) {
  val logs by viewModel.logs.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier.fillMaxSize().background(RiyaGradients.BackgroundAura),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
    ) {
      Text(
        text = "Action & Safety Log",
        style = MaterialTheme.typography.headlineMedium,
        color = RoseGoldWarm,
        modifier = Modifier.padding(vertical = 10.dp),
      )

      if (logs.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No phone control actions recorded yet.", color = TextSecondary)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(logs, key = { it.id }) { log ->
            val statusColor =
              when (log.status) {
                "SUCCESS" -> SuccessMint
                "BLOCKED", "PENDING_CONFIRM" -> WarningAmber
                else -> ErrorCoral
              }
            RiyaGlassCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(log.toolName, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                  onClick = {},
                  label = { Text(log.status, color = statusColor, style = MaterialTheme.typography.labelSmall) },
                )
              }
              if (log.argumentsSummary.isNotBlank()) {
                Text(log.argumentsSummary, style = MaterialTheme.typography.labelMedium, color = TextMuted)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(log.resultMessage, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
              Spacer(modifier = Modifier.height(4.dp))
              val ts = SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))
              Text(ts, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
          }
          item { Spacer(modifier = Modifier.height(16.dp)) }
        }
      }
    }
  }
}
