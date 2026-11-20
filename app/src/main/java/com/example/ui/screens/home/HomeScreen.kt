package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.config.RiyaIdentity
import com.example.core.security.ApiKeyState
import com.example.ui.components.RiyaGlassCard
import com.example.ui.components.RiyaOrb
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.voice.VoiceStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  onOpenPermissions: () -> Unit,
  onOpenApiKey: () -> Unit,
  onOpenSettings: () -> Unit,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

  val greetingText =
    if (state.settings.userName.isNotBlank()) {
      stringResource(R.string.home_greeting_format, state.settings.userName)
    } else {
      stringResource(R.string.home_greeting_default)
    }

  Box(
    modifier = Modifier.fillMaxSize().background(RiyaGradients.BackgroundAura),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize()
          .statusBarsPadding()
          .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Pinned Top Bar (never collides with status bar when scrolling)
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text =
              if (state.settings.masterEnabled) state.voiceState.statusHint
              else stringResource(R.string.home_master_paused),
            style = MaterialTheme.typography.labelSmall,
            color = if (state.settings.masterEnabled) SuccessMint else TextMuted,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
            checked = state.settings.masterEnabled,
            onCheckedChange = viewModel::toggleMasterSwitch,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = SakuraRose,
              ),
            modifier = Modifier.testTag("master_toggle_switch"),
          )
          Spacer(modifier = Modifier.width(6.dp))
          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Settings,
              contentDescription = stringResource(R.string.home_quick_settings),
              tint = TextSecondary,
            )
          }
        }
      }

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .clipToBounds()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(modifier = Modifier.height(6.dp))

      // Interactive Animated Orb (Tap to Talk / Barge-in)
      RiyaOrb(
        isActive = state.settings.masterEnabled,
        amplitude =
          when (state.voiceState.status) {
            VoiceStatus.LISTENING -> state.voiceState.amplitude.coerceAtLeast(0.45f)
            VoiceStatus.SPEAKING -> 0.72f
            VoiceStatus.THINKING -> 0.55f
            else -> if (state.voiceState.isServiceRunning) 0.3f else 0.12f
          },
        size = 185.dp,
        onClick = {
          if (!state.micGranted) onOpenPermissions() else viewModel.onOrbTapped()
        },
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = greetingText,
        style = MaterialTheme.typography.headlineMedium,
        color = RoseGoldWarm,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Quick Voice / Bubble Controls
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = {
            if (!state.micGranted && !state.voiceState.isServiceRunning) {
              onOpenPermissions()
            } else {
              viewModel.toggleBackgroundVoiceService()
            }
          },
          modifier = Modifier.testTag("toggle_background_voice_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            when {
              state.voiceState.isServiceRunning -> "Stop Hey Riya"
              !state.micGranted -> "Allow Mic"
              else -> "Start Hey Riya"
            }
          )
        }

        FilledTonalButton(
          onClick = {
            if (state.overlayGranted) viewModel.toggleOverlayBubble() else onOpenPermissions()
          },
          modifier = Modifier.testTag("toggle_overlay_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            when {
              !state.overlayGranted -> "Enable Bubble"
              state.overlayEnabled -> "Hide Bubble"
              else -> "Floating Bubble"
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Pending Risky Action Confirmation Card
      AnimatedVisibility(visible = state.pendingConfirmation != null) {
        state.pendingConfirmation?.let { pending ->
          RiyaGlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Rounded.WarningAmber, contentDescription = null, tint = WarningAmber)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Confirm Action",
                style = MaterialTheme.typography.titleMedium,
                color = WarningAmber,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = pending.summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = viewModel::confirmPending,
                colors = ButtonDefaults.buttonColors(containerColor = SakuraRose),
                modifier = Modifier.weight(1f).testTag("confirm_action_button"),
              ) {
                Text("Confirm")
              }
              OutlinedButton(
                onClick = viewModel::cancelPending,
                modifier = Modifier.weight(1f).testTag("cancel_action_button"),
              ) {
                Text("Cancel")
              }
            }
          }
        }
      }

      // Playful GF "Rag" Banner when excessive phone/chat detected
      AnimatedVisibility(visible = state.ragStatus?.shouldShowRag == true) {
        state.ragStatus?.reasonMessage?.let { ragMsg ->
          RiyaGlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Rounded.Favorite, contentDescription = null, tint = SakuraRose)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Riya's Caring Rag Alert",
                style = MaterialTheme.typography.titleMedium,
                color = SakuraRose,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = ragMsg, style = MaterialTheme.typography.bodyMedium, color = RoseGoldWarm)
          }
        }
      }

      // Live Conversation Preview Card
      if (state.voiceState.liveTranscript.isNotBlank() || state.voiceState.lastRiyaReply.isNotBlank()) {
        RiyaGlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
          if (state.voiceState.liveTranscript.isNotBlank()) {
            Text(
              text = "You: ${state.voiceState.liveTranscript}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
          }
          if (state.voiceState.lastRiyaReply.isNotBlank()) {
            Text(
              text = "Riya: ${state.voiceState.lastRiyaReply}",
              style = MaterialTheme.typography.bodyLarge,
              color = RoseGoldWarm,
            )
          }
        }
      }

      // Quick Text / Voice Command Input Bar
      OutlinedTextField(
        value = state.textDraft,
        onValueChange = viewModel::onTextDraftChanged,
        placeholder = { Text("Bolo jaan... (e.g., YouTube e gaan chalao)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { viewModel.sendTextCommand() }),
        trailingIcon = {
          IconButton(
            onClick = viewModel::sendTextCommand,
            modifier = Modifier.testTag("home_send_command_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Send,
              contentDescription = "Send command",
              tint = SakuraRose,
            )
          }
        },
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SakuraRose,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("home_command_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Quick Suggestion Chips
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(
          "YouTube e romantic gaan play koro",
          "Amar phone analyse koro",
          "Flashlight on koro",
          "Tomake ke banaiyeche?",
        ).forEach { prompt ->
          AssistChip(
            onClick = {
              viewModel.onTextDraftChanged(prompt)
              viewModel.sendTextCommand()
            },
            leadingIcon = {
              Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = AmethystGlow)
            },
            label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Readiness Cards
      RiyaGlassCard(
        onClick = onOpenApiKey,
        modifier = Modifier.testTag("home_api_key_card"),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Key,
            contentDescription = null,
            tint =
              when (state.apiKeyState) {
                is ApiKeyState.Saved -> SuccessMint
                is ApiKeyState.Missing -> WarningAmber
                is ApiKeyState.StorageError -> ErrorCoral
              },
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_card_api_title), style = MaterialTheme.typography.titleMedium)
            val sub =
              when (val k = state.apiKeyState) {
                is ApiKeyState.Saved -> if (k.isVerified) "Verified • ${k.maskedKey}" else "Saved • ${k.maskedKey}"
                is ApiKeyState.Missing -> "Tap to verify Grok API key"
                is ApiKeyState.StorageError -> k.message
              }
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
          }
          Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextMuted)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      RiyaGlassCard(
        onClick = onOpenPermissions,
        modifier = Modifier.testTag("home_permissions_card"),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (state.accessibilityConnected) Icons.Rounded.AccessibilityNew else Icons.Rounded.Security,
            contentDescription = null,
            tint = if (state.accessibilityConnected && state.micGranted) SuccessMint else WarningAmber,
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_card_permissions_title), style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                "${state.grantedPermissionsCount}/${state.totalPermissionsCount} enabled • Accessibility: ${if (state.accessibilityConnected) "Active" else "Off"}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
          Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextMuted)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      RiyaGlassCard(modifier = Modifier.testTag("home_permanent_memory_card")) {
        Text(
          text = "Riya's Lifelong Memory",
          style = MaterialTheme.typography.titleMedium,
          color = RoseGoldWarm,
        )
        Spacer(modifier = Modifier.height(6.dp))
        RiyaIdentity.PERMANENT_ROHAN_FACTS.forEach { fact ->
          Text(
            text = "• ${fact.label}: ${fact.detail}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(3.dp))
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
      }
    }
  }
}
