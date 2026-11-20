package com.example.data.repository

import com.example.core.security.ApiKeyState
import com.example.core.security.ApiKeyStore
import com.example.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface KeyVerificationResult {
  data object Valid : KeyVerificationResult

  data class Invalid(val reason: String) : KeyVerificationResult
}

interface SecretRepository {
  val apiKeyState: StateFlow<ApiKeyState>

  fun getStoredApiKey(): String?

  suspend fun verifyAndSaveApiKey(apiKey: String): KeyVerificationResult

  suspend fun saveApiKeyLocally(apiKey: String): Result<Unit>

  suspend fun clearApiKey(): Result<Unit>
}

@Singleton
class SecretRepositoryImpl
@Inject
constructor(
  private val apiKeyStore: ApiKeyStore,
  private val okHttpClient: OkHttpClient,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SecretRepository {

  override val apiKeyState: StateFlow<ApiKeyState> = apiKeyStore.apiKeyState

  override fun getStoredApiKey(): String? = apiKeyStore.getApiKey()

  override suspend fun verifyAndSaveApiKey(apiKey: String): KeyVerificationResult =
    withContext(ioDispatcher) {
      val trimmed = apiKey.trim()
      if (trimmed.length < 20) {
        return@withContext KeyVerificationResult.Invalid(
          "This key looks too short. Please paste your full Gemini API key from Google AI Studio."
        )
      }

      val request =
        Request.Builder()
          .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash")
          .header("x-goog-api-key", trimmed)
          .get()
          .build()

      try {
        okHttpClient.newCall(request).execute().use { response ->
          when {
            response.isSuccessful -> {
              apiKeyStore.saveApiKey(trimmed, verified = true)
              KeyVerificationResult.Valid
            }
            response.code == 400 || response.code == 401 || response.code == 403 -> {
              KeyVerificationResult.Invalid(
                "Gemini rejected this API key (HTTP ${response.code}). Please double-check the key in Google AI Studio."
              )
            }
            response.code == 429 -> {
              // Key is valid but rate-limited right now
              apiKeyStore.saveApiKey(trimmed, verified = true)
              KeyVerificationResult.Valid
            }
            else -> {
              KeyVerificationResult.Invalid(
                "Unexpected server response (HTTP ${response.code}). You can still save locally and retry later."
              )
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Network error verifying Gemini API key")
        KeyVerificationResult.Invalid(
          "Could not reach Gemini servers (${e.localizedMessage ?: "network error"}). Check internet or use Save Locally."
        )
      }
    }

  override suspend fun saveApiKeyLocally(apiKey: String): Result<Unit> =
    withContext(ioDispatcher) { apiKeyStore.saveApiKey(apiKey, verified = false) }

  override suspend fun clearApiKey(): Result<Unit> =
    withContext(ioDispatcher) { apiKeyStore.clearApiKey() }
}
