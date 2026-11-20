package com.example.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MemoryFactEntity
import com.example.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
  val facts: List<MemoryFactEntity> = emptyList(),
  val newFactDraft: String = "",
)

@HiltViewModel
class MemoryViewModel
@Inject
constructor(private val memoryRepository: MemoryRepository) : ViewModel() {

  private val draftFlow = MutableStateFlow("")

  init {
    viewModelScope.launch { memoryRepository.ensurePermanentFactsSeeded() }
  }

  val uiState: StateFlow<MemoryUiState> =
    combine(memoryRepository.allFacts, draftFlow) { facts, draft ->
      MemoryUiState(facts = facts, newFactDraft = draft)
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = MemoryUiState(),
    )

  fun onDraftChange(value: String) {
    draftFlow.value = value
  }

  fun addMemory() {
    val text = draftFlow.value.trim()
    if (text.isEmpty()) return
    draftFlow.value = ""
    viewModelScope.launch { memoryRepository.rememberFact("Personal", text) }
  }

  fun deleteFact(id: Long) {
    viewModelScope.launch { memoryRepository.deleteDynamicFact(id) }
  }

  fun wipeDynamicMemory() {
    viewModelScope.launch { memoryRepository.clearDynamicMemoryAndChat() }
  }
}
