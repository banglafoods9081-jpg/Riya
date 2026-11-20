package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PreferredLanguage(val displayName: String, val nativeHint: String) {
  AUTO("Auto Match", "Bengali / Banglish / Hindi / English"),
  BENGALI("Bengali", "বাংলা"),
  BANGLISH("Banglish", "Bangla in English letters"),
  HINDI("Hindi", "हिन्दी / Hinglish"),
  ENGLISH("English", "English"),
}

data class RiyaSettings(
  val userName: String = "",
  val preferredLanguage: PreferredLanguage = PreferredLanguage.BANGLISH,
  val personaIntensity: Float = 0.78f,
  val ttsSpeed: Float = 1.0f,
  val ttsPitch: Float = 1.08f,
  val wakeWordEnabled: Boolean = true,
  val masterEnabled: Boolean = true,
  val onboardingCompleted: Boolean = false,
)
