package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.repository.PreferencesRepository
import com.example.ui.navigation.RiyaNavHost
import com.example.ui.navigation.RiyaRoute
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.RiyaTheme
import com.example.ui.theme.SakuraRose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var preferencesRepository: PreferencesRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )

    setContent {
      RiyaTheme {
        val startRoute by
          produceState<RiyaRoute?>(initialValue = null) {
            val settings = preferencesRepository.settingsFlow.first()
            value = if (settings.onboardingCompleted) RiyaRoute.Home else RiyaRoute.Onboarding
          }

        val resolvedStart = startRoute
        if (resolvedStart == null) {
          Box(
            modifier = Modifier.fillMaxSize().background(ObsidianNight),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = SakuraRose)
          }
        } else {
          RiyaNavHost(startDestination = resolvedStart)
        }
      }
    }
  }
}
