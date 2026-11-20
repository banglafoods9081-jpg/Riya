package com.example.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ActionLogEntity
import com.example.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ActionLogViewModel
@Inject
constructor(memoryRepository: MemoryRepository) : ViewModel() {

  val logs: StateFlow<List<ActionLogEntity>> =
    memoryRepository.actionLogs.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList(),
    )
}
