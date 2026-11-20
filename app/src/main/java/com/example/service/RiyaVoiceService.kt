package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.safety.SafetyGuard
import com.example.data.repository.PreferencesRepository
import com.example.domain.agent.RiyaBrain
import com.example.domain.model.PreferredLanguage
import com.example.voice.RiyaTtsManager
import com.example.voice.VoiceStateHolder
import com.example.voice.VoiceStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RiyaVoiceService : Service() {

  @Inject lateinit var riyaBrain: RiyaBrain
  @Inject lateinit var ttsManager: RiyaTtsManager
  @Inject lateinit var voiceStateHolder: VoiceStateHolder
  @Inject lateinit var preferencesRepository: PreferencesRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mainHandler = Handler(Looper.getMainLooper())

  private var speechRecognizer: SpeechRecognizer? = null
  private var isListeningActive = false
  private var directTapMode = false
  private var conversationWindowUntilMs = 0L
  private var forceEnglishRecognizerFallback = false

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        ensureForeground()
        stopVoiceService()
        return START_NOT_STICKY
      }
      ACTION_PAUSE_FOR_TTS -> {
        ensureForeground()
        mainHandler.removeCallbacksAndMessages(null)
        cancelRecognition()
        return START_STICKY
      }
      ACTION_TOGGLE_MUTE -> {
        ensureForeground()
        val nextMuted = !voiceStateHolder.state.value.isMuted
        voiceStateHolder.update {
          it.copy(
            isMuted = nextMuted,
            status = if (nextMuted) VoiceStatus.MUTED else VoiceStatus.WAKE_STANDBY,
            statusHint = if (nextMuted) "Mic muted" else "Listening for Hey Riya...",
          )
        }
        if (nextMuted) {
          ttsManager.stop()
          cancelRecognition()
        } else {
          startListeningCycle(directTap = false, forceReset = true)
        }
        updateNotification()
        return START_STICKY
      }
      ACTION_TAP_TO_TALK -> {
        // Barge-in: interrupt TTS immediately if speaking
        if (ttsManager.isSpeaking.value) {
          ttsManager.stop()
        }
        ensureForeground()
        voiceStateHolder.update {
          it.copy(isServiceRunning = true, isMuted = false, status = VoiceStatus.LISTENING, statusHint = "Shunchi jaan...")
        }
        startListeningCycle(directTap = true, forceReset = true)
        updateNotification()
        return START_STICKY
      }
      else -> {
        ensureForeground()
        voiceStateHolder.update {
          it.copy(isServiceRunning = true, isMuted = false, status = VoiceStatus.WAKE_STANDBY, statusHint = "Listening for Hey Riya...")
        }
        if (!ttsManager.isSpeaking.value) {
          startListeningCycle(directTap = false, forceReset = true)
        }
        updateNotification()
        return START_STICKY
      }
    }
  }

  private fun ensureForeground() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val hasMic =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
      if (hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun startListeningCycle(directTap: Boolean, forceReset: Boolean = false) {
    if (voiceStateHolder.state.value.isMuted) return
    if (!directTap && ttsManager.isSpeaking.value) return
    val hasMic =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      voiceStateHolder.update {
        it.copy(status = VoiceStatus.IDLE, statusHint = "Microphone permission needed")
      }
      return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      voiceStateHolder.update {
        it.copy(status = VoiceStatus.IDLE, statusHint = "Speech recognition unavailable on this device (type below)")
      }
      return
    }

    directTapMode = directTap
    serviceScope.launch {
      val prefLang = runCatching { preferencesRepository.settingsFlow.first().preferredLanguage }.getOrNull()
      val langTag =
        if (forceEnglishRecognizerFallback) {
          "en-IN"
        } else {
          when (prefLang) {
            PreferredLanguage.BENGALI -> "bn-IN"
            PreferredLanguage.HINDI -> "hi-IN"
            else -> "en-IN"
          }
        }
      try {
        mainHandler.removeCallbacksAndMessages(null)
        if (forceReset || isListeningActive) {
          runCatching { speechRecognizer?.cancel() }
          runCatching { speechRecognizer?.destroy() }
          speechRecognizer = null
          isListeningActive = false
        }
        if (speechRecognizer == null) {
          speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@RiyaVoiceService).apply {
            setRecognitionListener(createRecognitionListener())
          }
        }
        val recognizerIntent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 850L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
          }
        isListeningActive = true
        voiceStateHolder.update {
          it.copy(
            status = if (directTap) VoiceStatus.LISTENING else VoiceStatus.WAKE_STANDBY,
            statusHint = if (directTap) "Bolo jaan, shunchi..." else "Listening for \"Hey Riya\"...",
          )
        }
        speechRecognizer?.startListening(recognizerIntent)
      } catch (e: Exception) {
        Timber.e(e, "Failed to start SpeechRecognizer")
        destroyRecognizer()
        scheduleRestart(900L, forceReset = true)
      }
    }
  }

  private fun createRecognitionListener(): RecognitionListener {
    return object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}

      override fun onBeginningOfSpeech() {
        // Barge-in if user starts speaking while Riya talks
        if (ttsManager.isSpeaking.value) {
          ttsManager.stop()
        }
      }

      override fun onRmsChanged(rmsdB: Float) {
        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        voiceStateHolder.update { it.copy(amplitude = norm) }
      }

      override fun onBufferReceived(buffer: ByteArray?) {}

      override fun onEndOfSpeech() {
        voiceStateHolder.update { it.copy(amplitude = 0f) }
      }

      override fun onError(error: Int) {
        isListeningActive = false
        voiceStateHolder.update { it.copy(amplitude = 0f) }
        if (!voiceStateHolder.state.value.isServiceRunning || voiceStateHolder.state.value.isMuted) {
          return
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
          voiceStateHolder.update {
            it.copy(isServiceRunning = false, status = VoiceStatus.IDLE, statusHint = "Microphone permission allow koro!")
          }
          return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
          forceEnglishRecognizerFallback = true
          destroyRecognizer()
          scheduleRestart(300L, forceReset = true)
          return
        }
        val shouldHardReset =
          error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        if (shouldHardReset) {
          destroyRecognizer()
        }
        val delayMs =
          when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> 650L
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1400L
            else -> 900L
          }
        if (directTapMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
          directTapMode = false
        }
        scheduleRestart(delayMs, forceReset = shouldHardReset)
      }

      override fun onResults(results: Bundle?) {
        isListeningActive = false
        val spoken =
          results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        handleSpokenText(spoken)
      }

      override fun onPartialResults(partialResults: Bundle?) {
        val partial =
          partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (partial.isNotEmpty()) {
          voiceStateHolder.update { it.copy(liveTranscript = partial) }
          if (SafetyGuard.isEmergencyStopCommand(partial)) {
            ttsManager.stop()
          }
        }
      }

      override fun onEvent(eventType: Int, params: Bundle?) {}
    }
  }

  private fun handleSpokenText(spoken: String) {
    if (spoken.isEmpty()) {
      scheduleRestart(250L)
      return
    }
    serviceScope.launch {
      val settings = preferencesRepository.settingsFlow.first()
      val inWindow = System.currentTimeMillis() < conversationWindowUntilMs
      val hasWakeWord = containsWakeWord(spoken)
      val shouldRespond = directTapMode || !settings.wakeWordEnabled || hasWakeWord || inWindow || SafetyGuard.isEmergencyStopCommand(spoken)

      if (!shouldRespond) {
        scheduleRestart(250L)
        return@launch
      }

      val cleanedCommand =
        if (hasWakeWord) stripWakeWord(spoken).ifBlank { "Hii Riya" } else spoken

      directTapMode = false
      voiceStateHolder.update {
        it.copy(
          liveTranscript = cleanedCommand,
          status = VoiceStatus.THINKING,
          statusHint = "Bhabchi jaan...",
        )
      }
      updateNotification()

      val reply = riyaBrain.processUserMessage(cleanedCommand)
      if (reply.isEmergencyStop) {
        ttsManager.stop()
        voiceStateHolder.update {
          it.copy(
            lastRiyaReply = reply.text,
            currentMood = reply.mood,
            status = VoiceStatus.IDLE,
            statusHint = "Stopped",
          )
        }
        stopVoiceService()
        return@launch
      }

      conversationWindowUntilMs = System.currentTimeMillis() + 25_000L
      voiceStateHolder.update {
        it.copy(
          lastRiyaReply = reply.text,
          currentMood = reply.mood,
          status = VoiceStatus.SPEAKING,
          statusHint = "Riya kotha bolche...",
        )
      }
      updateNotification()

      ttsManager.speak(reply.text) {
        if (voiceStateHolder.state.value.isServiceRunning && !voiceStateHolder.state.value.isMuted) {
          startListeningCycle(directTap = false, forceReset = true)
        } else {
          voiceStateHolder.update {
            it.copy(status = VoiceStatus.IDLE, statusHint = "Tap the orb to talk with Riya")
          }
        }
      }
    }
  }

  private fun containsWakeWord(text: String): Boolean {
    val lower = text.lowercase()
    return WAKE_WORD_REGEX.containsMatchIn(lower) ||
      lower.contains("রিয়া") ||
      lower.contains("রিয়া")
  }

  private fun stripWakeWord(text: String): String {
    return text
      .replace(
        Regex("(?i)^(hey|hi|ok|hello|o|হে|হাই|হ্যালো|ও)?\\s*(riya|ria|reeya|reya|রিয়া|রিয়া)[,\\s]*"),
        "",
      )
      .trim()
  }

  private fun destroyRecognizer() {
    isListeningActive = false
    runCatching { speechRecognizer?.cancel() }
    runCatching { speechRecognizer?.destroy() }
    speechRecognizer = null
  }

  private fun scheduleRestart(delayMs: Long, forceReset: Boolean = false) {
    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.postDelayed({
      if (voiceStateHolder.state.value.isServiceRunning && !voiceStateHolder.state.value.isMuted) {
        startListeningCycle(directTap = false, forceReset = forceReset)
      }
    }, delayMs)
  }

  private fun cancelRecognition() {
    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.post {
      isListeningActive = false
      speechRecognizer?.cancel()
    }
  }

  private fun stopVoiceService() {
    mainHandler.removeCallbacksAndMessages(null)
    ttsManager.stop()
    destroyRecognizer()
    voiceStateHolder.update {
      it.copy(
        isServiceRunning = false,
        status = VoiceStatus.IDLE,
        amplitude = 0f,
        statusHint = "Tap the orb to talk with Riya",
      )
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun createNotificationChannel() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Riya Voice Assistant",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Keeps Riya listening warmly in the background"
      }
    nm.createNotificationChannel(channel)
  }

  private fun updateNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun buildNotification(): Notification {
    val state = voiceStateHolder.state.value
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val muteIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, RiyaVoiceService::class.java).setAction(ACTION_TOGGLE_MUTE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        2,
        Intent(this, RiyaVoiceService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Riya • ${if (state.isMuted) "Muted" else "Active"}")
      .setContentText(state.statusHint)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentIntent(openIntent)
      .setOngoing(true)
      .addAction(0, if (state.isMuted) "Unmute" else "Mute", muteIntent)
      .addAction(0, "Stop listening", stopIntent)
      .build()
  }

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    destroyRecognizer()
    serviceScope.cancel()
    voiceStateHolder.update {
      it.copy(
        isServiceRunning = false,
        status = VoiceStatus.IDLE,
        amplitude = 0f,
      )
    }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private val WAKE_WORD_REGEX = Regex("""\b(riya|ria|reeya|reya)\b""", RegexOption.IGNORE_CASE)
    const val CHANNEL_ID = "riya_voice_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_START = "com.example.action.START_VOICE"
    const val ACTION_TAP_TO_TALK = "com.example.action.TAP_TO_TALK"
    const val ACTION_PAUSE_FOR_TTS = "com.example.action.PAUSE_FOR_TTS"
    const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
    const val ACTION_STOP = "com.example.action.STOP_VOICE"

    fun start(context: Context, action: String = ACTION_START) {
      if (action == ACTION_STOP) {
        stop(context)
        return
      }
      val intent = Intent(context, RiyaVoiceService::class.java).setAction(action)
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      runCatching {
        context.stopService(Intent(context, RiyaVoiceService::class.java))
      }
    }
  }
}
