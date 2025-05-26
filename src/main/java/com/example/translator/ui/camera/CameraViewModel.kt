package com.example.translator.ui.camera

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TextRecognitionService
import com.example.translator.services.TranslationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    // Jobs for cancellation
    private var recognitionJob: Job? = null
    private var translationJob: Job? = null

    suspend fun recognizeText(inputImage: InputImage): String? {
        // Cancel previous recognition job
        recognitionJob?.cancel()

        return try {
            _isLoading.value = true
            _errorMessage.value = null

            val recognizedText = textRecognitionService.recognizeTextFromImage(inputImage)
            _detectedText.value = recognizedText
            recognizedText
        } catch (e: Exception) {
            handleError("Text recognition failed", e)
            null
        } finally {
            _isLoading.value = false
        }
    }

    // Add this method that was missing
    suspend fun recognzeText(inputImage: InputImage): String? {
        return recognizeText(inputImage)
    }

    fun translateDetectedText(text: String, sourceLanguage: String, targetLanguage: String) {
        // Cancel previous translation job
        translationJob?.cancel()

        translationJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val result = translationService.translateText(text, sourceLanguage, targetLanguage)
                _translationResult.value = result

                if (result == null) {
                    _errorMessage.value = "Translation failed. Please check your internet connection."
                }
            } catch (e: Exception) {
                handleError("Translation failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun handleError(message: String, exception: Exception) {
        val errorMsg = when {
            exception.message?.contains("network", ignoreCase = true) == true ->
                "No internet connection available"
            exception.message?.contains("timeout", ignoreCase = true) == true ->
                "Request timed out. Please try again."
            else -> "$message: ${exception.message ?: "Unknown error"}"
        }
        _errorMessage.value = errorMsg
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearResults() {
        _detectedText.value = null
        _translationResult.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()

        // Cancel ongoing jobs
        recognitionJob?.cancel()
        translationJob?.cancel()

        // Close services
        textRecognitionService.close()
        translationService.closeTranslators()
    }
}

class CameraViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            return CameraViewModel(userRepository, languageRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}