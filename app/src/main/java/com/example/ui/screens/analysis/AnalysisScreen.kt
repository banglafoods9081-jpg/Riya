package com.example.ui.screens.analysis

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier.fillMaxSize().background(RiyaGradients.BackgroundAura),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Phone Analysis", style = MaterialTheme.typography.headlineMedium, color = RoseGoldWarm)
        IconButton(
          onClick = viewModel::refreshAnalysis,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_analysis_button"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextSecondary)
        }
      }

      Button(
        onClick = viewModel::speakFullReport,
        enabled = !state.isAnalyzing,
        colors = ButtonDefaults.buttonColors(containerColor = SakuraRose),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("speak_phone_analysis_button"),
      ) {
        if (state.isAnalyzing) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Analyzing phone...")
        } else {
          Icon(Icons.Rounded.VolumeUp, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Amar Phone Analyse Koro (Speak)")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        state.spokenSummary?.let { summary ->
          item {
            RiyaGlassCard {
              Text("Riya's Report", style = MaterialTheme.typography.titleMedium, color = SakuraRose)
              Spacer(modifier = Modifier.height(4.dp))
              Text(summary, style = MaterialTheme.typography.bodyMedium, color = RoseGoldWarm)
            }
          }
        }

        state.report?.let { r ->
          item {
            RiyaGlassCard {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = SuccessMint)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Battery: ${r.batteryPercent}% • ${r.batteryTempCelsius}°C • ${r.batteryHealthLabel} ${if (r.isCharging) "(Charging)" else ""}",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, contentDescription = null, tint = AmethystGlow)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "RAM: ${"%.1f".format(r.ramUsedGb)} / ${"%.1f".format(r.ramTotalGb)} GB • Network: ${r.networkLabel}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, contentDescription = null, tint = RoseGoldWarm)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Storage: ${"%.1f".format(r.storageUsedGb)} / ${"%.1f".format(r.storageTotalGb)} GB • Apps: ${r.installedAppCount}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
            }
          }

          item {
            RiyaGlassCard {
              Text("Screen Time & Chat Monitor", style = MaterialTheme.typography.titleMedium, color = RoseGoldWarm)
              Spacer(modifier = Modifier.height(6.dp))
              if (r.usageStatsGranted) {
                Text(
                  text = "Today: ${r.totalScreenMinutesToday} mins total • ${r.chatMinutesToday} mins on chat apps",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
                r.topAppsToday.forEach { app ->
                  Text(
                    text = "• ${app.appName}: ${app.minutesToday} min${if (app.isChatApp) " (Chat)" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                  )
                }
              } else {
                Text(
                  text = "Enable Usage Stats permission so Riya can track screen time, chat time, and unused apps.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextMuted,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text("Tips: ${r.tips.joinToString(" • ")}", style = MaterialTheme.typography.bodyMedium, color = SuccessMint)
            }
          }
        }

        if (state.notifications.isNotEmpty()) {
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Notifications, contentDescription = null, tint = AmethystGlow, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Recent Notifications", style = MaterialTheme.typography.titleMedium)
            }
          }
          items(state.notifications.take(15), key = { it.id }) { notif ->
            RiyaGlassCard {
              Text("[${notif.appName}] ${notif.title}", style = MaterialTheme.typography.titleMedium)
              if (notif.body.isNotBlank()) {
                Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
              }
            }
          }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}
