package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
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
) : TextToSpeech.OnInitListener {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var tts: TextToSpeech? = null
  private var isReady = false

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
            _isSpeaking.value = false
            onUtteranceDoneCallback?.invoke()
            onUtteranceDoneCallback = null
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            _isSpeaking.value = false
            onUtteranceDoneCallback?.invoke()
            onUtteranceDoneCallback = null
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            _isSpeaking.value = false
            onUtteranceDoneCallback?.invoke()
            onUtteranceDoneCallback = null
          }
        }
      )
      Timber.i("RiyaTtsManager initialized")
    } else {
      Timber.w("TextToSpeech init failed with status $status")
    }
  }

  fun speak(text: String, onDone: (() -> Unit)? = null) {
    val clean = text.replace("*", "").replace("#", "").trim()
    if (clean.isEmpty() || !isReady) {
      onDone?.invoke()
      return
    }
    onUtteranceDoneCallback = onDone
    scope.launch {
      val settings = preferencesRepository.settingsFlow.first()
      val engine = tts ?: return@launch
      selectFemaleVoiceForText(engine, clean)
      engine.setSpeechRate(settings.ttsSpeed)
      engine.setPitch(settings.ttsPitch)
      val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
      _isSpeaking.value = true
      engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }
  }

  fun stop() {
    onUtteranceDoneCallback = null
    tts?.stop()
    _isSpeaking.value = false
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
              v.name.contains("hi-in-x-hie", ignoreCase = true) ||
              v.name.contains("en-in-x-ena", ignoreCase = true) ||
              v.name.contains("sfg", ignoreCase = true) ||
              v.name.contains("network", ignoreCase = true).not())
        }
        ?.firstOrNull()

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
