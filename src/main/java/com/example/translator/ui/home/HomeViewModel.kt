package com.example.translator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository

class HomeViewModel(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    val userPreferences = userRepository.getUserPreferences().asLiveData()
    val supportedLanguages = languageRepository.getAllSupportedLanguages().asLiveData()

    suspend fun initializeDefaultPreferences() {
        userRepository.initializeDefaultPreferences()
    }
}

class HomeViewModelFactory(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userRepository, languageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}