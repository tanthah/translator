package com.example.translator

import android.app.Application
import androidx.lifecycle.lifecycleScope
import com.example.translator.data.local.AppDatabase
import com.example.translator.data.repository.LanguageRepository
import com.example.translator.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TranslatorApplication : Application() {

    // Application scope for coroutines
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.getDatabase(this) }
    val languageRepository by lazy { LanguageRepository(this) }
    val userRepository by lazy { UserRepository(this) }

    override fun onCreate() {
        super.onCreate()

        // Initialize supported languages data in a coroutine
        applicationScope.launch {
            try {
                languageRepository.initializeSupportedLanguages()
            } catch (e: Exception) {
                // Handle initialization error gracefully
                e.printStackTrace()
            }
        }
    }
}