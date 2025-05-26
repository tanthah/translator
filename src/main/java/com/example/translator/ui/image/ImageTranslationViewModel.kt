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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ImageTranslationViewModel(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    context: Context
) : ViewModel() {

    private val textRecognitionService = TextRecognitionService()
    private val translationService = TranslationService(context)

    val supportedLanguages = languageRepository.getAllSupportedLanguages().asLiveData()
    val userPreferences = userRepository.getUserPreferences().asLiveData()

    private val _detectedText = MutableLiveData<String?>()
    val detectedText: LiveData<String?> = _detectedText

    private val _translationResult = MutableLiveData<String?>()
    val translationResult: LiveData<String?> = _translationResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Job management
    private var processingJob: Job? = null

    companion object {
        private const val TAG = "ImageTranslationViewModel"
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

                if (sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
                    _errorMessage.value = "Please select source and target languages."
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

                // Step 3: Translate the recognized text if languages are different
                if (sourceLanguage == targetLanguage) {
                    Log.d(TAG, "Source and target languages are the same, skipping translation")
                    _translationResult.value = recognizedText
                } else {
                    Log.d(TAG, "Translating text from $sourceLanguage to $targetLanguage...")
                    val translatedText = translationService.translateText(recognizedText, sourceLanguage, targetLanguage)

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
        _translationResult.value = null
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

        // Close services
        try {
            textRecognitionService.close()
            translationService.closeTranslators()
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