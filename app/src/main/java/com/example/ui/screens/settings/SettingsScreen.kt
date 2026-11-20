package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.PreferredLanguage
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onOpenApiKey: () -> Unit,
  onOpenPermissions: () -> Unit,
  onRerunOnboarding: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  var nameDraft by remember(settings.userName) { mutableStateOf(settings.userName) }

  LaunchedEffect(settings.userName) {
    if (nameDraft != settings.userName) {
      nameDraft = settings.userName
    }
  }

  Scaffold(
    containerColor = ObsidianNight,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
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
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Profile & Language Card
        RiyaGlassCard {
          Text(
            text = "Profile & Language",
            style = MaterialTheme.typography.titleMedium,
            color = RoseGoldWarm,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = nameDraft,
            onValueChange = {
              nameDraft = it
              viewModel.updateUserName(it)
            },
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
          )
          Spacer(modifier = Modifier.height(14.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PreferredLanguage.entries.forEach { lang ->
              FilterChip(
                selected = settings.preferredLanguage == lang,
                onClick = { viewModel.updateLanguage(lang) },
                label = { Text(lang.displayName) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              )
            }
          }
        }

        // Persona & Voice Tuning
        RiyaGlassCard {
          Text(
            text = stringResource(R.string.settings_persona_intensity),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "${(settings.personaIntensity * 100).toInt()}% romantic warmth",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Slider(
            value = settings.personaIntensity,
            onValueChange = viewModel::updatePersonaIntensity,
            colors = SliderDefaults.colors(thumbColor = SakuraRose, activeTrackColor = SakuraRose),
            modifier = Modifier.testTag("settings_persona_slider"),
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "${stringResource(R.string.settings_tts_speed)}: ${"%.2f".format(settings.ttsSpeed)}x",
            style = MaterialTheme.typography.titleMedium,
          )
          Slider(
            value = settings.ttsSpeed,
            onValueChange = { viewModel.updateVoiceTuning(it, settings.ttsPitch) },
            valueRange = 0.6f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AmethystGlow, activeTrackColor = AmethystGlow),
          )

          Text(
            text = "${stringResource(R.string.settings_tts_pitch)}: ${"%.2f".format(settings.ttsPitch)}",
            style = MaterialTheme.typography.titleMedium,
          )
          Slider(
            value = settings.ttsPitch,
            onValueChange = { viewModel.updateVoiceTuning(settings.ttsSpeed, it) },
            valueRange = 0.75f..1.4f,
            colors = SliderDefaults.colors(thumbColor = AmethystGlow, activeTrackColor = AmethystGlow),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.settings_wake_word),
              style = MaterialTheme.typography.titleMedium,
            )
            Switch(
              checked = settings.wakeWordEnabled,
              onCheckedChange = viewModel::setWakeWordEnabled,
              colors = SwitchDefaults.colors(checkedTrackColor = SakuraRose),
              modifier = Modifier.testTag("settings_wake_word_switch"),
            )
          }
        }

        // Security & Permissions Shortcuts
        OutlinedButton(
          onClick = onOpenApiKey,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_api_key_button"),
        ) {
          Text(stringResource(R.string.home_quick_setup_apikey))
        }

        OutlinedButton(
          onClick = onOpenPermissions,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_permissions_button"),
        ) {
          Text(stringResource(R.string.home_quick_setup_permissions))
        }

        OutlinedButton(
          onClick = { viewModel.rerunOnboarding(onRerunOnboarding) },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_rerun_onboarding_button"),
        ) {
          Text(stringResource(R.string.settings_rerun_onboarding))
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
