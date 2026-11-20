package com.example.ui.screens.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.security.ApiKeyState
import com.example.data.repository.KeyVerificationResult
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import com.example.voice.RiyaTtsManager
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
  val elevenLabsInputKey: String = "",
  val elevenLabsActionStatus: ApiKeyActionStatus = ApiKeyActionStatus.Idle,
  val customVoiceIdDraft: String = "",
)

@HiltViewModel
class ApiKeyViewModel
@Inject
constructor(
  private val secretRepository: SecretRepository,
  private val preferencesRepository: PreferencesRepository,
  private val ttsManager: RiyaTtsManager,
) : ViewModel() {

  val storedKeyState: StateFlow<ApiKeyState> =
    secretRepository.apiKeyState.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ApiKeyState.Missing,
    )

  val storedElevenLabsState: StateFlow<ApiKeyState> =
    secretRepository.elevenLabsKeyState.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ApiKeyState.Missing,
    )

  val selectedVoiceId: StateFlow<String> =
    secretRepository.elevenLabsVoiceId.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = secretRepository.getStoredElevenLabsVoiceId(),
    )

  private val _uiState = MutableStateFlow(ApiKeyScreenState())
  val uiState: StateFlow<ApiKeyScreenState> = _uiState.asStateFlow()

  fun onKeyInputChanged(value: String) {
    _uiState.update { it.copy(inputKey = value, actionStatus = ApiKeyActionStatus.Idle) }
  }

  fun onElevenLabsInputChanged(value: String) {
    _uiState.update {
      it.copy(elevenLabsInputKey = value, elevenLabsActionStatus = ApiKeyActionStatus.Idle)
    }
  }

  fun onCustomVoiceIdChanged(value: String) {
    _uiState.update { it.copy(customVoiceIdDraft = value) }
  }

  fun selectVoicePreset(voiceId: String) {
    viewModelScope.launch {
      secretRepository.setElevenLabsVoiceId(voiceId)
    }
  }

  fun saveCustomVoiceId() {
    val clean = _uiState.value.customVoiceIdDraft.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      secretRepository.setElevenLabsVoiceId(clean)
      _uiState.update {
        it.copy(
          customVoiceIdDraft = "",
          elevenLabsActionStatus = ApiKeyActionStatus.SavedLocalOnly("Custom Voice ID updated!"),
        )
      }
    }
  }

  fun verifyAndSave(onCompleteIfOnboarding: () -> Unit, isOnboardingFlow: Boolean) {
    val candidate = _uiState.value.inputKey.trim()
    if (candidate.isEmpty()) {
      _uiState.update {
        it.copy(actionStatus = ApiKeyActionStatus.Error("Please enter your Grok (or Groq) API key first."))
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
          val isGroq = candidate.startsWith("gsk_", ignoreCase = true)
          _uiState.update {
            it.copy(
              inputKey = "",
              actionStatus =
                ApiKeyActionStatus.VerifiedSuccess(
                  if (isGroq) "Verified! Riya's fast Groq brain is ready."
                  else "Verified! Riya's Grok AI brain is ready."
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

  fun verifyAndSaveElevenLabs() {
    val candidate = _uiState.value.elevenLabsInputKey.trim()
    if (candidate.isEmpty()) {
      _uiState.update {
        it.copy(
          elevenLabsActionStatus =
            ApiKeyActionStatus.Error("Paste your ElevenLabs API key first.")
        )
      }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(elevenLabsActionStatus = ApiKeyActionStatus.Verifying) }
      when (val res = secretRepository.verifyAndSaveElevenLabsKey(candidate)) {
        is KeyVerificationResult.Valid -> {
          _uiState.update {
            it.copy(
              elevenLabsInputKey = "",
              elevenLabsActionStatus =
                ApiKeyActionStatus.VerifiedSuccess(
                  "ElevenLabs cute voice connected! Tap Test Voice to hear Riya."
                ),
            )
          }
        }
        is KeyVerificationResult.Invalid -> {
          _uiState.update { it.copy(elevenLabsActionStatus = ApiKeyActionStatus.Error(res.reason)) }
        }
      }
    }
  }

  fun clearElevenLabsKey() {
    viewModelScope.launch {
      secretRepository.clearElevenLabsKey()
      _uiState.update {
        it.copy(elevenLabsInputKey = "", elevenLabsActionStatus = ApiKeyActionStatus.Idle)
      }
    }
  }

  fun testVoiceSample() {
    ttsManager.speak("Hii Rohan jaan! Ami tomar Riya. Kemon acho shona?")
  }

  fun completeOnboardingWithoutKey(onDone: () -> Unit) {
    viewModelScope.launch {
      preferencesRepository.setOnboardingCompleted(true)
      onDone()
    }
  }
}
