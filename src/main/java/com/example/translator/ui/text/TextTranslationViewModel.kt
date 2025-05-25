package com.example.translator.ui.text

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import com.example.translator.services.TranslationService

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

    suspend fun translateText(text: String, sourceLanguage: String, targetLanguage: String) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val result = translationService.translateText(text, sourceLanguage, targetLanguage)
            _translationResult.value = result

            if (result == null) {
                _errorMessage.value = "Translation failed. Please check your internet connection."
            }
        } catch (e: Exception) {
            _errorMessage.value = "Translation error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun detectLanguage(text: String): String? {
        return translationService.detectLanguage(text)
    }

    override fun onCleared() {
        super.onCleared()
        translationService.closeTranslators()
    }
}

class TextTranslationViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TextTranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TextTranslationViewModel(userRepository, languageRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}