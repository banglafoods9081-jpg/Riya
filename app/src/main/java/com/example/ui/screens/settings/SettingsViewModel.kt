package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PreferencesRepository
import com.example.domain.model.PreferredLanguage
import com.example.domain.model.RiyaSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(private val preferencesRepository: PreferencesRepository) : ViewModel() {

  val settings: StateFlow<RiyaSettings> =
    preferencesRepository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = RiyaSettings(),
    )

  fun updateUserName(name: String) {
    viewModelScope.launch { preferencesRepository.updateUserName(name) }
  }

  fun updateLanguage(language: PreferredLanguage) {
    viewModelScope.launch { preferencesRepository.updateLanguage(language) }
  }

  fun updatePersonaIntensity(intensity: Float) {
    viewModelScope.launch { preferencesRepository.updatePersonaIntensity(intensity) }
  }

  fun updateVoiceTuning(speed: Float, pitch: Float) {
    viewModelScope.launch { preferencesRepository.updateVoiceTuning(speed, pitch) }
  }

  fun setWakeWordEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setWakeWordEnabled(enabled) }
  }

  fun rerunOnboarding(onNavigate: () -> Unit) {
    viewModelScope.launch {
      preferencesRepository.setOnboardingCompleted(false)
      onNavigate()
    }
  }
}
