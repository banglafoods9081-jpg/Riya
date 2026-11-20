package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface RiyaRoute {
  @Serializable data object Onboarding : RiyaRoute

  @Serializable data class Permissions(val isOnboardingFlow: Boolean = true) : RiyaRoute

  @Serializable data class ApiKeySetup(val isOnboardingFlow: Boolean = true) : RiyaRoute

  @Serializable data object Home : RiyaRoute

  @Serializable data object Chat : RiyaRoute

  @Serializable data object Memory : RiyaRoute

  @Serializable data object Analysis : RiyaRoute

  @Serializable data object Logs : RiyaRoute

  @Serializable data object Settings : RiyaRoute
}
