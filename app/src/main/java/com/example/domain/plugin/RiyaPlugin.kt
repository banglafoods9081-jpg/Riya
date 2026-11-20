package com.example.domain.plugin

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

interface RiyaPlugin {
  val id: String
  val title: String
  val description: String
  val triggerKeywords: List<String>

  fun canHandle(userInput: String): Boolean =
    triggerKeywords.any { userInput.lowercase().contains(it) }

  suspend fun provideContextHint(userInput: String): String?
}

class StudyHelperPlugin @Inject constructor() : RiyaPlugin {
  override val id = "study_helper"
  override val title = "Class 11 Study Helper"
  override val description = "Helps Rohan with Class 11 subjects & Saddam sir's tuition prep."
  override val triggerKeywords =
    listOf("porashona", "study", "class 11", "saddam", "homework", "tuition", "exam", "math", "physics", "chemistry", "biology")

  override suspend fun provideContextHint(userInput: String): String {
    return "Study Helper Mode active for Rohan (Class 11, Saddam sir's tuition): explain concepts clearly with warm encouragement, and remind him to stay focused."
  }
}

class SmartHomeWebhookPlugin @Inject constructor() : RiyaPlugin {
  override val id = "smart_home_iot"
  override val title = "Smart Home & IoT Bridge"
  override val description = "Plugin interface ready for MQTT / webhook smart-home switches."
  override val triggerKeywords = listOf("smart home", "light off", "fan off", "webhook", "mqtt")

  override suspend fun provideContextHint(userInput: String): String {
    return "Smart Home IoT plugin slot is registered. Note: No MQTT broker URL or smart-home webhook is configured yet; inform Rohan honestly if he asks to toggle room lights/IoT devices."
  }
}

@Singleton
class PluginRegistry @Inject constructor(private val plugins: Set<@JvmSuppressWildcards RiyaPlugin>) {
  fun allPlugins(): List<RiyaPlugin> = plugins.toList()

  suspend fun collectHintsForInput(userInput: String): List<String> {
    return plugins.filter { it.canHandle(userInput) }.mapNotNull { it.provideContextHint(userInput) }
  }
}

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

  @Provides
  @IntoSet
  fun provideStudyHelperPlugin(): RiyaPlugin = StudyHelperPlugin()

  @Provides
  @IntoSet
  fun provideSmartHomeWebhookPlugin(): RiyaPlugin = SmartHomeWebhookPlugin()
}
