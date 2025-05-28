package com.example.translator.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

class SpeechService(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    private var isSpeaking = false
    private var isListening = false

    // Speech rate settings
    private var speechRate = 1.0f // Normal speed
    private var speechPitch = 1.0f // Normal pitch

    companion object {
        private const val TAG = "SpeechService"
        private const val MAX_TEXT_LENGTH = 4000
        private const val TTS_UTTERANCE_ID = "TTS_UTTERANCE"

        // Speed constants
        const val SPEED_VERY_SLOW = 0.5f
        const val SPEED_SLOW = 0.75f
        const val SPEED_NORMAL = 1.0f
        const val SPEED_FAST = 1.25f
        const val SPEED_VERY_FAST = 1.5f
    }

    fun initializeTextToSpeech(onInitComplete: (Boolean) -> Unit) {
        try {
            // Clean up existing instance
            textToSpeech?.stop()
            textToSpeech?.shutdown()

            textToSpeech = TextToSpeech(context) { status ->
                isInitialized = status == TextToSpeech.SUCCESS

                if (isInitialized) {
                    setupTTSListener()
                    // Set default speech rate and pitch
                    textToSpeech?.setSpeechRate(speechRate)
                    textToSpeech?.setPitch(speechPitch)
                    Log.d(TAG, "TextToSpeech initialized successfully")
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                }

                onInitComplete(isInitialized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech", e)
            onInitComplete(false)
        }
    }

    private fun setupTTSListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                Log.d(TAG, "TTS started speaking")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                Log.d(TAG, "TTS finished speaking")
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                Log.e(TAG, "TTS error occurred")
            }
        })
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        textToSpeech?.setSpeechRate(speechRate)
    }

    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.1f, 2.0f)
        textToSpeech?.setPitch(speechPitch)
    }

    fun getSpeechRate(): Float = speechRate

    fun getSpeechPitch(): Float = speechPitch

    fun speakText(text: String, languageCode: String, rate: Float = speechRate) {
        if (!isInitialized) {
            Log.w(TAG, "TextToSpeech not initialized")
            return
        }

        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) {
            Log.w(TAG, "Invalid text for TTS: length=${text.length}")
            return
        }

        try {
            val locale = Locale.forLanguageTag(languageCode)
            val result = textToSpeech?.setLanguage(locale)

            when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Language not supported: $languageCode, using default")
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    Log.d(TAG, "Language set successfully: $languageCode")
                }
            }

            // Stop any ongoing speech
            stopSpeaking()

            // Set speech rate for this utterance
            textToSpeech?.setSpeechRate(rate)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_UTTERANCE_ID)
            }

            val speakResult = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, TTS_UTTERANCE_ID)

            if (speakResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Failed to start TTS with result: $speakResult")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in speakText", e)
        }
    }

    fun stopSpeaking() {
        try {
            if (isSpeaking) {
                textToSpeech?.stop()
                isSpeaking = false
                Log.d(TAG, "TTS stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun startSpeechRecognition(languageCode: String): Flow<SpeechResult> = callbackFlow {
        try {
            if (isListening) {
                stopSpeechRecognition()
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                trySend(SpeechResult.Error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
                close()
                return@callbackFlow
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            isListening = true

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Speech recognition ready")
                    trySend(SpeechResult.Ready)
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech recognition started")
                    trySend(SpeechResult.Speaking)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SpeechResult.RmsChanged(rmsdB))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    results?.firstOrNull()?.let { result ->
                        if (result.isNotBlank()) {
                            trySend(SpeechResult.PartialResult(result))
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val resultList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val finalResult = resultList?.firstOrNull()

                    if (!finalResult.isNullOrBlank()) {
                        Log.d(TAG, "Speech recognition completed: $finalResult")
                        trySend(SpeechResult.FinalResult(finalResult))
                    } else {
                        Log.w(TAG, "Speech recognition returned empty result")
                        trySend(SpeechResult.Error(SpeechRecognizer.ERROR_NO_MATCH))
                    }

                    isListening = false
                    close()
                }

                override fun onError(error: Int) {
                    val errorMessage = getSpeechErrorMessage(error)
                    Log.e(TAG, "Speech recognition error: $errorMessage (code: $error)")
                    trySend(SpeechResult.Error(error))
                    isListening = false
                    close()
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Not needed for this implementation
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech recognition ended")
                    isListening = false
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Handle speech recognition events
                    Log.d(TAG, "Speech recognition event: $eventType")
                }
            })

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            trySend(SpeechResult.Error(SpeechRecognizer.ERROR_CLIENT))
            isListening = false
            close()
        }

        awaitClose {
            stopSpeechRecognition()
        }
    }

    fun stopSpeechRecognition() {
        try {
            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
                Log.d(TAG, "Speech recognition stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    private fun getSpeechErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server sends error status"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }

    fun release() {
        try {
            // Stop any ongoing operations
            stopSpeaking()
            stopSpeechRecognition()

            // Release TTS
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null

            // Release speech recognizer
            speechRecognizer?.destroy()
            speechRecognizer = null

            // Reset flags
            isInitialized = false
            isSpeaking = false
            isListening = false

            Log.d(TAG, "SpeechService resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SpeechService resources", e)
        }
    }

    sealed class SpeechResult {
        object Ready : SpeechResult()
        object Speaking : SpeechResult()
        data class RmsChanged(val rmsdB: Float) : SpeechResult()
        data class PartialResult(val text: String) : SpeechResult()
        data class FinalResult(val text: String) : SpeechResult()
        data class Error(val errorCode: Int) : SpeechResult()
    }
}