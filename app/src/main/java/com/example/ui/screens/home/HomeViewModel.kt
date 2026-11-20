package com.example.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analysis.PhoneAnalyzer
import com.example.analysis.RagStatus
import com.example.control.AccessibilityBridge
import com.example.control.PendingConfirmation
import com.example.control.RiyaToolExecutor
import com.example.core.permissions.PermissionChecker
import com.example.core.permissions.RiyaPermission
import com.example.core.security.ApiKeyState
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import com.example.domain.agent.RiyaBrain
import com.example.domain.model.RiyaSettings
import com.example.service.RiyaOverlayService
import com.example.service.RiyaVoiceService
import com.example.voice.RiyaTtsManager
import com.example.voice.VoiceEngineUiState
import com.example.voice.VoiceStateHolder
import com.example.voice.VoiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
  val settings: RiyaSettings = RiyaSettings(),
  val apiKeyState: ApiKeyState = ApiKeyState.Missing,
  val grantedPermissionsCount: Int = 0,
  val totalPermissionsCount: Int = RiyaPermission.entries.size,
  val micGranted: Boolean = false,
  val accessibilityConnected: Boolean = false,
  val overlayGranted: Boolean = false,
  val voiceState: VoiceEngineUiState = VoiceEngineUiState(),
  val pendingConfirmation: PendingConfirmation? = null,
  val ragStatus: RagStatus? = null,
  val textDraft: String = "",
  val overlayEnabled: Boolean = false,
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val preferencesRepository: PreferencesRepository,
  secretRepository: SecretRepository,
  private val permissionChecker: PermissionChecker,
  private val accessibilityBridge: AccessibilityBridge,
  private val voiceStateHolder: VoiceStateHolder,
  private val ttsManager: RiyaTtsManager,
  private val riyaBrain: RiyaBrain,
  private val toolExecutor: RiyaToolExecutor,
  private val phoneAnalyzer: PhoneAnalyzer,
) : ViewModel() {

  private val textDraftFlow = MutableStateFlow("")
  private val overlayToggleFlow = MutableStateFlow(false)

  private val baseFlow =
    combine(
      preferencesRepository.settingsFlow,
      secretRepository.apiKeyState,
      permissionChecker.statuses,
      accessibilityBridge.isConnected,
      voiceStateHolder.state,
    ) { settings, keyState, permissions, accConnected, voice ->
      HomeBaseSnapshot(
        settings = settings,
        apiKeyState = keyState,
        grantedCount = permissions.count { it.isGranted },
        totalCount = permissions.size.coerceAtLeast(RiyaPermission.entries.size),
        micGranted = permissions.any { it.permission == RiyaPermission.MICROPHONE && it.isGranted },
        overlayGranted = permissions.any { it.permission == RiyaPermission.OVERLAY && it.isGranted },
        accessibilityConnected = accConnected,
        voiceState = voice,
      )
    }

  val uiState: StateFlow<HomeUiState> =
    combine(
      baseFlow,
      toolExecutor.pendingConfirmation,
      riyaBrain.latestRagStatus,
      textDraftFlow,
      overlayToggleFlow,
    ) { base, pending, rag, draft, overlayOn ->
      HomeUiState(
        settings = base.settings,
        apiKeyState = base.apiKeyState,
        grantedPermissionsCount = base.grantedCount,
        totalPermissionsCount = base.totalCount,
        micGranted = base.micGranted,
        accessibilityConnected = base.accessibilityConnected,
        overlayGranted = base.overlayGranted,
        voiceState = base.voiceState,
        pendingConfirmation = pending,
        ragStatus = rag,
        textDraft = draft,
        overlayEnabled = overlayOn,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )

  init {
    viewModelScope.launch {
      runCatching { phoneAnalyzer.analyze() }
    }
  }

  fun refreshPermissions() {
    permissionChecker.refresh()
  }

  fun toggleMasterSwitch(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setMasterEnabled(enabled)
      if (!enabled) {
        ttsManager.stop()
        RiyaVoiceService.start(context, RiyaVoiceService.ACTION_STOP)
      }
    }
  }

  fun onOrbTapped() {
    if (!uiState.value.settings.masterEnabled) {
      toggleMasterSwitch(true)
    }
    if (ttsManager.isSpeaking.value) {
      ttsManager.stop()
    }
    if (uiState.value.micGranted) {
      RiyaVoiceService.start(context, RiyaVoiceService.ACTION_TAP_TO_TALK)
    } else {
      voiceStateHolder.update {
        it.copy(statusHint = "Microphone permission allow koro, ba niche type koro jaan!")
      }
    }
  }

  fun toggleBackgroundVoiceService() {
    if (uiState.value.voiceState.isServiceRunning) {
      RiyaVoiceService.start(context, RiyaVoiceService.ACTION_STOP)
    } else {
      RiyaVoiceService.start(context, RiyaVoiceService.ACTION_START)
    }
  }

  fun toggleOverlayBubble() {
    if (!uiState.value.overlayGranted) return
    val next = !overlayToggleFlow.value
    overlayToggleFlow.value = next
    RiyaOverlayService.toggle(context, next)
  }

  fun onTextDraftChanged(text: String) {
    textDraftFlow.value = text
  }

  fun sendTextCommand() {
    val msg = textDraftFlow.value.trim()
    if (msg.isEmpty()) return
    textDraftFlow.value = ""
    viewModelScope.launch {
      if (ttsManager.isSpeaking.value) ttsManager.stop()
      voiceStateHolder.update {
        it.copy(liveTranscript = msg, status = VoiceStatus.THINKING, statusHint = "Bhabchi jaan...")
      }
      val reply = riyaBrain.processUserMessage(msg)
      voiceStateHolder.update {
        it.copy(
          lastRiyaReply = reply.text,
          currentMood = reply.mood,
          status = if (reply.isEmergencyStop) VoiceStatus.IDLE else VoiceStatus.SPEAKING,
          statusHint = if (reply.isEmergencyStop) "Stopped" else "Riya bolche...",
        )
      }
      if (reply.isEmergencyStop) {
        ttsManager.stop()
      } else {
        ttsManager.speak(reply.text) {
          voiceStateHolder.update { state ->
            state.copy(
              status = if (state.isServiceRunning) VoiceStatus.WAKE_STANDBY else VoiceStatus.IDLE,
              statusHint = if (state.isServiceRunning) "Listening for Hey Riya..." else "Tap the orb to talk with Riya",
            )
          }
        }
      }
    }
  }

  fun confirmPending() {
    viewModelScope.launch {
      val result = toolExecutor.confirmPendingAction()
      voiceStateHolder.update { it.copy(lastRiyaReply = result) }
      ttsManager.speak(result)
    }
  }

  fun cancelPending() {
    viewModelScope.launch {
      val result = toolExecutor.cancelPendingAction()
      voiceStateHolder.update { it.copy(lastRiyaReply = result) }
      ttsManager.speak(result)
    }
  }

  private data class HomeBaseSnapshot(
    val settings: RiyaSettings,
    val apiKeyState: ApiKeyState,
    val grantedCount: Int,
    val totalCount: Int,
    val micGranted: Boolean,
    val overlayGranted: Boolean,
    val accessibilityConnected: Boolean,
    val voiceState: VoiceEngineUiState,
  )
}
