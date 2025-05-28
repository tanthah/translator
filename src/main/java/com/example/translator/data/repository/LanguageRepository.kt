package com.example.translator.data.repository

import android.content.Context
import com.example.translator.data.local.AppDatabase
import com.example.translator.data.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageRepository(context: Context) {
    private val languageDao = AppDatabase.getDatabase(context).languageDao()

    fun getAllSupportedLanguages(): Flow<List<Language>> {
        return languageDao.getAllSupportedLanguages()
    }

    suspend fun getLanguageByCode(code: String): Language? {
        return withContext(Dispatchers.IO) {
            languageDao.getLanguageByCode(code)
        }
    }

    suspend fun initializeSupportedLanguages() {
        withContext(Dispatchers.IO) {
            val supportedLanguages = listOf(
                Language("af", "Afrikaans", "Afrikaans"),
                Language("ar", "Arabic", "العربية"),
                Language("zh", "Chinese", "中文"),
                Language("cs", "Czech", "Čeština"),
                Language("da", "Danish", "Dansk"),
                Language("nl", "Dutch", "Nederlands"),
                Language("en", "English", "English", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("fi", "Finnish", "Suomi"),
                Language("fr", "French", "Français", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("de", "German", "Deutsch", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("hi", "Hindi", "हिन्दी"),
                Language("it", "Italian", "Italiano", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("ja", "Japanese", "日本語", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("ko", "Korean", "한국어", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("pl", "Polish", "Polski"),
                Language("pt", "Portuguese", "Português"),
                Language("ru", "Russian", "Русский"),
                Language("es", "Spanish", "Español", supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("sv", "Swedish", "Svenska"),
                Language("th", "Thai", "ไทย"),
                Language("tr", "Turkish", "Türkçe"),
                Language("vi", "Vietnamese", "Tiếng Việt", supportsVoiceTranslation = true, supportsCameraTranslation = true)
            )

            languageDao.insertLanguages(supportedLanguages)
        }
    }
}