package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.analysis.AnalysisScreen
import com.example.ui.screens.apikey.ApiKeyScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.logs.ActionLogScreen
import com.example.ui.screens.memory.MemoryScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.permissions.PermissionsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.PlumSurface
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private data class MainTab(
  val route: RiyaRoute,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val mainTabs =
  listOf(
    MainTab(RiyaRoute.Home, "Riya", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq, "tab_home"),
    MainTab(RiyaRoute.Chat, "Chat", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_chat"),
    MainTab(RiyaRoute.Memory, "Memory", Icons.Rounded.Psychology, Icons.Outlined.Psychology, "tab_memory"),
    MainTab(RiyaRoute.Analysis, "Phone", Icons.Rounded.PhoneAndroid, Icons.Outlined.PhoneAndroid, "tab_analysis"),
    MainTab(RiyaRoute.Logs, "Logs", Icons.Rounded.History, Icons.Outlined.History, "tab_logs"),
  )

@Composable
fun RiyaNavHost(
  startDestination: RiyaRoute,
  navController: NavHostController = rememberNavController(),
) {
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = navBackStackEntry?.destination
  val showBottomBar =
    currentDest != null &&
      mainTabs.any { tab -> currentDest.hasRoute(tab.route::class) }

  Scaffold(
    containerColor = ObsidianNight,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (showBottomBar) {
        NavigationBar(containerColor = PlumSurface) {
          mainTabs.forEach { tab ->
            val selected = currentDest?.hasRoute(tab.route::class) == true
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                  contentDescription = tab.label,
                )
              },
              label = { Text(tab.label) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = SakuraRose,
                  selectedTextColor = SakuraRose,
                  indicatorColor = RoseContainer,
                  unselectedIconColor = TextMuted,
                  unselectedTextColor = TextMuted,
                ),
              modifier = Modifier.testTag(tab.testTag),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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

        composable<RiyaRoute.Chat> { ChatScreen() }

        composable<RiyaRoute.Memory> { MemoryScreen() }

        composable<RiyaRoute.Analysis> { AnalysisScreen() }

        composable<RiyaRoute.Logs> { ActionLogScreen() }

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
  }
}
