package com.example.ui.screens.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.core.permissions.PermissionChecker
import com.example.core.permissions.PermissionStatusItem
import com.example.core.permissions.RiyaPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PermissionsUiState(
  val items: List<PermissionStatusItem> = emptyList(),
  val currentStepIndex: Int = 0,
  val isChecklistMode: Boolean = false,
  val grantedCount: Int = 0,
  val totalCount: Int = RiyaPermission.entries.size,
) {
  val currentItem: PermissionStatusItem?
    get() = items.getOrNull(currentStepIndex)
}

@HiltViewModel
class PermissionsViewModel
@Inject
constructor(private val permissionChecker: PermissionChecker) : ViewModel() {

  private val _uiState = MutableStateFlow(PermissionsUiState())
  val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

  init {
    refreshPermissions()
  }

  fun refreshPermissions() {
    val updated = permissionChecker.refresh()
    _uiState.update { state ->
      state.copy(
        items = updated,
        grantedCount = updated.count { it.isGranted },
        totalCount = updated.size,
        currentStepIndex = state.currentStepIndex.coerceIn(0, (updated.size - 1).coerceAtLeast(0)),
      )
    }
  }

  fun setChecklistMode(checklist: Boolean) {
    _uiState.update { it.copy(isChecklistMode = checklist) }
  }

  fun nextStep() {
    _uiState.update { state ->
      val next = (state.currentStepIndex + 1).coerceAtMost((state.items.size - 1).coerceAtLeast(0))
      state.copy(currentStepIndex = next)
    }
  }

  fun previousStep() {
    _uiState.update { state ->
      val prev = (state.currentStepIndex - 1).coerceAtLeast(0)
      state.copy(currentStepIndex = prev)
    }
  }

  fun selectStep(index: Int) {
    _uiState.update { state ->
      state.copy(
        currentStepIndex = index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0)),
        isChecklistMode = false,
      )
    }
  }

  fun createSettingsIntent(permission: RiyaPermission): Intent =
    permissionChecker.createSettingsIntent(permission)

  fun createAppInfoIntent(): Intent = permissionChecker.createAppDetailsIntent()
}
