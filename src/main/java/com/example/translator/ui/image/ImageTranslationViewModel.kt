package com.example.translator.ui.image

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TextRecognitionService
import com.example.translator.services.TranslationService

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

    suspend fun processImage(bitmap: Bitmap, sourceLanguage: String, targetLanguage: String) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            // Step 1: Recognize text from image
            val recognizedText = textRecognitionService.recognizeTextFromBitmap(bitmap)
            _detectedText.value = recognizedText

            if (recognizedText.isNullOrEmpty()) {
                _errorMessage.value = "No text detected in the image"
                return
            }

            // Step 2: Translate the recognized text
            val translatedText = translationService.translateText(recognizedText, sourceLanguage, targetLanguage)
            _translationResult.value = translatedText

            if (translatedText == null) {
                _errorMessage.value = "Translation failed"
            }

        } catch (e: Exception) {
            _errorMessage.value = "Processing failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        textRecognitionService.close()
        translationService.closeTranslators()
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
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}