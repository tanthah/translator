package com.example.translator.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TextRecognitionService
import com.example.translator.services.TranslationService
import com.example.translator.services.TextSummarizationService
import com.example.translator.services.SpeechService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ImageTranslationViewModel(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    context: Context
) : ViewModel() {

    private val textRecognitionService = TextRecognitionService()
    private val translationService = TranslationService(context)
    private val summarizationService = TextSummarizationService(context)
    private val speechService = SpeechService(context)

    val supportedLanguages = languageRepository.getAllSupportedLanguages().asLiveData()
    val userPreferences = userRepository.getUserPreferences().asLiveData()

    private val _detectedText = MutableLiveData<String?>()
    val detectedText: LiveData<String?> = _detectedText

    private val _detectedLanguage = MutableLiveData<String?>()
    val detectedLanguage: LiveData<String?> = _detectedLanguage

    private val _detectedLanguageName = MutableLiveData<String?>()
    val detectedLanguageName: LiveData<String?> = _detectedLanguageName

    private val _translationResult = MutableLiveData<String?>()
    val translationResult: LiveData<String?> = _translationResult

    private val _summaryResult = MutableLiveData<String?>()
    val summaryResult: LiveData<String?> = _summaryResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isDetectingLanguage = MutableLiveData<Boolean>()
    val isDetectingLanguage: LiveData<Boolean> = _isDetectingLanguage

    private val _isSummarizing = MutableLiveData<Boolean>()
    val isSummarizing: LiveData<Boolean> = _isSummarizing

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _speechRate = MutableLiveData<Float>()
    val speechRate: LiveData<Float> = _speechRate

    // Job management
    private var processingJob: Job? = null
    private var summarizationJob: Job? = null
    private var languageDetectionJob: Job? = null

    // Speech settings
    private var currentSpeechRate = SpeechService.SPEED_NORMAL

    companion object {
        private const val TAG = "ImageTranslationViewModel"
    }

    init {
        // Initialize speech service
        speechService.initializeTextToSpeech { success ->
            if (!success) {
                _errorMessage.value = "Text-to-speech not available"
            }
        }
        _speechRate.value = currentSpeechRate
    }

    suspend fun processImage(bitmap: Bitmap, sourceLanguage: String, targetLanguage: String) {
        // Cancel any ongoing processing
        processingJob?.cancel()

        processingJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                Log.d(TAG, "Starting image processing...")

                // Step 1: Validate inputs
                if (!isValidBitmap(bitmap)) {
                    _errorMessage.value = "Invalid image. Please select a different image."
                    return@launch
                }

                if (targetLanguage.isEmpty()) {
                    _errorMessage.value = "Please select target language."
                    return@launch
                }

                // Step 2: Recognize text from image
                Log.d(TAG, "Recognizing text from image...")
                val recognizedText = textRecognitionService.recognizeTextFromBitmap(bitmap)

                if (recognizedText.isNullOrEmpty()) {
                    Log.w(TAG, "No text detected in image")
                    _detectedText.value = null
                    _errorMessage.value = "No text detected in the selected area. Try selecting a different area or image with clearer text."
                    return@launch
                }

                Log.d(TAG, "Text recognized: ${recognizedText.length} characters")
                _detectedText.value = recognizedText

                // Step 3: Auto-detect language if sourceLanguage is "auto" or empty
                var actualSourceLanguage = sourceLanguage
                if (sourceLanguage == "auto" || sourceLanguage.isEmpty()) {
                    Log.d(TAG, "Auto-detecting language...")
                    _isDetectingLanguage.value = true

                    try {
                        val detectedLang = translationService.detectLanguage(recognizedText)
                        if (!detectedLang.isNullOrEmpty() && detectedLang != "und") {
                            actualSourceLanguage = detectedLang
                            _detectedLanguage.value = detectedLang

                            // Get language name for display
                            val language = languageRepository.getLanguageByCode(detectedLang)
                            _detectedLanguageName.value = language?.languageName ?: detectedLang

                            Log.d(TAG, "Language detected: $detectedLang (${language?.languageName})")
                        } else {
                            Log.w(TAG, "Could not detect language, using English as default")
                            actualSourceLanguage = "en"
                            _detectedLanguage.value = "en"
                            _detectedLanguageName.value = "English (Auto-detected)"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Language detection failed", e)
                        actualSourceLanguage = "en"
                        _detectedLanguage.value = "en"
                        _detectedLanguageName.value = "English (Default)"
                    } finally {
                        _isDetectingLanguage.value = false
                    }
                }

                // Step 4: Translate the recognized text if languages are different
                if (actualSourceLanguage == targetLanguage) {
                    Log.d(TAG, "Source and target languages are the same, skipping translation")
                    _translationResult.value = recognizedText
                } else {
                    Log.d(TAG, "Translating text from $actualSourceLanguage to $targetLanguage...")
                    val translatedText = translationService.translateText(recognizedText, actualSourceLanguage, targetLanguage)

                    if (translatedText.isNullOrEmpty()) {
                        Log.w(TAG, "Translation failed or returned empty result")
                        _translationResult.value = null
                        _errorMessage.value = "Translation failed. Please check your internet connection and try again."
                    } else {
                        Log.d(TAG, "Translation successful: ${translatedText.length} characters")
                        _translationResult.value = translatedText
                    }
                }

            } catch (e: TranslationService.NetworkException) {
                Log.e(TAG, "Network error during processing", e)
                _errorMessage.value = "No internet connection. Please check your network and try again."
            } catch (e: TranslationService.TranslationException) {
                Log.e(TAG, "Translation error during processing", e)
                _errorMessage.value = e.message ?: "Translation service error. Please try again."
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during processing", e)
                _errorMessage.value = "An unexpected error occurred: ${e.message ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // New method for processing with auto-detect
    suspend fun processImageWithAutoDetect(bitmap: Bitmap, targetLanguage: String) {
        processImage(bitmap, "auto", targetLanguage)
    }

    fun detectLanguageFromText(text: String) {
        languageDetectionJob?.cancel()

        languageDetectionJob = viewModelScope.launch {
            try {
                _isDetectingLanguage.value = true
                _errorMessage.value = null

                if (text.trim().isEmpty()) {
                    _errorMessage.value = "No text available for language detection"
                    return@launch
                }

                Log.d(TAG, "Detecting language for text...")
                val detectedLang = translationService.detectLanguage(text)

                if (!detectedLang.isNullOrEmpty() && detectedLang != "und") {
                    _detectedLanguage.value = detectedLang

                    // Get language name for display
                    val language = languageRepository.getLanguageByCode(detectedLang)
                    _detectedLanguageName.value = language?.languageName ?: detectedLang

                    Log.d(TAG, "Language detected: $detectedLang (${language?.languageName})")
                } else {
                    _detectedLanguage.value = null
                    _detectedLanguageName.value = null
                    _errorMessage.value = "Could not detect language. Please select manually."
                }

            } catch (e: Exception) {
                Log.e(TAG, "Language detection failed", e)
                _errorMessage.value = "Language detection failed: ${e.message}"
                _detectedLanguage.value = null
                _detectedLanguageName.value = null
            } finally {
                _isDetectingLanguage.value = false
            }
        }
    }

    fun summarizeDetectedText(
        summaryType: TextSummarizationService.SummaryType = TextSummarizationService.SummaryType.BRIEF,
        targetLanguage: String = "en"
    ) {
        val textToSummarize = _detectedText.value

        if (textToSummarize.isNullOrEmpty()) {
            _errorMessage.value = "No text available to summarize"
            return
        }

        summarizationJob?.cancel()
        summarizationJob = viewModelScope.launch {
            try {
                _isSummarizing.value = true
                _errorMessage.value = null

                Log.d(TAG, "Summarizing text...")
                val result = summarizationService.summarizeText(textToSummarize, summaryType, targetLanguage)

                when (result) {
                    is TextSummarizationService.SummaryResult.Success -> {
                        _summaryResult.value = result.summary
                        Log.d(TAG, "Summarization successful")
                    }
                    is TextSummarizationService.SummaryResult.Error -> {
                        _errorMessage.value = result.message
                        Log.e(TAG, "Summarization failed: ${result.message}")
                    }
                    else -> {
                        _errorMessage.value = "Unknown summarization result"
                        Log.e(TAG, "Unknown summarization result type")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during summarization", e)
                _errorMessage.value = "Summarization failed: ${e.message}"
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    fun speakDetectedText(languageCode: String) {
        val text = _detectedText.value
        if (text.isNullOrEmpty()) {
            _errorMessage.value = "No detected text to speak"
            return
        }

        try {
            speechService.speakText(text, languageCode, currentSpeechRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking detected text", e)
            _errorMessage.value = "Failed to speak text"
        }
    }

    fun speakTranslatedText(languageCode: String) {
        val text = _translationResult.value
        if (text.isNullOrEmpty()) {
            _errorMessage.value = "No translated text to speak"
            return
        }

        try {
            speechService.speakText(text, languageCode, currentSpeechRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking translated text", e)
            _errorMessage.value = "Failed to speak translation"
        }
    }

    fun speakSummary(languageCode: String) {
        val text = _summaryResult.value
        if (text.isNullOrEmpty()) {
            _errorMessage.value = "No summary to speak"
            return
        }

        try {
            speechService.speakText(text, languageCode, currentSpeechRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking summary", e)
            _errorMessage.value = "Failed to speak summary"
        }
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(SpeechService.SPEED_VERY_SLOW, SpeechService.SPEED_VERY_FAST)
        speechService.setSpeechRate(currentSpeechRate)
        _speechRate.value = currentSpeechRate
    }

    fun stopSpeaking() {
        speechService.stopSpeaking()
    }

    fun getSpeechRateText(rate: Float): String {
        return when {
            rate <= SpeechService.SPEED_VERY_SLOW -> "Very Slow"
            rate <= SpeechService.SPEED_SLOW -> "Slow"
            rate <= SpeechService.SPEED_NORMAL -> "Normal"
            rate <= SpeechService.SPEED_FAST -> "Fast"
            rate <= SpeechService.SPEED_VERY_FAST -> "Very Fast"
            else -> "Custom"
        }
    }

    private fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
                !bitmap.isRecycled &&
                bitmap.width > 0 &&
                bitmap.height > 0 &&
                bitmap.width <= 4096 &&
                bitmap.height <= 4096
    }

    fun clearResults() {
        _detectedText.value = null
        _detectedLanguage.value = null
        _detectedLanguageName.value = null
        _translationResult.value = null
        _summaryResult.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "ViewModel cleared, cleaning up resources")

        // Cancel ongoing processing
        processingJob?.cancel()
        summarizationJob?.cancel()
        languageDetectionJob?.cancel()

        // Close services
        try {
            textRecognitionService.close()
            translationService.closeTranslators()
            summarizationService.close()
            speechService.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing services", e)
        }
    }
}

class ImageTranslationViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageTranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageTranslationViewModel(userRepository, languageRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}