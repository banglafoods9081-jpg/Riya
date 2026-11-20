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

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopVoiceService()
        return START_NOT_STICKY
      }
      ACTION_TOGGLE_MUTE -> {
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
          startListeningCycle(directTap = false)
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
        startListeningCycle(directTap = true)
        updateNotification()
        return START_STICKY
      }
      else -> {
        ensureForeground()
        voiceStateHolder.update {
          it.copy(isServiceRunning = true, isMuted = false, status = VoiceStatus.WAKE_STANDBY, statusHint = "Listening for Hey Riya...")
        }
        startListeningCycle(directTap = false)
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

  private fun startListeningCycle(directTap: Boolean) {
    if (voiceStateHolder.state.value.isMuted) return
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
    mainHandler.post {
      try {
        if (speechRecognizer == null) {
          speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(createRecognitionListener())
          }
        }
        val recognizerIntent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
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
        scheduleRestart(1500L)
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
        val delayMs =
          when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_AUDIO -> 1200L
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 450L
            else -> 1500L
          }
        if (directTapMode && error == SpeechRecognizer.ERROR_NO_MATCH) {
          directTapMode = false
        }
        scheduleRestart(delayMs)
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
      scheduleRestart(400L)
      return
    }
    serviceScope.launch {
      val settings = preferencesRepository.settingsFlow.first()
      val inWindow = System.currentTimeMillis() < conversationWindowUntilMs
      val hasWakeWord = containsWakeWord(spoken)
      val shouldRespond = directTapMode || !settings.wakeWordEnabled || hasWakeWord || inWindow || SafetyGuard.isEmergencyStopCommand(spoken)

      if (!shouldRespond) {
        scheduleRestart(400L)
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

      conversationWindowUntilMs = System.currentTimeMillis() + 18_000L
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
          startListeningCycle(directTap = false)
        }
      }
    }
  }

  private fun containsWakeWord(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("hey riya") ||
      lower.contains("hi riya") ||
      lower.contains("ok riya") ||
      lower.startsWith("riya") ||
      lower.contains("রিয়া")
  }

  private fun stripWakeWord(text: String): String {
    return text
      .replace(Regex("(?i)^(hey|hi|ok)?\\s*riya[,\\s]*"), "")
      .trim()
  }

  private fun scheduleRestart(delayMs: Long) {
    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.postDelayed({
      if (voiceStateHolder.state.value.isServiceRunning && !voiceStateHolder.state.value.isMuted) {
        startListeningCycle(directTap = false)
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
    speechRecognizer?.destroy()
    speechRecognizer = null
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
    speechRecognizer?.destroy()
    speechRecognizer = null
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val CHANNEL_ID = "riya_voice_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_START = "com.example.action.START_VOICE"
    const val ACTION_TAP_TO_TALK = "com.example.action.TAP_TO_TALK"
    const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
    const val ACTION_STOP = "com.example.action.STOP_VOICE"

    fun start(context: Context, action: String = ACTION_START) {
      val intent = Intent(context, RiyaVoiceService::class.java).setAction(action)
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
