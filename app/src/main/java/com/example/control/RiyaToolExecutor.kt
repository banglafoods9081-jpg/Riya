package com.example.control

import com.example.analysis.PhoneAnalyzer
import com.example.core.safety.SafetyGuard
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PendingConfirmation {
  val summary: String

  data class CallAction(val displayName: String, val number: String) : PendingConfirmation {
    override val summary: String = "Call $displayName ($number)"
  }

  data class MessageAction(
    val channel: String,
    val displayName: String,
    val numberOrEmpty: String?,
    val message: String,
  ) : PendingConfirmation {
    override val summary: String =
      "Send ${channel.uppercase()} to $displayName: \"$message\""
  }
}

data class ToolExecutionOutput(
  val ok: Boolean,
  val message: String,
  val screenshotBase64: String? = null,
)

@Singleton
class RiyaToolExecutor
@Inject
constructor(
  private val appLauncher: AppLauncher,
  private val accessibilityBridge: AccessibilityBridge,
  private val systemController: SystemController,
  private val phoneAnalyzer: PhoneAnalyzer,
  private val memoryRepository: MemoryRepository,
) {

  private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
  val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

  suspend fun confirmPendingAction(): String {
    val pending = _pendingConfirmation.value ?: return "Kono pending action nei, jaan."
    _pendingConfirmation.value = null
    return when (pending) {
      is PendingConfirmation.CallAction -> {
        val result = systemController.executeCall(pending.number)
        logAndFormat("call_contact", pending.summary, result)
      }
      is PendingConfirmation.MessageAction -> {
        val result =
          if (pending.channel.equals("whatsapp", ignoreCase = true)) {
            systemController.openWhatsAppMessage(pending.numberOrEmpty, pending.message)
          } else {
            val number = pending.numberOrEmpty
            if (number.isNullOrBlank()) {
              Result.failure(IllegalArgumentException("Phone number khuje pelam na ${pending.displayName}-er jonno."))
            } else {
              systemController.executeSms(number, pending.message)
            }
          }
        logAndFormat("send_message", pending.summary, result)
      }
    }
  }

  suspend fun cancelPendingAction(): String {
    val pending = _pendingConfirmation.value ?: return "Thik ache jaan, kichu korchi na."
    _pendingConfirmation.value = null
    memoryRepository.logAction(
      toolName = "confirmation",
      argumentsSummary = pending.summary,
      status = "BLOCKED",
      resultMessage = "Cancelled by user",
    )
    return "Thik ache shona, ${pending.summary} cancel kore dilam."
  }

  suspend fun executeTool(name: String, args: JSONObject): ToolExecutionOutput {
    return when (name) {
      "open_app" -> {
        val appName = args.optString("app_name")
        val res = appLauncher.openAppByName(appName)
        toOutput(name, appName, res)
      }

      "play_youtube" -> {
        val query = args.optString("query")
        val openRes = systemController.openYouTubeSearch(query)
        if (openRes.isFailure) {
          toOutput(name, query, openRes)
        } else if (accessibilityBridge.isConnected.value) {
          val autoRes = accessibilityBridge.automateYouTubeFirstResult(query)
          toOutput(name, query, autoRes)
        } else {
          toOutput(
            name,
            query,
            Result.success("Opened YouTube search for '$query' (enable Accessibility Service for auto-tap first video)."),
          )
        }
      }

      "media_control" -> {
        val action = args.optString("action")
        val res =
          when (action.lowercase()) {
            "fullscreen", "like", "subscribe" -> {
              val label =
                when (action.lowercase()) {
                  "fullscreen" -> "Enter fullscreen"
                  "like" -> "like this video"
                  else -> "Subscribe"
                }
              accessibilityBridge.clickByTextOrId(label)
            }
            else -> systemController.dispatchMediaKey(action)
          }
        toOutput(name, action, res)
      }

      "tap" -> {
        val target = args.optString("target")
        val res =
          if (target.isNotBlank()) {
            accessibilityBridge.clickByTextOrId(target)
          } else if (args.has("x") && args.has("y")) {
            accessibilityBridge.tapCoordinates(args.optDouble("x").toFloat(), args.optDouble("y").toFloat())
          } else {
            Result.failure(IllegalArgumentException("Provide target text or x/y coordinates."))
          }
        toOutput(name, target.ifBlank { "coords" }, res)
      }

      "type_text" -> {
        val text = args.optString("text")
        if (SafetyGuard.containsSensitiveScreenText(text)) {
          val blocked = Result.failure<String>(SecurityException("Refused sensitive PIN/OTP/password input."))
          return toOutput(name, "[redacted]", blocked)
        }
        toOutput(name, "${text.take(24)}...", accessibilityBridge.typeText(text))
      }

      "scroll" -> {
        val dir = args.optString("direction", "down")
        toOutput(name, dir, accessibilityBridge.scroll(dir))
      }

      "swipe" -> {
        val res =
          accessibilityBridge.swipe(
            args.optDouble("start_x").toFloat(),
            args.optDouble("start_y").toFloat(),
            args.optDouble("end_x").toFloat(),
            args.optDouble("end_y").toFloat(),
          )
        toOutput(name, "swipe", res)
      }

      "press_global" -> {
        val action = args.optString("action")
        toOutput(name, action, accessibilityBridge.performGlobal(action))
      }

      "read_screen" -> {
        val res = accessibilityBridge.readScreenSummary()
        toOutput(name, "read_screen", res)
      }

      "describe_screen" -> {
        val treeRes = accessibilityBridge.readScreenSummary()
        if (treeRes.isFailure) {
          toOutput(name, "describe_screen", treeRes)
        } else {
          val shot = accessibilityBridge.takeScreenshotBase64().getOrNull()
          memoryRepository.logAction(name, "screen", "SUCCESS", "Screen read")
          ToolExecutionOutput(ok = true, message = treeRes.getOrThrow(), screenshotBase64 = shot)
        }
      }

      "take_screenshot" -> {
        toOutput(name, "screenshot", accessibilityBridge.performGlobal("screenshot"))
      }

      "call_contact" -> {
        val contact = args.optString("contact")
        val (displayName, number) = systemController.resolveContactNumber(contact)
        if (number.isNullOrBlank()) {
          val fail = Result.failure<String>(NoSuchElementException("'$contact' contact-e khuje pelam na ba Contacts permission nei."))
          toOutput(name, contact, fail)
        } else {
          val pending = PendingConfirmation.CallAction(displayName, number)
          _pendingConfirmation.value = pending
          memoryRepository.logAction(name, pending.summary, "PENDING_CONFIRM", "Awaiting confirmation")
          ToolExecutionOutput(
            ok = true,
            message = "CONFIRMATION_REQUIRED: Ask Rohan politely to confirm before calling $displayName ($number).",
          )
        }
      }

      "send_message" -> {
        val channel = args.optString("channel", "whatsapp")
        val recipient = args.optString("recipient")
        val message = args.optString("message")
        val (displayName, number) = systemController.resolveContactNumber(recipient)
        val pending = PendingConfirmation.MessageAction(channel, displayName, number, message)
        _pendingConfirmation.value = pending
        memoryRepository.logAction(name, pending.summary, "PENDING_CONFIRM", "Awaiting confirmation")
        ToolExecutionOutput(
          ok = true,
          message = "CONFIRMATION_REQUIRED: Ask Rohan to confirm sending '$message' via $channel to $displayName.",
        )
      }

      "set_alarm" -> {
        val hour = args.optInt("hour", 7)
        val minute = args.optInt("minute", 0)
        val label = args.optString("label", "Riya Alarm")
        toOutput(name, "$hour:$minute", systemController.setAlarm(hour, minute, label))
      }

      "set_timer" -> {
        val seconds = args.optInt("seconds", 60)
        val label = args.optString("label", "Riya Timer")
        toOutput(name, "${seconds}s", systemController.setTimer(seconds, label))
      }

      "toggle_setting" -> {
        val setting = args.optString("setting")
        val res =
          when (setting.lowercase()) {
            "flashlight_on", "torch_on" -> systemController.setFlashlight(true)
            "flashlight_off", "torch_off" -> systemController.setFlashlight(false)
            "flashlight_toggle", "flashlight", "torch" -> systemController.toggleFlashlight()
            "camera" -> systemController.openCamera()
            "maps" -> systemController.openMapsNavigation(args.optString("destination", "Home"))
            else -> systemController.openSetting(setting)
          }
        toOutput(name, setting, res)
      }

      "search_web" -> {
        val query = args.optString("query")
        toOutput(name, query, systemController.searchWeb(query))
      }

      "get_phone_status" -> {
        val report = phoneAnalyzer.analyze()
        val summary = phoneAnalyzer.formatSummaryForPrompt(report)
        toOutput(name, "analyze", Result.success(summary))
      }

      "read_notifications" -> {
        val unread = memoryRepository.getUnreadNotifications()
        val summary =
          if (unread.isEmpty()) {
            "Ekhon kono unread notification nei (ba Notification Access enable kora nei)."
          } else {
            unread.take(10).joinToString("\n") { "[${it.appName}] ${it.title}: ${it.body}" }
          }
        toOutput(name, "notifications", Result.success(summary))
      }

      "remember_fact" -> {
        val category = args.optString("category", "Personal")
        val fact = args.optString("fact")
        memoryRepository.rememberFact(category, fact)
        toOutput(name, category, Result.success("Remembered: $fact"))
      }

      else -> {
        toOutput(name, "", Result.failure(UnsupportedOperationException("Unknown tool $name")))
      }
    }
  }

  private suspend fun toOutput(
    toolName: String,
    argsSummary: String,
    result: Result<String>,
  ): ToolExecutionOutput {
    return result.fold(
      onSuccess = { msg ->
        memoryRepository.logAction(toolName, argsSummary, "SUCCESS", msg)
        ToolExecutionOutput(ok = true, message = msg)
      },
      onFailure = { err ->
        val status = if (err is SecurityException) "BLOCKED" else "FAILED"
        val msg = err.localizedMessage ?: "Action failed"
        memoryRepository.logAction(toolName, argsSummary, status, msg)
        ToolExecutionOutput(ok = false, message = msg)
      },
    )
  }

  private suspend fun logAndFormat(
    toolName: String,
    summary: String,
    result: Result<String>,
  ): String {
    return result.fold(
      onSuccess = { msg ->
        memoryRepository.logAction(toolName, summary, "SUCCESS", msg)
        "Hoye gese jaan! $msg"
      },
      onFailure = { err ->
        memoryRepository.logAction(toolName, summary, "FAILED", err.localizedMessage ?: "Failed")
        "Sorry shona, eta korte parlam na: ${err.localizedMessage ?: "permission/error"}"
      },
    )
  }
}
