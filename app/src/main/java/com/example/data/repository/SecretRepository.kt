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

data class ElevenLabsVoicePreset(
  val id: String,
  val label: String,
  val vibe: String,
)

val RIYA_ELEVENLABS_VOICES =
  listOf(
    ElevenLabsVoicePreset("EXAVITQu4vr4xnSDxMaL", "Bella (Soft & Sweet)", "Misti romantic girl tone"),
    ElevenLabsVoicePreset("21m00Tcm4TlvDq8ikWAM", "Rachel (Warm & Caring)", "Gentle girlfriend voice"),
    ElevenLabsVoicePreset("MF3mGyEYCl7XYWbV9V6O", "Elli (Cute & Playful)", "Young bubbly sweet tone"),
    ElevenLabsVoicePreset("9BWtsMINqrJLrRacOk9x", "Aria (Expressive)", "Natural emotional voice"),
  )

interface SecretRepository {
  val apiKeyState: StateFlow<ApiKeyState>
  val elevenLabsKeyState: StateFlow<ApiKeyState>
  val elevenLabsVoiceId: StateFlow<String>

  fun getStoredApiKey(): String?

  fun getStoredElevenLabsApiKey(): String?

  fun getStoredElevenLabsVoiceId(): String

  suspend fun verifyAndSaveApiKey(apiKey: String): KeyVerificationResult

  suspend fun saveApiKeyLocally(apiKey: String): Result<Unit>

  suspend fun clearApiKey(): Result<Unit>

  suspend fun verifyAndSaveElevenLabsKey(apiKey: String): KeyVerificationResult

  suspend fun saveElevenLabsKeyLocally(apiKey: String): Result<Unit>

  suspend fun clearElevenLabsKey(): Result<Unit>

  suspend fun setElevenLabsVoiceId(voiceId: String): Result<Unit>
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
  override val elevenLabsKeyState: StateFlow<ApiKeyState> = apiKeyStore.elevenLabsKeyState
  override val elevenLabsVoiceId: StateFlow<String> = apiKeyStore.elevenLabsVoiceId

  override fun getStoredApiKey(): String? = apiKeyStore.getApiKey()

  override fun getStoredElevenLabsApiKey(): String? = apiKeyStore.getElevenLabsApiKey()

  override fun getStoredElevenLabsVoiceId(): String = apiKeyStore.getElevenLabsVoiceId()

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

  override suspend fun verifyAndSaveElevenLabsKey(apiKey: String): KeyVerificationResult =
    withContext(ioDispatcher) {
      val trimmed = apiKey.trim()
      if (trimmed.length < 16) {
        return@withContext KeyVerificationResult.Invalid(
          "ElevenLabs API key looks too short. Copy the full key from elevenlabs.io."
        )
      }
      val request =
        Request.Builder()
          .url("https://api.elevenlabs.io/v1/user")
          .header("xi-api-key", trimmed)
          .get()
          .build()

      try {
        okHttpClient.newCall(request).execute().use { response ->
          when {
            response.isSuccessful -> {
              apiKeyStore.saveElevenLabsApiKey(trimmed, verified = true)
              KeyVerificationResult.Valid
            }
            response.code == 401 || response.code == 403 -> {
              KeyVerificationResult.Invalid(
                "ElevenLabs rejected this key (HTTP ${response.code}). Check your ElevenLabs profile."
              )
            }
            else -> {
              KeyVerificationResult.Invalid(
                "ElevenLabs check returned HTTP ${response.code}. You can still save locally."
              )
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Network error verifying ElevenLabs key")
        KeyVerificationResult.Invalid(
          "Could not reach ElevenLabs (${e.localizedMessage ?: "network error"})."
        )
      }
    }

  override suspend fun saveElevenLabsKeyLocally(apiKey: String): Result<Unit> =
    withContext(ioDispatcher) { apiKeyStore.saveElevenLabsApiKey(apiKey, verified = false) }

  override suspend fun clearElevenLabsKey(): Result<Unit> =
    withContext(ioDispatcher) { apiKeyStore.clearElevenLabsApiKey() }

  override suspend fun setElevenLabsVoiceId(voiceId: String): Result<Unit> =
    withContext(ioDispatcher) { apiKeyStore.saveElevenLabsVoiceId(voiceId) }
}
