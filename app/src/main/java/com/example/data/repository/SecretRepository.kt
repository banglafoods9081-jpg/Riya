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
      if (trimmed.startsWith("AIza", ignoreCase = true)) {
        return@withContext KeyVerificationResult.Invalid(
          "Eta purono Gemini key! Ekhon Grok API key (xai-... from console.x.ai, ba Groq gsk_...) paste koro."
        )
      }
      if (trimmed.length < 20) {
        return@withContext KeyVerificationResult.Invalid(
          "This key looks too short. Please paste your full Grok API key (xai-...) or Groq key (gsk_...)."
        )
      }

      val provider = RiyaBrain.resolveProviderConfig(trimmed)

      try {
        // 1. Check /models first (0 tokens) to validate key and discover active account models
        val modelsReq =
          Request.Builder()
            .url("${provider.baseUrl}/models")
            .header("Authorization", "Bearer $trimmed")
            .get()
            .build()

        val discovered = mutableListOf<String>()
        okHttpClient.newCall(modelsReq).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          when {
            resp.isSuccessful -> {
              discovered.addAll(RiyaBrain.parsePreferredChatModels(body, provider.isGroq))
            }
            resp.code == 401 || resp.code == 403 -> {
              val msg = RiyaBrain.extractGrokErrorMessage(body)
              return@withContext KeyVerificationResult.Invalid(
                "${provider.label} rejected this API key (HTTP ${resp.code}${msg?.let { ": $it" }.orEmpty()})."
              )
            }
          }
        }

        val queue = ArrayDeque<String>()
        discovered.forEach { queue.add(it) }
        provider.fallbackModels.forEach { if (!queue.contains(it)) queue.add(it) }

        val tried = linkedSetOf<String>()
        var lastCode = 0
        var lastMessage: String? = null

        while (queue.isNotEmpty()) {
          val model = queue.removeFirst()
          if (!tried.add(model)) continue

          val pingPayload =
            JSONObject()
              .put("model", model)
              .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", "hi")),
              )
              .put("max_tokens", 2)
              .toString()

          val request =
            Request.Builder()
              .url("${provider.baseUrl}/chat/completions")
              .header("Authorization", "Bearer $trimmed")
              .post(pingPayload.toRequestBody("application/json".toMediaType()))
              .build()

          okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            lastCode = response.code
            lastMessage = RiyaBrain.extractGrokErrorMessage(body)
            when {
              response.isSuccessful -> {
                apiKeyStore.saveApiKey(trimmed, verified = true)
                return@withContext KeyVerificationResult.Valid
              }
              response.code == 401 || response.code == 403 -> {
                return@withContext KeyVerificationResult.Invalid(
                  "${provider.label} rejected this API key (HTTP ${response.code}${lastMessage?.let { ": $it" }.orEmpty()})."
                )
              }
              response.code == 402 -> {
                return@withContext KeyVerificationResult.Invalid(
                  "${provider.label} key active, but account needs credits (HTTP 402${lastMessage?.let { ": $it" }.orEmpty()})."
                )
              }
              response.code == 429 -> {
                apiKeyStore.saveApiKey(trimmed, verified = true)
                return@withContext KeyVerificationResult.Valid
              }
              response.code == 404 ||
                (response.code == 400 && body.contains("model", ignoreCase = true)) -> {
                // Try next discovered/fallback model
              }
              else -> {
                return@withContext KeyVerificationResult.Invalid(
                  "Unexpected ${provider.label} response (HTTP ${response.code}${lastMessage?.let { ": $it" }.orEmpty()}). You can still save locally and retry."
                )
              }
            }
          }
        }

        KeyVerificationResult.Invalid(
          "${provider.label} check returned HTTP $lastCode${lastMessage?.let { ": $it" }.orEmpty()}. You can still save locally and retry."
        )
      } catch (e: Exception) {
        Timber.w(e, "Network error verifying ${provider.label} API key")
        KeyVerificationResult.Invalid(
          "Could not reach ${provider.label} servers (${e.localizedMessage ?: "network error"}). Check internet or use Save Locally."
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
