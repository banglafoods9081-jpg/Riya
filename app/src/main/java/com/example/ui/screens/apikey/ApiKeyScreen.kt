package com.example.ui.screens.apikey

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.security.ApiKeyState
import com.example.data.repository.RIYA_ELEVENLABS_VOICES
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApiKeyScreen(
  isOnboardingFlow: Boolean,
  onBack: (() -> Unit)?,
  onDone: () -> Unit,
  viewModel: ApiKeyViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val storedState by viewModel.storedKeyState.collectAsStateWithLifecycle()
  val storedElevenLabsState by viewModel.storedElevenLabsState.collectAsStateWithLifecycle()
  val selectedVoiceId by viewModel.selectedVoiceId.collectAsStateWithLifecycle()
  val clipboardManager = LocalClipboardManager.current
  var showKey by remember { mutableStateOf(false) }
  var showElKey by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = ObsidianNight,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.api_key_title)) },
        navigationIcon = {
          if (onBack != null) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("api_key_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
              )
            }
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
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Text(
          text = stringResource(R.string.api_key_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )

        // Current Keystore Status Card
        RiyaGlassCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint =
                when (storedState) {
                  is ApiKeyState.Saved -> SuccessMint
                  is ApiKeyState.Missing -> WarningAmber
                  is ApiKeyState.StorageError -> ErrorCoral
                },
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              when (val s = storedState) {
                is ApiKeyState.Missing -> {
                  Text(
                    text = stringResource(R.string.api_key_status_missing),
                    style = MaterialTheme.typography.titleMedium,
                  )
                }
                is ApiKeyState.Saved -> {
                  Text(
                    text =
                      if (s.isVerified) "Verified Key Stored"
                      else stringResource(R.string.api_key_status_saved),
                    style = MaterialTheme.typography.titleMedium,
                    color = SuccessMint,
                  )
                  Text(
                    text = s.maskedKey,
                    style = MaterialTheme.typography.labelMedium,
                    color = RoseGoldWarm,
                  )
                }
                is ApiKeyState.StorageError -> {
                  Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorCoral,
                  )
                }
              }
            }
            if (storedState is ApiKeyState.Saved) {
              IconButton(
                onClick = viewModel::clearKey,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_api_key_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.DeleteOutline,
                  contentDescription = stringResource(R.string.api_key_clear),
                  tint = ErrorCoral,
                )
              }
            }
          }
        }

        // Input Field
        OutlinedTextField(
          value = uiState.inputKey,
          onValueChange = viewModel::onKeyInputChanged,
          label = { Text(stringResource(R.string.api_key_field_label)) },
          placeholder = { Text(stringResource(R.string.api_key_field_placeholder)) },
          singleLine = true,
          visualTransformation =
            if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
          leadingIcon = { Icon(imageVector = Icons.Rounded.Key, contentDescription = null) },
          trailingIcon = {
            Row {
              IconButton(
                onClick = {
                  val pasted = clipboardManager.getText()?.text.orEmpty().trim()
                  if (pasted.isNotEmpty()) viewModel.onKeyInputChanged(pasted)
                },
                modifier = Modifier.testTag("paste_api_key_button"),
              ) {
                Icon(imageVector = Icons.Rounded.ContentPaste, contentDescription = "Paste key")
              }
              IconButton(
                onClick = { showKey = !showKey },
                modifier = Modifier.testTag("toggle_api_key_visibility"),
              ) {
                Icon(
                  imageVector =
                    if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = if (showKey) "Hide key" else "Show key",
                )
              }
            }
          },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = SakuraRose,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedLabelColor = SakuraRose,
            ),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
        )

        // Action Feedback Banner
        when (val status = uiState.actionStatus) {
          is ApiKeyActionStatus.VerifiedSuccess -> {
            StatusBanner(icon = Icons.Rounded.CheckCircle, color = SuccessMint, text = status.message)
          }
          is ApiKeyActionStatus.SavedLocalOnly -> {
            StatusBanner(icon = Icons.Rounded.CheckCircle, color = AmethystGlow, text = status.message)
          }
          is ApiKeyActionStatus.Error -> {
            StatusBanner(icon = Icons.Rounded.ErrorOutline, color = ErrorCoral, text = status.message)
          }
          else -> Unit
        }

        val isVerifying = uiState.actionStatus is ApiKeyActionStatus.Verifying

        Button(
          onClick = { viewModel.verifyAndSave(onDone, isOnboardingFlow) },
          enabled = !isVerifying,
          shape = MaterialTheme.shapes.large,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = SakuraRose,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier =
            Modifier.fillMaxWidth().height(54.dp).testTag("verify_save_api_key_button"),
        ) {
          if (isVerifying) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Verifying with Grok...")
          } else {
            Text(
              text = stringResource(R.string.api_key_verify_save),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }

        OutlinedButton(
          onClick = { viewModel.saveLocallyWithoutVerify(onDone, isOnboardingFlow) },
          enabled = !isVerifying,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_local_api_key_button"),
        ) {
          Text(stringResource(R.string.api_key_save_without_verify))
        }

        // Help Guide
        RiyaGlassCard {
          Text(
            text = stringResource(R.string.api_key_how_to_title),
            style = MaterialTheme.typography.titleMedium,
            color = RoseGoldWarm,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.api_key_how_to_steps),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }

        // ElevenLabs Cute Girl Voice Card (Optional Ultra-Sweet Real Voice)
        RiyaGlassCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.RecordVoiceOver,
              contentDescription = null,
              tint = SakuraRose,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "ElevenLabs Cute Girl Voice (Optional)",
                style = MaterialTheme.typography.titleMedium,
                color = RoseGoldWarm,
              )
              val elStatus =
                when (val s = storedElevenLabsState) {
                  is ApiKeyState.Saved -> "Connected • ${s.maskedKey}"
                  is ApiKeyState.Missing -> "Not set (using built-in sweet Android TTS)"
                  is ApiKeyState.StorageError -> s.message
                }
              Text(text = elStatus, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (storedElevenLabsState is ApiKeyState.Saved) {
              IconButton(
                onClick = viewModel::clearElevenLabsKey,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_elevenlabs_key_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.DeleteOutline,
                  contentDescription = "Remove ElevenLabs key",
                  tint = ErrorCoral,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = uiState.elevenLabsInputKey,
            onValueChange = viewModel::onElevenLabsInputChanged,
            label = { Text("ElevenLabs API Key") },
            placeholder = { Text("sk_...") },
            singleLine = true,
            visualTransformation =
              if (showElKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              Row {
                IconButton(
                  onClick = {
                    val pasted = clipboardManager.getText()?.text.orEmpty().trim()
                    if (pasted.isNotEmpty()) viewModel.onElevenLabsInputChanged(pasted)
                  },
                ) {
                  Icon(imageVector = Icons.Rounded.ContentPaste, contentDescription = "Paste ElevenLabs key")
                }
                IconButton(onClick = { showElKey = !showElKey }) {
                  Icon(
                    imageVector = if (showElKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Toggle ElevenLabs key visibility",
                  )
                }
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("elevenlabs_key_input"),
          )

          Spacer(modifier = Modifier.height(10.dp))

          when (val elStatus = uiState.elevenLabsActionStatus) {
            is ApiKeyActionStatus.VerifiedSuccess -> {
              Text(elStatus.message, style = MaterialTheme.typography.bodyMedium, color = SuccessMint)
              Spacer(modifier = Modifier.height(6.dp))
            }
            is ApiKeyActionStatus.SavedLocalOnly -> {
              Text(elStatus.message, style = MaterialTheme.typography.bodyMedium, color = AmethystGlow)
              Spacer(modifier = Modifier.height(6.dp))
            }
            is ApiKeyActionStatus.Error -> {
              Text(elStatus.message, style = MaterialTheme.typography.bodyMedium, color = ErrorCoral)
              Spacer(modifier = Modifier.height(6.dp))
            }
            else -> Unit
          }

          val isVerifyingEl = uiState.elevenLabsActionStatus is ApiKeyActionStatus.Verifying
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
              onClick = viewModel::verifyAndSaveElevenLabs,
              enabled = !isVerifyingEl,
              modifier = Modifier.weight(1f).testTag("verify_elevenlabs_button"),
            ) {
              Text(if (isVerifyingEl) "Checking..." else "Save ElevenLabs Key")
            }
            OutlinedButton(
              onClick = viewModel::testVoiceSample,
              modifier = Modifier.testTag("test_voice_button"),
            ) {
              Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Test Voice")
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text("Choose Sweet Girl Voice Preset:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            RIYA_ELEVENLABS_VOICES.forEach { preset ->
              FilterChip(
                selected = selectedVoiceId == preset.id,
                onClick = { viewModel.selectVoicePreset(preset.id) },
                label = { Text(preset.label) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = uiState.customVoiceIdDraft,
              onValueChange = viewModel::onCustomVoiceIdChanged,
              label = { Text("Custom Voice ID (Optional)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = viewModel::saveCustomVoiceId) {
              Text("Apply")
            }
          }
        }

        if (isOnboardingFlow) {
          TextButton(
            onClick = { viewModel.completeOnboardingWithoutKey(onDone) },
            modifier =
              Modifier.align(Alignment.CenterHorizontally).testTag("skip_api_key_button"),
          ) {
            Text(
              text =
                if (storedState is ApiKeyState.Saved) "Continue to Riya Home"
                else stringResource(R.string.api_key_skip_for_now),
              color = TextSecondary,
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun StatusBanner(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  text: String,
) {
  RiyaGlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
      )
    }
  }
}
