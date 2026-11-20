package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.permissions.PermissionChecker
import com.example.core.permissions.RiyaPermission
import com.example.core.security.ApiKeyState
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import com.example.domain.model.RiyaSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
  val settings: RiyaSettings = RiyaSettings(),
  val apiKeyState: ApiKeyState = ApiKeyState.Missing,
  val grantedPermissionsCount: Int = 0,
  val totalPermissionsCount: Int = RiyaPermission.entries.size,
  val micGranted: Boolean = false,
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val preferencesRepository: PreferencesRepository,
  secretRepository: SecretRepository,
  private val permissionChecker: PermissionChecker,
) : ViewModel() {

  val uiState: StateFlow<HomeUiState> =
    combine(
      preferencesRepository.settingsFlow,
      secretRepository.apiKeyState,
      permissionChecker.statuses,
    ) { settings, keyState, permissions ->
      HomeUiState(
        settings = settings,
        apiKeyState = keyState,
        grantedPermissionsCount = permissions.count { it.isGranted },
        totalPermissionsCount = permissions.size.coerceAtLeast(RiyaPermission.entries.size),
        micGranted = permissions.any { it.permission == RiyaPermission.MICROPHONE && it.isGranted },
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )

  fun refreshPermissions() {
    permissionChecker.refresh()
  }

  fun toggleMasterSwitch(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setMasterEnabled(enabled) }
  }
}
