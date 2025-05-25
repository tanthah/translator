package com.example.translator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.translator.data.model.UserPreferences
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository

class SettingsViewModel(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    val userPreferences = userRepository.getUserPreferences().asLiveData()
    val supportedLanguages = languageRepository.getAllSupportedLanguages().asLiveData()

    suspend fun updateUserPreferences(preferences: UserPreferences) {
        userRepository.updateUserPreferences(preferences)
    }
}

class SettingsViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userRepository, languageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}