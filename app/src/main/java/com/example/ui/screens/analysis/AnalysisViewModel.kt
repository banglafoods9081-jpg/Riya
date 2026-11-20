package com.example.ui.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analysis.PhoneAnalyzer
import com.example.analysis.PhoneHealthReport
import com.example.data.local.NotificationRecordEntity
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

data class AnalysisUiState(
  val report: PhoneHealthReport? = null,
  val isAnalyzing: Boolean = false,
  val notifications: List<NotificationRecordEntity> = emptyList(),
  val spokenSummary: String? = null,
)

@HiltViewModel
class AnalysisViewModel
@Inject
constructor(
  private val phoneAnalyzer: PhoneAnalyzer,
  private val memoryRepository: MemoryRepository,
  private val riyaBrain: RiyaBrain,
  private val ttsManager: RiyaTtsManager,
) : ViewModel() {

  private val reportFlow = MutableStateFlow<PhoneHealthReport?>(null)
  private val loadingFlow = MutableStateFlow(false)
  private val spokenFlow = MutableStateFlow<String?>(null)

  val uiState: StateFlow<AnalysisUiState> =
    combine(reportFlow, loadingFlow, memoryRepository.notifications, spokenFlow) { rep, loading, notifs, spoken ->
      AnalysisUiState(
        report = rep,
        isAnalyzing = loading,
        notifications = notifs,
        spokenSummary = spoken,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = AnalysisUiState(),
    )

  init {
    refreshAnalysis()
  }

  fun refreshAnalysis() {
    viewModelScope.launch {
      loadingFlow.value = true
      reportFlow.value = runCatching { phoneAnalyzer.analyze() }.getOrNull()
      loadingFlow.value = false
    }
  }

  fun speakFullReport() {
    viewModelScope.launch {
      loadingFlow.value = true
      val reply = riyaBrain.processUserMessage("Amar phone analyse koro ar tips dao")
      spokenFlow.value = reply.text
      loadingFlow.value = false
      ttsManager.speak(reply.text)
    }
  }
}
