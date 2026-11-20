package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.security.ApiKeyState
import com.example.ui.components.RiyaGlassCard
import com.example.ui.components.RiyaOrb
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

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

  Scaffold(
    containerColor = ObsidianNight,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(RiyaGradients.BackgroundAura)
          .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Top Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
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
                if (state.settings.masterEnabled) stringResource(R.string.home_master_active)
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
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("home_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.home_quick_settings),
                tint = TextSecondary,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Animated Riya Orb
        RiyaOrb(
          isActive = state.settings.masterEnabled,
          amplitude = if (state.settings.masterEnabled) 0.35f else 0f,
          onClick = { viewModel.toggleMasterSwitch(!state.settings.masterEnabled) },
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = greetingText,
          style = MaterialTheme.typography.headlineMedium,
          color = RoseGoldWarm,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AssistChip(
          onClick = {},
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = AmethystGlow,
              modifier = Modifier.size(16.dp),
            )
          },
          label = {
            Text(
              text = stringResource(R.string.home_voice_phase_note),
              style = MaterialTheme.typography.labelSmall,
            )
          },
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Card 1: Gemini Brain Key Status
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
              modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_card_api_title),
                style = MaterialTheme.typography.titleMedium,
              )
              val subtitle =
                when (val keyState = state.apiKeyState) {
                  is ApiKeyState.Saved ->
                    if (keyState.isVerified) "Verified • ${keyState.maskedKey}"
                    else "Saved locally • ${keyState.maskedKey}"
                  is ApiKeyState.Missing -> "Tap to add your Gemini API key"
                  is ApiKeyState.StorageError -> keyState.message
                }
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = TextMuted,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Permissions Readiness
        RiyaGlassCard(
          onClick = onOpenPermissions,
          modifier = Modifier.testTag("home_permissions_card"),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Security,
              contentDescription = null,
              tint = if (state.micGranted) AmethystGlow else WarningAmber,
              modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_card_permissions_title),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  stringResource(
                    R.string.permissions_progress_format,
                    state.grantedPermissionsCount,
                    state.totalPermissionsCount,
                  ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = TextMuted,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Persona & Language
        RiyaGlassCard(
          onClick = onOpenSettings,
          modifier = Modifier.testTag("home_persona_card"),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = null,
              tint = SakuraRose,
              modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_card_persona_title),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  "${state.settings.preferredLanguage.displayName} • Warmth ${(state.settings.personaIntensity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = TextMuted,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 4: Lifelong Memory & Caring Rag Mode
        RiyaGlassCard(
          modifier = Modifier.testTag("home_permanent_memory_card"),
        ) {
          Text(
            text = "Riya's Lifelong Memory",
            style = MaterialTheme.typography.titleMedium,
            color = RoseGoldWarm,
          )
          Spacer(modifier = Modifier.height(8.dp))
          com.example.core.config.RiyaIdentity.PERMANENT_ROHAN_FACTS.forEach { fact ->
            Text(
              text = "• ${fact.label}: ${fact.detail}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
