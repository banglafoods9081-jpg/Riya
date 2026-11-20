package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceStatus {
  IDLE,
  LISTENING,
  WAKE_STANDBY,
  THINKING,
  SPEAKING,
  MUTED,
}

data class VoiceEngineUiState(
  val isServiceRunning: Boolean = false,
  val isMuted: Boolean = false,
  val status: VoiceStatus = VoiceStatus.IDLE,
  val amplitude: Float = 0f,
  val liveTranscript: String = "",
  val lastRiyaReply: String = "",
  val currentMood: String = "warm",
  val statusHint: String = "Tap the orb to talk with Riya",
)

@Singleton
class VoiceStateHolder @Inject constructor() {

  private val _state = MutableStateFlow(VoiceEngineUiState())
  val state: StateFlow<VoiceEngineUiState> = _state.asStateFlow()

  fun update(transform: (VoiceEngineUiState) -> VoiceEngineUiState) {
    _state.update(transform)
  }
}
