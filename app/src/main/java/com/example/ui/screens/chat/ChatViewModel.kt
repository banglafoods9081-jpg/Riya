package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.repository.MemoryRepository
import com.example.domain.agent.RiyaBrain
import com.example.voice.RiyaTtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
  val messages: List<ChatMessageEntity> = emptyList(),
  val isThinking: Boolean = false,
  val draft: String = "",
)

@HiltViewModel
class ChatViewModel
@Inject
constructor(
  private val memoryRepository: MemoryRepository,
  private val riyaBrain: RiyaBrain,
  private val ttsManager: RiyaTtsManager,
) : ViewModel() {

  private val draftFlow = MutableStateFlow("")

  val uiState: StateFlow<ChatUiState> =
    combine(memoryRepository.recentMessages, riyaBrain.isThinking, draftFlow) { msgs, thinking, draft ->
      ChatUiState(messages = msgs, isThinking = thinking, draft = draft)
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ChatUiState(),
    )

  fun onDraftChange(value: String) {
    draftFlow.value = value
  }

  fun sendMessage(speakReply: Boolean = true) {
    val text = draftFlow.value.trim()
    if (text.isEmpty()) return
    draftFlow.value = ""
    viewModelScope.launch {
      if (ttsManager.isSpeaking.value) ttsManager.stop()
      val reply = riyaBrain.processUserMessage(text)
      if (speakReply && !reply.isEmergencyStop) {
        ttsManager.speak(reply.text)
      }
    }
  }

  fun clearChat() {
    viewModelScope.launch { memoryRepository.clearChatOnly() }
  }
}
