package com.example.ui.screens.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.security.ApiKeyState
import com.example.data.repository.KeyVerificationResult
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ApiKeyActionStatus {
  data object Idle : ApiKeyActionStatus

  data object Verifying : ApiKeyActionStatus

  data class VerifiedSuccess(val message: String) : ApiKeyActionStatus

  data class SavedLocalOnly(val message: String) : ApiKeyActionStatus

  data class Error(val message: String) : ApiKeyActionStatus
}

data class ApiKeyScreenState(
  val inputKey: String = "",
  val actionStatus: ApiKeyActionStatus = ApiKeyActionStatus.Idle,
)

@HiltViewModel
class ApiKeyViewModel
@Inject
constructor(
  private val secretRepository: SecretRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  val storedKeyState: StateFlow<ApiKeyState> =
    secretRepository.apiKeyState.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ApiKeyState.Missing,
    )

  private val _uiState = MutableStateFlow(ApiKeyScreenState())
  val uiState: StateFlow<ApiKeyScreenState> = _uiState.asStateFlow()

  fun onKeyInputChanged(value: String) {
    _uiState.update { it.copy(inputKey = value, actionStatus = ApiKeyActionStatus.Idle) }
  }

  fun verifyAndSave(onCompleteIfOnboarding: () -> Unit, isOnboardingFlow: Boolean) {
    val candidate = _uiState.value.inputKey.trim()
    if (candidate.isEmpty()) {
      _uiState.update {
        it.copy(actionStatus = ApiKeyActionStatus.Error("Please enter your Gemini API key first."))
      }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(actionStatus = ApiKeyActionStatus.Verifying) }
      when (val result = secretRepository.verifyAndSaveApiKey(candidate)) {
        is KeyVerificationResult.Valid -> {
          if (isOnboardingFlow) {
            preferencesRepository.setOnboardingCompleted(true)
          }
          _uiState.update {
            it.copy(
              inputKey = "",
              actionStatus =
                ApiKeyActionStatus.VerifiedSuccess(
                  "Verified! Riya's Gemini 2.5 Flash brain is ready."
                ),
            )
          }
          if (isOnboardingFlow) onCompleteIfOnboarding()
        }
        is KeyVerificationResult.Invalid -> {
          _uiState.update { it.copy(actionStatus = ApiKeyActionStatus.Error(result.reason)) }
        }
      }
    }
  }

  fun saveLocallyWithoutVerify(onCompleteIfOnboarding: () -> Unit, isOnboardingFlow: Boolean) {
    val candidate = _uiState.value.inputKey.trim()
    if (candidate.isEmpty()) {
      _uiState.update {
        it.copy(actionStatus = ApiKeyActionStatus.Error("Please paste an API key first."))
      }
      return
    }
    viewModelScope.launch {
      secretRepository
        .saveApiKeyLocally(candidate)
        .onSuccess {
          if (isOnboardingFlow) {
            preferencesRepository.setOnboardingCompleted(true)
          }
          _uiState.update {
            it.copy(
              inputKey = "",
              actionStatus =
                ApiKeyActionStatus.SavedLocalOnly(
                  "Saved locally in EncryptedSharedPreferences. Verify anytime when online."
                ),
            )
          }
          if (isOnboardingFlow) onCompleteIfOnboarding()
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(
              actionStatus =
                ApiKeyActionStatus.Error(error.localizedMessage ?: "Failed to save key.")
            )
          }
        }
    }
  }

  fun clearKey() {
    viewModelScope.launch {
      secretRepository.clearApiKey()
      _uiState.update {
        it.copy(
          inputKey = "",
          actionStatus = ApiKeyActionStatus.Idle,
        )
      }
    }
  }

  fun completeOnboardingWithoutKey(onDone: () -> Unit) {
    viewModelScope.launch {
      preferencesRepository.setOnboardingCompleted(true)
      onDone()
    }
  }
}
