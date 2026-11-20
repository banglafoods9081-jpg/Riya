package com.example.domain.agent

import com.example.analysis.PhoneAnalyzer
import com.example.analysis.RagStatus
import com.example.control.RiyaToolExecutor
import com.example.core.config.RiyaIdentity
import com.example.core.safety.SafetyGuard
import com.example.data.remote.GrokToolDeclarations
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

  @Volatile private var activeGrokModel: String = RiyaIdentity.PRIMARY_GROK_MODEL
  @Volatile private var activeGroqModel: String = RiyaIdentity.FALLBACK_GROQ_MODELS.first()

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

      // 5. Instant (<50ms) fast-path for direct single-step phone control & analysis
      val fastReply = tryFastLocalCommand(trimmed, health, rag)
      if (fastReply != null) {
        val mood = if (rag?.shouldShowRag == true) "playful_rag" else "loving"
        memoryRepository.addMessage(role = "riya", text = fastReply, mood = mood)
        return@withContext RiyaReply(text = fastReply, mood = mood, ragStatus = rag)
      }

      val apiKey = secretRepository.getStoredApiKey()
      if (apiKey.isNullOrBlank()) {
        val reply =
          "Jaan, amar Grok API key ekhono dewa hoyni. Settings -> API Key screen-e giye key ta dao, tahole ami tomar sathe mon khule kotha bolte parbo!"
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

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        recentHistory.dropLast(1).forEach { msg ->
          val role = if (msg.role == "user") "user" else "assistant"
          messages.put(
            JSONObject()
              .put("role", role)
              .put("content", msg.text)
          )
        }
        messages.put(
          JSONObject()
            .put("role", "user")
            .put("content", trimmed)
        )

        val replyText = runGrokToolLoop(apiKey, messages)
        val mood =
          when {
            rag?.shouldShowRag == true -> "playful_rag"
            replyText.contains("sorry", ignoreCase = true) || replyText.contains("parlam na", ignoreCase = true) -> "apologetic"
            else -> "loving"
          }
        memoryRepository.addMessage(role = "riya", text = replyText, mood = mood)
        RiyaReply(text = replyText, mood = mood, ragStatus = rag)
      } catch (e: Exception) {
        Timber.e(e, "GrokBrain error")
        val fallbackMsg =
          "Shona, ektu network/API problem hocche (${e.localizedMessage ?: "error"}). Tobe ami pashei achi!"
        memoryRepository.addMessage(role = "riya", text = fallbackMsg, mood = "caring")
        RiyaReply(text = fallbackMsg, mood = "caring", ragStatus = rag)
      } finally {
        _isThinking.value = false
      }
    }

  private suspend fun runGrokToolLoop(
    apiKey: String,
    messages: JSONArray,
  ): String {
    val tools = GrokToolDeclarations.buildToolsJsonArray()
    val provider = resolveProviderConfig(apiKey)

    repeat(6) {
      val responseJson =
        executeChatCompletionsWithFallback(
          apiKey = apiKey,
          provider = provider,
          messages = messages,
          tools = tools,
        )

      val choices = responseJson.optJSONArray("choices")
      val firstChoice = choices?.optJSONObject(0)
      val assistantMsg = firstChoice?.optJSONObject("message")
        ?: return "Hoye gese jaan!"

      messages.put(assistantMsg)

      val toolCalls = assistantMsg.optJSONArray("tool_calls")
      if (toolCalls == null || toolCalls.length() == 0) {
        val text = assistantMsg.optString("content", "").trim()
        return text.ifEmpty { "Hoye gese jaan!" }
      }

      var capturedScreenshot: String? = null
      for (i in 0 until toolCalls.length()) {
        val tc = toolCalls.optJSONObject(i) ?: continue
        val callId = tc.optString("id", "call_$i")
        val fnObj = tc.optJSONObject("function") ?: continue
        val fnName = fnObj.optString("name")
        val rawArgs = fnObj.optString("arguments", "{}")
        val fnArgs = runCatching { JSONObject(rawArgs) }.getOrElse { JSONObject() }

        val result = toolExecutor.executeTool(fnName, fnArgs)
        if (result.screenshotBase64 != null) {
          capturedScreenshot = result.screenshotBase64
        }
        val toolContent =
          JSONObject()
            .put("ok", result.ok)
            .put("result", result.message)
            .toString()

        messages.put(
          JSONObject()
            .put("role", "tool")
            .put("tool_call_id", callId)
            .put("content", toolContent)
        )
      }

      if (capturedScreenshot != null && !provider.isGroq) {
        messages.put(
          JSONObject()
            .put("role", "user")
            .put(
              "content",
              JSONArray()
                .put(
                  JSONObject()
                    .put("type", "text")
                    .put("text", "Here is the current screen image. Describe it warmly and concisely.")
                )
                .put(
                  JSONObject()
                    .put("type", "image_url")
                    .put(
                      "image_url",
                      JSONObject().put("url", "data:image/jpeg;base64,$capturedScreenshot")
                    )
                ),
            )
        )
      }
    }

    return "Kaj ta shesh korlam jaan!"
  }

  private fun executeChatCompletionsWithFallback(
    apiKey: String,
    provider: ProviderConfig,
    messages: JSONArray,
    tools: JSONArray,
  ): JSONObject {
    val tried = linkedSetOf<String>()
    val queue = ArrayDeque<String>()
    val preferredActive = if (provider.isGroq) activeGroqModel else activeGrokModel
    queue.add(preferredActive)
    provider.fallbackModels.forEach { if (it != preferredActive) queue.add(it) }

    var discoveredModels = false
    var lastError: String? = null

    while (queue.isNotEmpty()) {
      val model = queue.removeFirst()
      if (!tried.add(model)) continue

      val payload =
        JSONObject()
          .put("model", model)
          .put("messages", messages)
          .put("tools", tools)
          .put("tool_choice", "auto")
          .put("temperature", 0.72)
          .put("max_tokens", 350)
          .toString()

      val request =
        Request.Builder()
          .url("${provider.baseUrl}/chat/completions")
          .header("Authorization", "Bearer $apiKey")
          .post(payload.toRequestBody("application/json".toMediaType()))
          .build()

      okHttpClient.newCall(request).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (resp.isSuccessful) {
          if (provider.isGroq) activeGroqModel = model else activeGrokModel = model
          return JSONObject(body)
        }
        val cleanMsg = extractGrokErrorMessage(body) ?: "HTTP ${resp.code}"
        lastError = "${provider.label} HTTP ${resp.code}: $cleanMsg"

        val isModelError =
          resp.code == 404 ||
            (resp.code == 400 &&
              (body.contains("model", ignoreCase = true) ||
                body.contains("does not exist", ignoreCase = true) ||
                body.contains("deprecated", ignoreCase = true)))

        if (isModelError) {
          if (!discoveredModels) {
            discoveredModels = true
            val liveModels = discoverLiveModels(okHttpClient, apiKey, provider)
            liveModels.forEach { candidate ->
              if (!tried.contains(candidate) && !queue.contains(candidate)) {
                queue.addFirst(candidate)
              }
            }
          }
        } else {
          throw IllegalStateException(lastError)
        }
      }
    }
    throw IllegalStateException(lastError ?: "No compatible ${provider.label} model available")
  }

  private suspend fun tryFastLocalCommand(
    input: String,
    health: com.example.analysis.PhoneHealthReport?,
    rag: RagStatus?,
  ): String? {
    val lower = input.trim().lowercase()
    // Let multi-step commands go to Grok tool loop
    val looksMultiStep =
      listOf(" then ", " tarpor ", " kore ", " pore ", " ar volume", " & ").any { lower.contains(it) }
    if (looksMultiStep) return null

    // 1. Flashlight
    if (Regex("""\b(flashlight|torch|light)\b.*\b(on|jalao|jalo|start)\b""").containsMatchIn(lower) ||
      lower == "flashlight on" || lower == "torch on" ||
      (lower.contains("ফ্ল্যাশলাইট") && (lower.contains("অন") || lower.contains("জ্বালাও") || lower.contains("চালু")))
    ) {
      val res = toolExecutor.executeTool("toggle_setting", JSONObject().put("setting", "flashlight_on"))
      return if (res.ok) "Hoye gese jaan! Flashlight on kore diyechi." else res.message
    }
    if (Regex("""\b(flashlight|torch|light)\b.*\b(off|bondho|stop|bujhao)\b""").containsMatchIn(lower) ||
      lower == "flashlight off" || lower == "torch off" ||
      (lower.contains("ফ্ল্যাশলাইট") && (lower.contains("অফ") || lower.contains("বন্ধ")))
    ) {
      val res = toolExecutor.executeTool("toggle_setting", JSONObject().put("setting", "flashlight_off"))
      return if (res.ok) "Flashlight off kore dilam shona." else res.message
    }

    // 2. YouTube search & play
    val ytRegexes =
      listOf(
        Regex("""^(?:hey\s+)?(?:youtube|yt|ইউটিউব|ইউটিউবে)\s*(?:e|te|me|pe|on|in|এ|তে)?\s+(.+?)\s+(?:play|chalao|chala|bajao|baja|search|প্লে|চালাও|চালা|বাজাও|বাজা|সার্চ)(?:\s+(?:koro|karo|kro|করো|কর|দাও))?$"""),
        Regex("""^play\s+(.+?)\s+on\s+youtube$"""),
      )
    for (rx in ytRegexes) {
      val match = rx.find(lower)
      val songQuery = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
      if (songQuery.isNotEmpty() && !songQuery.contains("kore")) {
        val res = toolExecutor.executeTool("play_youtube", JSONObject().put("query", songQuery))
        return if (res.ok) "Hoye gese jaan! YouTube-e \"$songQuery\" chaliye dichi." else res.message
      }
    }

    // 3. Volume & Media
    when {
      lower in listOf("volume up", "volume barao", "awaj barao", "sound barao", "ভলিউম বাড়াও", "আওয়াজ বাড়াও") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "volume_up"))
        return if (res.ok) "Volume bariye dilam jaan!" else res.message
      }
      lower in listOf("volume down", "volume komao", "awaj komao", "sound komao", "ভলিউম কমাও", "আওয়াজ কমাও") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "volume_down"))
        return if (res.ok) "Volume komiye dilam shona!" else res.message
      }
      lower in listOf("mute", "mute koro", "awaj bondho", "মিউট করো") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "mute"))
        return if (res.ok) "Sound mute kore diyechi." else res.message
      }
      lower in listOf("unmute", "unmute koro") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "unmute"))
        return if (res.ok) "Sound unmute kore diyechi." else res.message
      }
      lower in listOf("pause", "pause koro", "gan thamao", "gaan thamao", "video thamao", "পজ করো", "থামাও") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "pause"))
        return if (res.ok) "Pause kore dilam jaan." else res.message
      }
      lower in listOf("resume", "play koro", "gan chalao", "চালু করো") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "play"))
        return if (res.ok) "Abar play kore dilam!" else res.message
      }
      lower in listOf("next", "next song", "porer gaan", "next video", "পরের গান") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "next"))
        return if (res.ok) "Porer gaan-e gelam shona!" else res.message
      }
      lower in listOf("previous", "previous song", "ager gaan", "আগের গান") -> {
        val res = toolExecutor.executeTool("media_control", JSONObject().put("action", "previous"))
        return if (res.ok) "Ager gaan-e phire gelam!" else res.message
      }
    }

    // 4. Global Accessibility actions & Scrolling
    when {
      lower in listOf("back", "go back", "back jao", "pichone jao", "ব্যাক যাও", "পিছনে যাও") -> {
        val res = toolExecutor.executeTool("press_global", JSONObject().put("action", "back"))
        return if (res.ok) "Back gelam jaan." else res.message
      }
      lower in listOf("home", "go home", "home jao", "home screen", "হোম যাও") -> {
        val res = toolExecutor.executeTool("press_global", JSONObject().put("action", "home"))
        return if (res.ok) "Home screen-e chole elam." else res.message
      }
      lower in listOf("screenshot", "take screenshot", "screenshot nao", "screenshot tulo", "স্ক্রিনশট নাও") -> {
        val res = toolExecutor.executeTool("take_screenshot", JSONObject())
        return if (res.ok) "Screenshot tule niyechi shona!" else res.message
      }
      lower in listOf("scroll down", "niche scroll", "niche scroll koro", "niche namo", "নিচে স্ক্রল করো") -> {
        val res = toolExecutor.executeTool("scroll", JSONObject().put("direction", "down"))
        return if (res.ok) "Niche scroll korlam!" else res.message
      }
      lower in listOf("scroll up", "upore scroll", "upore scroll koro", "upore otho", "উপরে স্ক্রল করো") -> {
        val res = toolExecutor.executeTool("scroll", JSONObject().put("direction", "up"))
        return if (res.ok) "Upore scroll korlam!" else res.message
      }
      lower in listOf("notifications", "open notifications", "notification kholo", "নোটিফিকেশন খোলো") -> {
        val res = toolExecutor.executeTool("press_global", JSONObject().put("action", "notifications"))
        return if (res.ok) "Notification panel khule dilam." else res.message
      }
      lower in listOf("read notifications", "notification poro", "ki notification eseche") -> {
        return toolExecutor.executeTool("read_notifications", JSONObject()).message
      }
      lower in listOf("read screen", "screen poro", "screen e ki ache") -> {
        val res = toolExecutor.executeTool("read_screen", JSONObject())
        return if (res.ok) "Screen-e dekhchi:\n${res.message.take(260)}" else res.message
      }
    }

    // 5. Phone Health & Caring Rag Analysis
    if (lower.contains("analyse") || lower.contains("analyze") || lower.contains("analysis") ||
      lower.contains("phone status") || lower.contains("phone check") || lower.contains("amar phone") ||
      lower.contains("ফোন চেক") || lower.contains("অ্যানালাইসিস") || lower.contains("অ্যানালাইজ")
    ) {
      val report = health ?: phoneAnalyzer.analyze()
      val summary = phoneAnalyzer.formatSummaryForPrompt(report)
      toolExecutor.executeTool("get_phone_status", JSONObject())
      return buildString {
        if (rag?.shouldShowRag == true && !rag.reasonMessage.isNullOrBlank()) {
          appendLine(rag.reasonMessage)
          appendLine()
        }
        append("Tomar phone-er khobor, Rohan: $summary")
      }
    }

    // 6. Direct Open App ("open youtube", "whatsapp kholo", "ইউটিউব খোল", etc.)
    val openSuffixMatch =
      Regex("""^(.{2,28}?)\s+(?:kholo|khol|khule\s*dao|open\s*(?:koro|karo|kro)?|chalao|chala|open|খোলো|খোল|খুলে\s*দাও|ওপেন\s*(?:করো|কর)?|চালাও|চালা|চালু\s*(?:করো|কর))$""").find(lower)
    val openPrefixMatch =
      Regex("""^(?:open|launch|kholo|khol|খোলো|খোল|ওপেন\s*(?:করো|কর)?)\s+(.{2,28})$""").find(lower)
    val appCandidate =
      (openSuffixMatch?.groupValues?.getOrNull(1) ?: openPrefixMatch?.groupValues?.getOrNull(1))?.trim()
    if (!appCandidate.isNullOrBlank() &&
      appCandidate !in setOf("it", "this", "app", "screen", "notifications", "settings")
    ) {
      val res = toolExecutor.executeTool("open_app", JSONObject().put("app_name", appCandidate))
      return if (res.ok) "Hoye gese jaan! ${res.message}." else res.message
    }

    return null
  }

  data class ProviderConfig(
    val label: String,
    val baseUrl: String,
    val isGroq: Boolean,
    val fallbackModels: List<String>,
  )

  companion object {
    internal fun resolveProviderConfig(apiKey: String): ProviderConfig {
      val trimmed = apiKey.trim()
      return if (trimmed.startsWith("gsk_", ignoreCase = true)) {
        ProviderConfig(
          label = "Groq",
          baseUrl = "https://api.groq.com/openai/v1",
          isGroq = true,
          fallbackModels = RiyaIdentity.FALLBACK_GROQ_MODELS,
        )
      } else {
        ProviderConfig(
          label = "Grok (xAI)",
          baseUrl = "https://api.x.ai/v1",
          isGroq = false,
          fallbackModels = RiyaIdentity.FALLBACK_GROK_MODELS,
        )
      }
    }

    internal fun discoverLiveModels(
      client: OkHttpClient,
      apiKey: String,
      provider: ProviderConfig,
    ): List<String> {
      return runCatching {
        val req =
          Request.Builder()
            .url("${provider.baseUrl}/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return emptyList()
          val body = resp.body?.string().orEmpty()
          parsePreferredChatModels(body, provider.isGroq)
        }
      }.getOrDefault(emptyList())
    }

    internal fun parsePreferredChatModels(modelsJsonBody: String, isGroq: Boolean): List<String> {
      val data = runCatching { JSONObject(modelsJsonBody).optJSONArray("data") }.getOrNull() ?: return emptyList()
      val blockedKeywords = listOf("image", "video", "imagine", "tts", "whisper", "audio", "guard", "embed", "moderation")
      val ids = mutableListOf<String>()
      for (i in 0 until data.length()) {
        val id = data.optJSONObject(i)?.optString("id")?.trim().orEmpty()
        if (id.isNotEmpty() && blockedKeywords.none { id.contains(it, ignoreCase = true) }) {
          ids.add(id)
        }
      }
      val preferredOrder =
        if (isGroq) listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "qwen", "llama")
        else listOf("grok-4.20", "grok-4.3", "grok-4.6", "grok-4.7", "grok-4", "grok-3-mini", "grok-3-fast", "grok-3", "grok")

      return ids.sortedBy { id ->
        val idx = preferredOrder.indexOfFirst { id.contains(it, ignoreCase = true) }
        if (idx >= 0) idx else preferredOrder.size
      }
    }

    internal fun extractGrokErrorMessage(errorBody: String): String? {
      return runCatching {
        val json = JSONObject(errorBody)
        when (val err = json.opt("error")) {
          is JSONObject -> err.optString("message").takeIf { it.isNotBlank() }
          is String -> err.takeIf { it.isNotBlank() }
          else -> json.optString("message").takeIf { it.isNotBlank() }
        }
      }.getOrNull()
    }
  }
}
