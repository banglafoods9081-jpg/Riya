package com.example.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.domain.model.PreferredLanguage
import com.example.domain.model.RiyaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface PreferencesRepository {
  val settingsFlow: Flow<RiyaSettings>

  suspend fun updateUserName(name: String)

  suspend fun updateLanguage(language: PreferredLanguage)

  suspend fun updatePersonaIntensity(intensity: Float)

  suspend fun updateVoiceTuning(speed: Float, pitch: Float)

  suspend fun setWakeWordEnabled(enabled: Boolean)

  suspend fun setMasterEnabled(enabled: Boolean)

  suspend fun setOnboardingCompleted(completed: Boolean)

  suspend fun resetAllPreferences()
}

@Singleton
class PreferencesRepositoryImpl
@Inject
constructor(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

  override val settingsFlow: Flow<RiyaSettings> =
    dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          Timber.e(exception, "Error reading Riya preferences")
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        val languageName = prefs[KEY_LANGUAGE] ?: PreferredLanguage.BANGLISH.name
        val language =
          PreferredLanguage.entries.firstOrNull { it.name == languageName }
            ?: PreferredLanguage.BANGLISH

        RiyaSettings(
          userName = prefs[KEY_USER_NAME].orEmpty(),
          preferredLanguage = language,
          personaIntensity = (prefs[KEY_PERSONA_INTENSITY] ?: 0.78f).coerceIn(0f, 1f),
          ttsSpeed = (prefs[KEY_TTS_SPEED] ?: 1.0f).coerceIn(0.6f, 1.5f),
          ttsPitch = (prefs[KEY_TTS_PITCH] ?: 1.08f).coerceIn(0.75f, 1.4f),
          wakeWordEnabled = prefs[KEY_WAKE_WORD_ENABLED] ?: true,
          masterEnabled = prefs[KEY_MASTER_ENABLED] ?: true,
          onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
        )
      }

  override suspend fun updateUserName(name: String) {
    dataStore.edit { it[KEY_USER_NAME] = name.trim() }
  }

  override suspend fun updateLanguage(language: PreferredLanguage) {
    dataStore.edit { it[KEY_LANGUAGE] = language.name }
  }

  override suspend fun updatePersonaIntensity(intensity: Float) {
    dataStore.edit { it[KEY_PERSONA_INTENSITY] = intensity.coerceIn(0f, 1f) }
  }

  override suspend fun updateVoiceTuning(speed: Float, pitch: Float) {
    dataStore.edit {
      it[KEY_TTS_SPEED] = speed.coerceIn(0.6f, 1.5f)
      it[KEY_TTS_PITCH] = pitch.coerceIn(0.75f, 1.4f)
    }
  }

  override suspend fun setWakeWordEnabled(enabled: Boolean) {
    dataStore.edit { it[KEY_WAKE_WORD_ENABLED] = enabled }
  }

  override suspend fun setMasterEnabled(enabled: Boolean) {
    dataStore.edit { it[KEY_MASTER_ENABLED] = enabled }
  }

  override suspend fun setOnboardingCompleted(completed: Boolean) {
    dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
  }

  override suspend fun resetAllPreferences() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val KEY_USER_NAME = stringPreferencesKey("user_name")
    val KEY_LANGUAGE = stringPreferencesKey("preferred_language")
    val KEY_PERSONA_INTENSITY = floatPreferencesKey("persona_intensity")
    val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
    val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
    val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    val KEY_MASTER_ENABLED = booleanPreferencesKey("master_enabled")
    val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
  }
}
