package com.example.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PreferencesRepository
import com.example.domain.model.PreferredLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
  val userName: String = "",
  val selectedLanguage: PreferredLanguage = PreferredLanguage.BANGLISH,
  val isSaving: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(private val preferencesRepository: PreferencesRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val existing = preferencesRepository.settingsFlow.first()
      _uiState.update {
        it.copy(
          userName = existing.userName.ifBlank { com.example.core.config.RiyaIdentity.CREATOR_NAME },
          selectedLanguage = existing.preferredLanguage,
        )
      }
    }
  }

  fun onUserNameChanged(name: String) {
    _uiState.update { it.copy(userName = name) }
  }

  fun onLanguageSelected(language: PreferredLanguage) {
    _uiState.update { it.copy(selectedLanguage = language) }
  }

  fun saveAndContinue(onSaved: () -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true) }
      val state = _uiState.value
      preferencesRepository.updateUserName(state.userName)
      preferencesRepository.updateLanguage(state.selectedLanguage)
      _uiState.update { it.copy(isSaving = false) }
      onSaved()
    }
  }
}
