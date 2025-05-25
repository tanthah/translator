package com.example.translator

import android.app.Application
import com.example.translator.data.local.AppDatabase
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository

class TranslatorApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val languageRepository by lazy { LanguageRepository(this) }
    val userRepository by lazy { UserRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Initialize supported languages data
        languageRepository.initializeSupportedLanguages()
    }
}