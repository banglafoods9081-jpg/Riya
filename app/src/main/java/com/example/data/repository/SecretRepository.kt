package com.example.data.repository

import com.example.core.config.RiyaIdentity
import com.example.core.security.ApiKeyState
import com.example.core.security.ApiKeyStore
import com.example.di.IoDispatcher
import com.example.domain.agent.RiyaBrain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

      val pingPayload =
        JSONObject()
          .put(
            "contents",
            JSONArray().put(
              JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "hi")))
            ),
          )
          .put("generationConfig", JSONObject().put("maxOutputTokens", 2))
          .toString()

      val tried = linkedSetOf<String>()
      val queue = ArrayDeque<String>()
      RiyaIdentity.FALLBACK_GEMINI_MODELS.forEach { queue.add(it) }

      try {
        var lastCode = 0
        var lastMessage: String? = null

        while (queue.isNotEmpty()) {
          val model = queue.removeFirst()
          if (!tried.add(model)) continue

          val request =
            Request.Builder()
              .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
              .header("x-goog-api-key", trimmed)
              .post(pingPayload.toRequestBody("application/json".toMediaType()))
              .build()

          okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            lastCode = response.code
            lastMessage = RiyaBrain.extractGeminiErrorMessage(body)
            when {
              response.isSuccessful -> {
                apiKeyStore.saveApiKey(trimmed, verified = true)
                return@withContext KeyVerificationResult.Valid
              }
              response.code == 400 || response.code == 401 || response.code == 403 -> {
                return@withContext KeyVerificationResult.Invalid(
                  "Gemini rejected this API key (HTTP ${response.code}). Please double-check the key in Google AI Studio."
                )
              }
              response.code == 429 -> {
                // Key is valid but rate-limited right now
                apiKeyStore.saveApiKey(trimmed, verified = true)
                return@withContext KeyVerificationResult.Valid
              }
              response.code == 404 -> {
                val suggested = RiyaBrain.extractSuggestedModel(body)
                if (suggested != null && !tried.contains(suggested)) {
                  queue.addFirst(suggested)
                }
              }
              else -> {
                return@withContext KeyVerificationResult.Invalid(
                  "Unexpected server response (HTTP ${response.code}${lastMessage?.let { ": $it" }.orEmpty()}). You can still save locally and retry later."
                )
              }
            }
          }
        }

        KeyVerificationResult.Invalid(
          "Model check returned HTTP $lastCode${lastMessage?.let { ": $it" }.orEmpty()}. You can still save locally and retry later."
        )
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
