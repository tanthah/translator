package com.example.translator.ui.camera

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.google.mlkit.vision.common.InputImage
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TextRecognitionService
import com.example.translator.services.TranslationService

class CameraViewModel(
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

    suspend fun recognizeText(inputImage: InputImage): String? {
        _isLoading.value = true

        return try {
            val recognizedText = textRecognitionService.recognizeTextFromImage(inputImage)
            _detectedText.value = recognizedText
            recognizedText
        } catch (e: Exception) {
            _errorMessage.value = "Text recognition failed: ${e.message}"
            null
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun translateDetectedText(text: String, sourceLanguage: String, targetLanguage: String) {
        _isLoading.value = true

        try {
            val result = translationService.translateText(text, sourceLanguage, targetLanguage)
            _translationResult.value = result

            if (result == null) {
                _errorMessage.value = "Translation failed"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Translation error: ${e.message}"
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

class CameraViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(userRepository, languageRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}