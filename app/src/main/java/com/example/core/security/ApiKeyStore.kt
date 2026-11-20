package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ApiKeyState {
  data object Missing : ApiKeyState

  data class Saved(val maskedKey: String, val isVerified: Boolean) : ApiKeyState

  data class StorageError(val message: String) : ApiKeyState
}

@Singleton
class ApiKeyStore @Inject constructor(@ApplicationContext private val context: Context) {

  private val prefsResult: Result<SharedPreferences> by lazy {
    runCatching {
      val masterKey =
        MasterKey.Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()

      EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    }.onFailure { error ->
      Timber.e(error, "Failed to initialize EncryptedSharedPreferences")
    }
  }

  private val _apiKeyState = MutableStateFlow<ApiKeyState>(ApiKeyState.Missing)
  val apiKeyState: StateFlow<ApiKeyState> = _apiKeyState.asStateFlow()

  private val _elevenLabsKeyState = MutableStateFlow<ApiKeyState>(ApiKeyState.Missing)
  val elevenLabsKeyState: StateFlow<ApiKeyState> = _elevenLabsKeyState.asStateFlow()

  private val _elevenLabsVoiceId = MutableStateFlow(DEFAULT_ELEVENLABS_VOICE_ID)
  val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId.asStateFlow()

  init {
    refreshState()
  }

  fun refreshState() {
    prefsResult.fold(
      onSuccess = { prefs ->
        val raw = prefs.getString(KEY_GEMINI_API, null)?.trim().orEmpty()
        val verified = prefs.getBoolean(KEY_GEMINI_VERIFIED, false)
        _apiKeyState.value =
          if (raw.isEmpty()) {
            ApiKeyState.Missing
          } else {
            ApiKeyState.Saved(maskedKey = maskKey(raw), isVerified = verified)
          }

        val rawEl = prefs.getString(KEY_ELEVENLABS_API, null)?.trim().orEmpty()
        val elVerified = prefs.getBoolean(KEY_ELEVENLABS_VERIFIED, false)
        _elevenLabsKeyState.value =
          if (rawEl.isEmpty()) {
            ApiKeyState.Missing
          } else {
            ApiKeyState.Saved(maskedKey = maskKey(rawEl), isVerified = elVerified)
          }

        val savedVoice = prefs.getString(KEY_ELEVENLABS_VOICE_ID, null)?.trim()
        if (!savedVoice.isNullOrEmpty()) {
          _elevenLabsVoiceId.value = savedVoice
        }
      },
      onFailure = { error ->
        val msg = error.localizedMessage ?: "Encrypted storage unavailable on this device"
        _apiKeyState.value = ApiKeyState.StorageError(msg)
        _elevenLabsKeyState.value = ApiKeyState.StorageError(msg)
      },
    )
  }

  fun getApiKey(): String? {
    return prefsResult.getOrNull()?.getString(KEY_GEMINI_API, null)?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun saveApiKey(key: String, verified: Boolean): Result<Unit> {
    val sanitized = key.trim()
    if (sanitized.isEmpty()) {
      return Result.failure(IllegalArgumentException("API key cannot be empty"))
    }
    return prefsResult.mapCatching { prefs ->
      prefs.edit()
        .putString(KEY_GEMINI_API, sanitized)
        .putBoolean(KEY_GEMINI_VERIFIED, verified)
        .apply()
      _apiKeyState.value = ApiKeyState.Saved(maskedKey = maskKey(sanitized), isVerified = verified)
    }
  }

  fun clearApiKey(): Result<Unit> {
    return prefsResult.mapCatching { prefs ->
      prefs.edit()
        .remove(KEY_GEMINI_API)
        .remove(KEY_GEMINI_VERIFIED)
        .apply()
      _apiKeyState.value = ApiKeyState.Missing
    }
  }

  fun getElevenLabsApiKey(): String? {
    return prefsResult.getOrNull()?.getString(KEY_ELEVENLABS_API, null)?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun saveElevenLabsApiKey(key: String, verified: Boolean): Result<Unit> {
    val sanitized = key.trim()
    if (sanitized.isEmpty()) {
      return Result.failure(IllegalArgumentException("ElevenLabs API key cannot be empty"))
    }
    return prefsResult.mapCatching { prefs ->
      prefs.edit()
        .putString(KEY_ELEVENLABS_API, sanitized)
        .putBoolean(KEY_ELEVENLABS_VERIFIED, verified)
        .apply()
      _elevenLabsKeyState.value = ApiKeyState.Saved(maskedKey = maskKey(sanitized), isVerified = verified)
    }
  }

  fun clearElevenLabsApiKey(): Result<Unit> {
    return prefsResult.mapCatching { prefs ->
      prefs.edit()
        .remove(KEY_ELEVENLABS_API)
        .remove(KEY_ELEVENLABS_VERIFIED)
        .apply()
      _elevenLabsKeyState.value = ApiKeyState.Missing
    }
  }

  fun getElevenLabsVoiceId(): String = _elevenLabsVoiceId.value

  fun saveElevenLabsVoiceId(voiceId: String): Result<Unit> {
    val clean = voiceId.trim().ifEmpty { DEFAULT_ELEVENLABS_VOICE_ID }
    return prefsResult.mapCatching { prefs ->
      prefs.edit().putString(KEY_ELEVENLABS_VOICE_ID, clean).apply()
      _elevenLabsVoiceId.value = clean
    }
  }

  private fun maskKey(key: String): String {
    if (key.length <= 10) return "••••${key.takeLast(2)}"
    return "${key.take(6)}••••${key.takeLast(4)}"
  }

  companion object {
    private const val PREFS_FILE_NAME = "riya_encrypted_secrets"
    private const val KEY_GEMINI_API = "gemini_api_key"
    private const val KEY_GEMINI_VERIFIED = "gemini_api_verified"
    private const val KEY_ELEVENLABS_API = "elevenlabs_api_key"
    private const val KEY_ELEVENLABS_VERIFIED = "elevenlabs_api_verified"
    private const val KEY_ELEVENLABS_VOICE_ID = "elevenlabs_voice_id"
    const val DEFAULT_ELEVENLABS_VOICE_ID = "EXAVITQu4vr4xnSDxMaL" // Bella / Sarah soft sweet girl voice
  }
}
