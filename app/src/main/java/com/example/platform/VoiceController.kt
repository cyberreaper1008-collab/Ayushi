package com.example.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.PreferredLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

private const val TAG = "JarvisVoice"

data class VoiceOption(
    val name: String,
    val localeDisplay: String
)

class VoiceController(
    private val context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onSpeechFinished: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var userCancelledRecognition = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f
    private var currentLanguage = PreferredLanguage.Hinglish

    init {
        scope.launch {
            delay(150)
            initTts()
        }
    }

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun canLaunchSystemSpeechIntent(): Boolean =
        createSystemSpeechIntent(currentLanguage).resolveActivity(context.packageManager) != null

    fun createSystemSpeechIntent(language: PreferredLanguage = currentLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to J.A.R.V.I.S. (${language.label})")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    applyLanguageAndVoice(currentLanguage, _selectedVoiceName.value)
                    scope.launch { refreshAvailableVoices() }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            scope.launch(Dispatchers.Main.immediate) {
                                _isSpeaking.value = false
                                onSpeechFinished()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            scope.launch(Dispatchers.Main.immediate) {
                                _isSpeaking.value = false
                            }
                        }
                    })
                } else {
                    Log.w(TAG, "TTS initialization failed with status $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextToSpeech", e)
        }
    }

    private suspend fun refreshAvailableVoices() {
        val voices = withContext(Dispatchers.IO) {
            try {
                tts?.voices
                    ?.filter {
                        val lang = it.locale.language
                        lang == "en" || lang == "hi"
                    }
                    ?.sortedBy { it.locale.displayName }
                    ?.take(16)
                    ?.map { VoiceOption(it.name, "${it.locale.displayLanguage} (${it.locale.country})") }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        _availableVoices.value = voices
    }

    fun updateSettings(language: PreferredLanguage, speed: Float, pitch: Float) {
        currentLanguage = language
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.5f, 1.8f)
        if (ttsReady) {
            tts?.setSpeechRate(currentSpeed)
            tts?.setPitch(currentPitch)
            applyLanguageAndVoice(language, _selectedVoiceName.value)
        }
    }

    fun selectVoice(voiceName: String?) {
        _selectedVoiceName.value = voiceName
        if (ttsReady) {
            applyLanguageAndVoice(currentLanguage, voiceName)
        }
    }

    private fun applyLanguageAndVoice(language: PreferredLanguage, voiceName: String?) {
        val locale = when (language) {
            PreferredLanguage.Hindi -> Locale("hi", "IN")
            PreferredLanguage.Hinglish -> Locale("en", "IN")
            PreferredLanguage.English -> Locale.US
        }
        tts?.language = locale
        if (!voiceName.isNullOrBlank()) {
            val match = tts?.voices?.firstOrNull { it.name == voiceName }
            if (match != null) {
                tts?.voice = match
            }
        }
    }

    fun startListening(language: PreferredLanguage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main.immediate) {
                startListeningOnMain(language)
            }
        } else {
            startListeningOnMain(language)
        }
    }

    private fun startListeningOnMain(language: PreferredLanguage) {
        // Voice interruption: stop TTS immediately when user initiates listening
        stopSpeaking()
        _voiceError.value = null
        _partialTranscript.value = ""
        userCancelledRecognition = false

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _isListening.value = false
            _voiceError.value = "In-app speech service unavailable on this device."
            return
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                    _isListening.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Android SpeechRecognizer reports roughly -2..10 dB
                    _rmsDb.value = (rmsdB + 2f).coerceIn(0f, 12f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rmsDb.value = 0f
                }

                override fun onError(error: Int) {
                    val wasCancelled = userCancelledRecognition
                    val partialFallback = _partialTranscript.value.trim()
                    _isListening.value = false
                    _rmsDb.value = 0f

                    if (wasCancelled) {
                        userCancelledRecognition = false
                        return
                    }

                    if (error == SpeechRecognizer.ERROR_NO_MATCH && partialFallback.isNotEmpty()) {
                        _partialTranscript.value = ""
                        onTranscriptReady(partialFallback)
                        return
                    }

                    _voiceError.value = mapSpeechError(error)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsDb.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty().trim()
                        .ifEmpty { _partialTranscript.value.trim() }
                    _partialTranscript.value = ""
                    if (text.isNotEmpty()) {
                        onTranscriptReady(text)
                    } else {
                        _voiceError.value = "Didn't catch that. Please try speaking again."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        _partialTranscript.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            _isListening.value = true
            recognizer.startListening(createSystemSpeechIntent(language))
        } catch (e: Exception) {
            _isListening.value = false
            _rmsDb.value = 0f
            _voiceError.value = e.localizedMessage ?: "Failed to start voice input."
        }
    }

    fun stopListening() {
        val action = {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            } finally {
                _isListening.value = false
                _rmsDb.value = 0f
            }
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main.immediate) { action() }
        } else {
            action()
        }
    }

    fun cancelListening() {
        val action = {
            userCancelledRecognition = true
            _partialTranscript.value = ""
            _voiceError.value = null
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            } finally {
                _isListening.value = false
                _rmsDb.value = 0f
            }
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main.immediate) { action() }
        } else {
            action()
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val cleaned = text
            .replace(Regex("[*#`_]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
        if (cleaned.isBlank()) return

        // Auto-detect Devanagari script for Hindi pronunciation if needed
        val hasDevanagari = cleaned.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) {
            tts?.language = Locale("hi", "IN")
        } else {
            applyLanguageAndVoice(currentLanguage, _selectedVoiceName.value)
        }

        tts?.setSpeechRate(currentSpeed)
        tts?.setPitch(currentPitch)
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
        }
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    fun release() {
        scope.cancel()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun mapSpeechError(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio capture error."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer reset. Please tap the mic again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required to listen."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Network error during speech recognition. Check internet connectivity."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking clearly."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please wait a moment and try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server unavailable."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Selected voice language pack is unavailable on this device."
            else -> "Voice recognition unavailable (code $errorCode)."
        }
    }
}
