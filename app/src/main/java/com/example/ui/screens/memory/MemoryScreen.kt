package com.example.ui.screens.memory

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextSecondary

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel()) {
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
        Text("Riya's Memory", style = MaterialTheme.typography.headlineMedium, color = RoseGoldWarm)
        OutlinedButton(
          onClick = viewModel::wipeDynamicMemory,
          modifier = Modifier.testTag("wipe_dynamic_memory_button"),
        ) {
          Text("Wipe Dynamic")
        }
      }

      OutlinedTextField(
        value = state.newFactDraft,
        onValueChange = viewModel::onDraftChange,
        placeholder = { Text("Teach Riya something to remember...") },
        singleLine = true,
        trailingIcon = {
          IconButton(
            onClick = viewModel::addMemory,
            modifier = Modifier.testTag("add_memory_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Save memory", tint = SakuraRose)
          }
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("add_memory_input"),
      )

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(state.facts, key = { it.id }) { item ->
          RiyaGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (item.isPermanent) Icons.Rounded.Lock else Icons.Rounded.Psychology,
                contentDescription = null,
                tint = if (item.isPermanent) SuccessMint else AmethystGlow,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (item.isPermanent) "${item.category} • Lifelong" else item.category,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (item.isPermanent) SuccessMint else RoseGoldWarm,
                )
                Text(
                  text = item.fact,
                  style = MaterialTheme.typography.bodyLarge,
                  color = TextSecondary,
                )
              }
              if (!item.isPermanent) {
                IconButton(
                  onClick = { viewModel.deleteFact(item.id) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete fact", tint = ErrorCoral)
                }
              }
            }
          }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}
