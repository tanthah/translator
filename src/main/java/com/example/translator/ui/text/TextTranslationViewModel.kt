package com.example.translator.ui.text

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TranslationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TextTranslationViewModel(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    context: Context
) : ViewModel() {

    private val translationService = TranslationService(context)

    val supportedLanguages = languageRepository.getAllSupportedLanguages().asLiveData()
    val userPreferences = userRepository.getUserPreferences().asLiveData()

    private val _translationResult = MutableLiveData<String?>()
    val translationResult: LiveData<String?> = _translationResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Job management
    private var translationJob: Job? = null
    private var detectionJob: Job? = null

    fun translateText(text: String, sourceLanguage: String, targetLanguage: String) {
        // Cancel previous translation job
        translationJob?.cancel()

        translationJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Validate input
                if (text.trim().isEmpty()) {
                    _errorMessage.value = "Please enter text to translate"
                    return@launch
                }

                if (text.length > 5000) {
                    _errorMessage.value = "Text too long. Maximum 5000 characters allowed."
                    return@launch
                }

                // Same language check
                if (sourceLanguage == targetLanguage) {
                    _translationResult.value = text
                    return@launch
                }

                val result = translationService.translateText(text, sourceLanguage, targetLanguage)

                if (result != null) {
                    _translationResult.value = result
                } else {
                    _errorMessage.value = "Translation failed. Please check your internet connection."
                }

            } catch (e: TranslationService.NetworkException) {
                _errorMessage.value = "No internet connection available"
            } catch (e: TranslationService.TranslationException) {
                _errorMessage.value = e.message ?: "Translation service error"
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun detectLanguage(text: String) {
        // Cancel previous detection job
        detectionJob?.cancel()

        detectionJob = viewModelScope.launch {
            try {
                if (text.trim().isEmpty()) return@launch

                val detectedLanguage = translationService.detectLanguage(text)

                // Handle detection result if needed
                // This could be used to automatically set source language

            } catch (e: Exception) {
                // Language detection is optional, don't show error to user
                // Just log for debugging
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearResults() {
        _translationResult.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()

        // Cancel ongoing jobs
        translationJob?.cancel()
        detectionJob?.cancel()

        // Close translation service
        translationService.closeTranslators()
    }
}

class TextTranslationViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TextTranslationViewModel::class.java)) {
            return TextTranslationViewModel(userRepository, languageRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}