package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface RiyaRoute {
  @Serializable data object Onboarding : RiyaRoute

  @Serializable data class Permissions(val isOnboardingFlow: Boolean = true) : RiyaRoute

  @Serializable data class ApiKeySetup(val isOnboardingFlow: Boolean = true) : RiyaRoute

  @Serializable data object Home : RiyaRoute

  @Serializable data object Settings : RiyaRoute
}
