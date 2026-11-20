package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SecretRepository
import com.example.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiyaTtsManager
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val preferencesRepository: PreferencesRepository,
  private val secretRepository: SecretRepository,
  private val okHttpClient: OkHttpClient,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TextToSpeech.OnInitListener {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mainHandler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = null
  private var isReady = false

  private var mediaPlayer: MediaPlayer? = null
  private var activeElevenLabsJob: Job? = null
  private var pendingSystemUtterance: String? = null

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private var onUtteranceDoneCallback: (() -> Unit)? = null

  init {
    tts = TextToSpeech(context, this)
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isReady = true
      tts?.setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
          }

          override fun onDone(utteranceId: String?) {
            finishSpeakingOnMain()
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            finishSpeakingOnMain()
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            finishSpeakingOnMain()
          }
        }
      )
      Timber.i("RiyaTtsManager initialized")
      val queued = pendingSystemUtterance
      pendingSystemUtterance = null
      if (!queued.isNullOrBlank()) {
        speakWithSystemTts(queued)
      }
    } else {
      Timber.w("TextToSpeech init failed with status $status")
      pendingSystemUtterance = null
      finishSpeakingOnMain()
    }
  }

  fun speak(text: String, onDone: (() -> Unit)? = null) {
    val clean = text.replace("*", "").replace("#", "").trim()
    if (clean.isEmpty()) {
      mainHandler.post { onDone?.invoke() }
      return
    }

    stopCurrentPlaybackOnly()
    onUtteranceDoneCallback = onDone

    val elevenKey = secretRepository.getStoredElevenLabsApiKey()
    if (!elevenKey.isNullOrBlank()) {
      val voiceId = secretRepository.getStoredElevenLabsVoiceId()
      activeElevenLabsJob =
        scope.launch {
          _isSpeaking.value = true
          val played = trySpeakWithElevenLabs(elevenKey, voiceId, clean)
          if (!played) {
            speakWithSystemTts(clean)
          }
        }
    } else {
      speakWithSystemTts(clean)
    }
  }

  private suspend fun trySpeakWithElevenLabs(
    apiKey: String,
    voiceId: String,
    text: String,
  ): Boolean {
    return try {
      val settings = preferencesRepository.settingsFlow.first()
      val audioFile =
        withContext(ioDispatcher) {
          val payload =
            JSONObject()
              .put("text", text)
              .put("model_id", "eleven_flash_v2_5")
              .put(
                "voice_settings",
                JSONObject()
                  .put("stability", 0.42)
                  .put("similarity_boost", 0.82)
                  .put("style", 0.35)
                  .put("use_speaker_boost", true)
                  .put("speed", settings.ttsSpeed.coerceIn(0.7f, 1.2f).toDouble()),
              )
              .toString()

          val request =
            Request.Builder()
              .url(
                "https://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream?optimize_streaming_latency=4&output_format=mp3_44100_128"
              )
              .header("xi-api-key", apiKey)
              .header("Accept", "audio/mpeg")
              .post(payload.toRequestBody("application/json".toMediaType()))
              .build()

          okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
              Timber.w("ElevenLabs HTTP ${resp.code}, falling back to Android TTS")
              return@withContext null
            }
            val bytes = resp.body?.bytes() ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null
            val outFile = File(context.cacheDir, "riya_voice_${System.currentTimeMillis()}.mp3")
            outFile.writeBytes(bytes)
            outFile
          }
        } ?: return false

      playAudioFile(audioFile)
      true
    } catch (e: Exception) {
      Timber.w(e, "ElevenLabs TTS failed, falling back to system TTS")
      false
    }
  }

  private fun playAudioFile(file: File) {
    try {
      releaseMediaPlayer()
      val player =
        MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          setDataSource(file.absolutePath)
          setOnPreparedListener {
            _isSpeaking.value = true
            it.start()
          }
          setOnCompletionListener {
            runCatching { file.delete() }
            releaseMediaPlayer()
            finishSpeakingOnMain()
          }
          setOnErrorListener { _, _, _ ->
            runCatching { file.delete() }
            releaseMediaPlayer()
            finishSpeakingOnMain()
            true
          }
          prepareAsync()
        }
      mediaPlayer = player
    } catch (e: Exception) {
      Timber.e(e, "MediaPlayer failed for ElevenLabs audio")
      runCatching { file.delete() }
      finishSpeakingOnMain()
    }
  }

  private fun speakWithSystemTts(clean: String) {
    if (!isReady) {
      pendingSystemUtterance = clean
      return
    }
    scope.launch {
      val settings = preferencesRepository.settingsFlow.first()
      val engine = tts ?: run {
        finishSpeakingOnMain()
        return@launch
      }
      selectFemaleVoiceForText(engine, clean)
      engine.setSpeechRate(settings.ttsSpeed.coerceIn(0.7f, 1.4f))
      // Slightly sweet feminine pitch uplift when using default Android TTS
      engine.setPitch((settings.ttsPitch * 1.04f).coerceIn(0.85f, 1.45f))
      val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
      _isSpeaking.value = true
      engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }
  }

  fun stop() {
    onUtteranceDoneCallback = null
    pendingSystemUtterance = null
    stopCurrentPlaybackOnly()
    _isSpeaking.value = false
  }

  private fun stopCurrentPlaybackOnly() {
    activeElevenLabsJob?.cancel()
    activeElevenLabsJob = null
    releaseMediaPlayer()
    tts?.stop()
  }

  private fun releaseMediaPlayer() {
    mediaPlayer?.let { mp ->
      runCatching {
        if (mp.isPlaying) mp.stop()
        mp.reset()
        mp.release()
      }
    }
    mediaPlayer = null
  }

  private fun finishSpeakingOnMain() {
    mainHandler.post {
      _isSpeaking.value = false
      val cb = onUtteranceDoneCallback
      onUtteranceDoneCallback = null
      cb?.invoke()
    }
  }

  fun shutdown() {
    stop()
    tts?.shutdown()
    tts = null
    isReady = false
  }

  private fun selectFemaleVoiceForText(engine: TextToSpeech, text: String) {
    val hasBengaliScript = text.any { it in '\u0980'..'\u09FF' }
    val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
    val targetLocale =
      when {
        hasBengaliScript -> Locale.forLanguageTag("bn-IN")
        hasDevanagari -> Locale.forLanguageTag("hi-IN")
        else -> Locale.forLanguageTag("en-IN")
      }

    val voices = runCatching { engine.voices }.getOrNull()
    val matchingFemale: Voice? =
      voices
        ?.filter { v ->
          v.locale.language == targetLocale.language &&
            (v.name.contains("female", ignoreCase = true) ||
              v.name.contains("bn-in-x-bnf", ignoreCase = true) ||
              v.name.contains("bn-in-x-bnd", ignoreCase = true) ||
              v.name.contains("hi-in-x-hie", ignoreCase = true) ||
              v.name.contains("hi-in-x-hid", ignoreCase = true) ||
              v.name.contains("en-in-x-ena", ignoreCase = true) ||
              v.name.contains("en-in-x-enc", ignoreCase = true) ||
              v.name.contains("sfg", ignoreCase = true))
        }
        ?.sortedBy { if (it.isNetworkConnectionRequired) 1 else 0 }
        ?.firstOrNull()
        ?: voices
          ?.firstOrNull { v ->
            v.locale.language == targetLocale.language && !v.name.contains("male", ignoreCase = true)
          }

    if (matchingFemale != null) {
      engine.voice = matchingFemale
    } else {
      val langSupport = engine.setLanguage(targetLocale)
      if (langSupport == TextToSpeech.LANG_MISSING_DATA || langSupport == TextToSpeech.LANG_NOT_SUPPORTED) {
        engine.setLanguage(Locale.US)
      }
    }
  }
}
