package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.apikey.ApiKeyScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.permissions.PermissionsScreen
import com.example.ui.screens.settings.SettingsScreen

@Composable
fun RiyaNavHost(
  startDestination: RiyaRoute,
  navController: NavHostController = rememberNavController(),
) {
  NavHost(
    navController = navController,
    startDestination = startDestination,
    enterTransition = { fadeIn() },
    exitTransition = { fadeOut() },
  ) {
    composable<RiyaRoute.Onboarding> {
      OnboardingScreen(
        onContinue = {
          navController.navigate(RiyaRoute.Permissions(isOnboardingFlow = true))
        }
      )
    }

    composable<RiyaRoute.Permissions> { backStackEntry ->
      val route = backStackEntry.toRoute<RiyaRoute.Permissions>()
      PermissionsScreen(
        isOnboardingFlow = route.isOnboardingFlow,
        onBack = { navController.popBackStack() },
        onDone = {
          if (route.isOnboardingFlow) {
            navController.navigate(RiyaRoute.ApiKeySetup(isOnboardingFlow = true))
          } else {
            navController.popBackStack()
          }
        },
      )
    }

    composable<RiyaRoute.ApiKeySetup> { backStackEntry ->
      val route = backStackEntry.toRoute<RiyaRoute.ApiKeySetup>()
      ApiKeyScreen(
        isOnboardingFlow = route.isOnboardingFlow,
        onBack = { navController.popBackStack() },
        onDone = {
          if (route.isOnboardingFlow) {
            navController.navigate(RiyaRoute.Home) {
              popUpTo(RiyaRoute.Onboarding) { inclusive = true }
            }
          } else {
            navController.popBackStack()
          }
        },
      )
    }

    composable<RiyaRoute.Home> {
      HomeScreen(
        onOpenPermissions = {
          navController.navigate(RiyaRoute.Permissions(isOnboardingFlow = false))
        },
        onOpenApiKey = {
          navController.navigate(RiyaRoute.ApiKeySetup(isOnboardingFlow = false))
        },
        onOpenSettings = { navController.navigate(RiyaRoute.Settings) },
      )
    }

    composable<RiyaRoute.Settings> {
      SettingsScreen(
        onBack = { navController.popBackStack() },
        onOpenApiKey = {
          navController.navigate(RiyaRoute.ApiKeySetup(isOnboardingFlow = false))
        },
        onOpenPermissions = {
          navController.navigate(RiyaRoute.Permissions(isOnboardingFlow = false))
        },
        onRerunOnboarding = {
          navController.navigate(RiyaRoute.Onboarding) {
            popUpTo(RiyaRoute.Home) { inclusive = true }
          }
        },
      )
    }
  }
}
