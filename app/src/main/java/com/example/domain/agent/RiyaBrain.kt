package com.example.domain.agent

import com.example.analysis.PhoneAnalyzer
import com.example.analysis.RagStatus
import com.example.control.RiyaToolExecutor
import com.example.core.config.RiyaIdentity
import com.example.core.safety.SafetyGuard
import com.example.data.remote.GeminiToolDeclarations
import com.example.data.repository.MemoryRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import com.example.di.IoDispatcher
import com.example.domain.plugin.PluginRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

data class RiyaReply(
  val text: String,
  val mood: String = "warm",
  val isEmergencyStop: Boolean = false,
  val ragStatus: RagStatus? = null,
)

@Singleton
class RiyaBrain
@Inject
constructor(
  private val secretRepository: SecretRepository,
  private val preferencesRepository: PreferencesRepository,
  private val memoryRepository: MemoryRepository,
  private val toolExecutor: RiyaToolExecutor,
  private val phoneAnalyzer: PhoneAnalyzer,
  private val pluginRegistry: PluginRegistry,
  private val okHttpClient: OkHttpClient,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

  private val _isThinking = MutableStateFlow(false)
  val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

  private val _latestRagStatus = MutableStateFlow<RagStatus?>(null)
  val latestRagStatus: StateFlow<RagStatus?> = _latestRagStatus.asStateFlow()

  @Volatile private var activeModel: String = RiyaIdentity.PRIMARY_GEMINI_MODEL

  suspend fun processUserMessage(userText: String): RiyaReply =
    withContext(ioDispatcher) {
      val trimmed = userText.trim()
      if (trimmed.isEmpty()) {
        return@withContext RiyaReply("Bolo shona, ami shunchi...", mood = "warm")
      }

      memoryRepository.ensurePermanentFactsSeeded()
      memoryRepository.addMessage(role = "user", text = trimmed)

      // 1. Emergency Stop
      if (SafetyGuard.isEmergencyStopCommand(trimmed)) {
        val stopMsg = "Thik ache jaan, ami ekhoni theme gelam. Dorkar hole abar deko."
        memoryRepository.addMessage(role = "riya", text = stopMsg, mood = "calm")
        return@withContext RiyaReply(stopMsg, mood = "calm", isEmergencyStop = true)
      }

      // 2. Hard-coded Immutable Creator Rule
      if (RiyaIdentity.isCreatorQuestion(trimmed)) {
        val reply = RiyaIdentity.CREATOR_RESPONSE
        memoryRepository.addMessage(role = "riya", text = reply, mood = "proud")
        return@withContext RiyaReply(reply, mood = "proud")
      }

      // 3. Pending Risky Action Confirmation
      if (toolExecutor.pendingConfirmation.value != null) {
        val lower = trimmed.lowercase()
        val confirmed =
          listOf("haan", "ha", "yes", "ok", "okay", "confirm", "koro", "dao", "হ্যাঁ", "हाँ")
            .any { lower == it || lower.contains(it) }
        val cancelled =
          listOf("na", "no", "cancel", "stop", "thak", "না", "नहीं")
            .any { lower == it || lower.contains(it) }
        if (confirmed) {
          val msg = toolExecutor.confirmPendingAction()
          memoryRepository.addMessage(role = "riya", text = msg, mood = "happy")
          return@withContext RiyaReply(msg, mood = "happy")
        } else if (cancelled) {
          val msg = toolExecutor.cancelPendingAction()
          memoryRepository.addMessage(role = "riya", text = msg, mood = "warm")
          return@withContext RiyaReply(msg, mood = "warm")
        }
      }

      // 4. Analyze phone / jealousy Rag state
      val health = runCatching { phoneAnalyzer.analyze() }.getOrNull()
      val rag = health?.ragStatus
      _latestRagStatus.value = rag

      val apiKey = secretRepository.getStoredApiKey()
      if (apiKey.isNullOrBlank()) {
        val offlineFallback = tryOfflineFallback(trimmed, health?.let { phoneAnalyzer.formatSummaryForPrompt(it) })
        val reply =
          offlineFallback
            ?: "Jaan, amar Gemini API key ekhono dewa hoyni. Settings -> API Key screen-e giye key ta dao, tahole ami tomar sathe mon khule kotha bolte parbo!"
        memoryRepository.addMessage(role = "riya", text = reply, mood = "caring")
        return@withContext RiyaReply(reply, mood = "caring", ragStatus = rag)
      }

      _isThinking.value = true
      try {
        val settings = preferencesRepository.settingsFlow.first()
        val facts = memoryRepository.getFactsForPrompt()
        val recentHistory = memoryRepository.getRecentConversationForPrompt(16)
        val pluginHints = pluginRegistry.collectHintsForInput(trimmed)

        val systemPrompt =
          buildString {
            appendLine(
              RiyaIdentity.buildSystemPrompt(
                userName = settings.userName,
                preferredLanguage = settings.preferredLanguage,
                personaIntensity = settings.personaIntensity,
                excessivePhoneUseDetected = rag?.excessivePhoneUse == true,
                heavyChattingAppDetected = rag?.heavyChatAppName,
              )
            )
            if (facts.isNotEmpty()) {
              appendLine("\nStored Memory Facts:")
              facts.forEach { appendLine("- [${it.category}] ${it.fact}") }
            }
            if (pluginHints.isNotEmpty()) {
              appendLine("\nPlugin Context:")
              pluginHints.forEach { appendLine("- $it") }
            }
            appendLine("\nWhen executing a phone control tool, respond warmly in Bengali/Banglish/Hindi/English matching the user, confirming what happened honestly.")
          }

        val contents = JSONArray()
        recentHistory.dropLast(1).forEach { msg ->
          val geminiRole = if (msg.role == "user") "user" else "model"
          contents.put(
            JSONObject()
              .put("role", geminiRole)
              .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
          )
        }
        contents.put(
          JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", trimmed)))
        )

        val replyText = runGeminiToolLoop(apiKey, systemPrompt, contents)
        val mood =
          when {
            rag?.shouldShowRag == true -> "playful_rag"
            replyText.contains("sorry", ignoreCase = true) || replyText.contains("parlam na", ignoreCase = true) -> "apologetic"
            else -> "loving"
          }
        memoryRepository.addMessage(role = "riya", text = replyText, mood = mood)
        RiyaReply(text = replyText, mood = mood, ragStatus = rag)
      } catch (e: Exception) {
        Timber.e(e, "GeminiBrain error")
        val offlineFallback = tryOfflineFallback(trimmed, health?.let { phoneAnalyzer.formatSummaryForPrompt(it) })
        val fallbackMsg =
          offlineFallback
            ?: "Shona, ektu network/API problem hocche (${e.localizedMessage ?: "error"}). Tobe ami pashei achi!"
        memoryRepository.addMessage(role = "riya", text = fallbackMsg, mood = "caring")
        RiyaReply(text = fallbackMsg, mood = "caring", ragStatus = rag)
      } finally {
        _isThinking.value = false
      }
    }

  private suspend fun runGeminiToolLoop(
    apiKey: String,
    systemPrompt: String,
    contents: JSONArray,
  ): String {
    val tools = GeminiToolDeclarations.buildToolsJsonArray()
    repeat(6) {
      val payload =
        JSONObject()
          .put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
          )
          .put("contents", contents)
          .put("tools", tools)
          .put(
            "generationConfig",
            JSONObject()
              .put("temperature", 0.75)
              .put("maxOutputTokens", 512),
          )

      val responseJson = executeGenerateContentWithModelFallback(apiKey, payload)

      val candidates = responseJson.optJSONArray("candidates")
      val firstCandidate = candidates?.optJSONObject(0)
      val content = firstCandidate?.optJSONObject("content")
      val parts = content?.optJSONArray("parts") ?: JSONArray()

      // Append model turn to contents
      if (content != null) {
        contents.put(content)
      }

      val functionCalls = mutableListOf<Pair<String, JSONObject>>()
      val textParts = mutableListOf<String>()

      for (i in 0 until parts.length()) {
        val part = parts.optJSONObject(i) ?: continue
        if (part.has("functionCall")) {
          val fc = part.getJSONObject("functionCall")
          functionCalls.add(fc.getString("name") to (fc.optJSONObject("args") ?: JSONObject()))
        } else if (part.has("text")) {
          textParts.add(part.getString("text"))
        }
      }

      if (functionCalls.isEmpty()) {
        return textParts.joinToString("\n").trim().ifEmpty { "Hoye gese jaan!" }
      }

      val responseParts = JSONArray()
      var capturedScreenshot: String? = null

      for ((fnName, fnArgs) in functionCalls) {
        val result = toolExecutor.executeTool(fnName, fnArgs)
        if (result.screenshotBase64 != null) {
          capturedScreenshot = result.screenshotBase64
        }
        responseParts.put(
          JSONObject()
            .put(
              "functionResponse",
              JSONObject()
                .put("name", fnName)
                .put(
                  "response",
                  JSONObject()
                    .put("ok", result.ok)
                    .put("result", result.message),
                ),
            )
        )
      }

      contents.put(
        JSONObject()
          .put("role", "user")
          .put("parts", responseParts)
      )

      if (capturedScreenshot != null) {
        contents.put(
          JSONObject()
            .put("role", "user")
            .put(
              "parts",
              JSONArray()
                .put(JSONObject().put("text", "Here is the current screen image. Describe it warmly and concisely."))
                .put(
                  JSONObject()
                    .put(
                      "inlineData",
                      JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", capturedScreenshot),
                    )
                ),
            )
        )
      }
    }

    return "Kaj ta shesh korlam jaan!"
  }

  private fun executeGenerateContentWithModelFallback(
    apiKey: String,
    payload: JSONObject,
  ): JSONObject {
    val tried = linkedSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(activeModel)
    RiyaIdentity.FALLBACK_GEMINI_MODELS.forEach { if (it != activeModel) queue.add(it) }

    var lastError: String? = null
    while (queue.isNotEmpty()) {
      val model = queue.removeFirst()
      if (!tried.add(model)) continue

      val request =
        Request.Builder()
          .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
          .header("x-goog-api-key", apiKey)
          .post(payload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      okHttpClient.newCall(request).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (resp.isSuccessful) {
          activeModel = model
          return JSONObject(body)
        }
        val cleanMsg = extractGeminiErrorMessage(body) ?: "HTTP ${resp.code}"
        lastError = "HTTP ${resp.code}: $cleanMsg"

        if (resp.code == 404) {
          val suggested = extractSuggestedModel(body)
          if (suggested != null && !tried.contains(suggested)) {
            queue.addFirst(suggested)
          }
        } else {
          throw IllegalStateException(lastError)
        }
      }
    }
    throw IllegalStateException(lastError ?: "No compatible Gemini model available")
  }

  private suspend fun tryOfflineFallback(input: String, phoneSummary: String?): String? {
    val lower = input.lowercase()
    return when {
      lower.contains("flashlight on") || lower.contains("torch on") -> {
        toolExecutor.executeTool("toggle_setting", JSONObject().put("setting", "flashlight_on")).message
      }
      lower.contains("flashlight off") || lower.contains("torch off") -> {
        toolExecutor.executeTool("toggle_setting", JSONObject().put("setting", "flashlight_off")).message
      }
      lower.contains("analyse") || lower.contains("analyze") || lower.contains("phone status") -> {
        phoneSummary ?: toolExecutor.executeTool("get_phone_status", JSONObject()).message
      }
      lower.startsWith("open ") -> {
        val app = lower.removePrefix("open ").trim()
        val res = toolExecutor.executeTool("open_app", JSONObject().put("app_name", app))
        if (res.ok) "Hoye gese jaan! ${res.message}" else res.message
      }
      else -> null
    }
  }

  companion object {
    private val SUGGESTED_MODEL_REGEX = Regex("""use\s+models/([a-zA-Z0-9._-]+)""", RegexOption.IGNORE_CASE)

    internal fun extractSuggestedModel(errorBody: String): String? {
      return SUGGESTED_MODEL_REGEX.find(errorBody)?.groupValues?.getOrNull(1)
    }

    internal fun extractGeminiErrorMessage(errorBody: String): String? {
      return runCatching {
        JSONObject(errorBody).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
      }.getOrNull()
    }
  }
}
