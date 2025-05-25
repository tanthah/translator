package com.example.translator.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

class SpeechService(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    fun initializeTextToSpeech(onInitComplete: (Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            onInitComplete(status == TextToSpeech.SUCCESS)
        }
    }

    fun speakText(text: String, languageCode: String) {
        textToSpeech?.let { tts ->
            val locale = Locale.forLanguageTag(languageCode)
            tts.language = locale
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun startSpeechRecognition(languageCode: String): Flow<SpeechResult> = callbackFlow {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechResult.Speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechResult.RmsChanged(rmsdB))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                results?.firstOrNull()?.let {
                    trySend(SpeechResult.PartialResult(it))
                }
            }

            override fun onResults(results: Bundle?) {
                val resultList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                resultList?.firstOrNull()?.let {
                    trySend(SpeechResult.FinalResult(it))
                }
            }

            override fun onError(error: Int) {
                trySend(SpeechResult.Error(error))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
        })

        speechRecognizer?.startListening(intent)

        awaitClose {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
    }

    fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
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