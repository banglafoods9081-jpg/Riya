package com.example.ui.screens.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.PreferredLanguage
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
  onContinue: () -> Unit,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            .navigationBarsPadding(),
      ) {
        // Atmospheric Hero Header
        Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
          Image(
            painter = painterResource(id = R.drawable.img_riya_onboarding_hero),
            contentDescription = "Riya luminous aura banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ObsidianNight.copy(alpha = 0.72f), ObsidianNight)
                  )
                )
          )
          Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 16.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = SakuraRose,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "ROMANTIC AI COMPANION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.onboarding_greeting_title),
              style = MaterialTheme.typography.displayLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
          }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
          Text(
            text = stringResource(R.string.onboarding_greeting_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
          )

          Spacer(modifier = Modifier.height(24.dp))

          // Name Field
          OutlinedTextField(
            value = state.userName,
            onValueChange = viewModel::onUserNameChanged,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SakuraRose,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = SakuraRose,
              ),
            modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
          )

          Spacer(modifier = Modifier.height(22.dp))

          // Language Preference Selector
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Translate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.onboarding_language_label),
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PreferredLanguage.entries.forEach { lang ->
              val selected = state.selectedLanguage == lang
              FilterChip(
                selected = selected,
                onClick = { viewModel.onLanguageSelected(lang) },
                label = { Text("${lang.displayName} (${lang.nativeHint})") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                modifier =
                  Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lang_chip_${lang.name.lowercase()}"),
              )
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          // Safety & Privacy Promise Card
          RiyaGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = SuccessMint,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = stringResource(R.string.onboarding_safety_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = stringResource(R.string.onboarding_safety_body),
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }

          Spacer(modifier = Modifier.height(28.dp))

          Button(
            onClick = { viewModel.saveAndContinue(onContinue) },
            enabled = !state.isSaving,
            shape = MaterialTheme.shapes.large,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = SakuraRose,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier =
              Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("onboarding_continue_button"),
          ) {
            Text(
              text = stringResource(R.string.onboarding_continue),
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
            )
          }

          Spacer(modifier = Modifier.height(32.dp))
        }
      }
    }
  }
}
