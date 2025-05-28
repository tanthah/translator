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
                // Major languages with full support
                Language("af", "Afrikaans", "Afrikaans", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ar", "Arabic", "العربية", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("bg", "Bulgarian", "Български", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("bn", "Bengali", "বাংলা", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ca", "Catalan", "Català", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("zh", "Chinese", "中文", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("zh-CN", "Chinese (Simplified)", "简体中文", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("zh-TW", "Chinese (Traditional)", "繁體中文", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("hr", "Croatian", "Hrvatski", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("cs", "Czech", "Čeština", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("da", "Danish", "Dansk", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("nl", "Dutch", "Nederlands", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("en", "English", "English", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("et", "Estonian", "Eesti", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("fi", "Finnish", "Suomi", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("fr", "French", "Français", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("gl", "Galician", "Galego", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ka", "Georgian", "ქართული", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("de", "German", "Deutsch", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("el", "Greek", "Ελληνικά", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("gu", "Gujarati", "ગુજરાતી", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ht", "Haitian Creole", "Kreyòl Ayisyen", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("he", "Hebrew", "עברית", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("hi", "Hindi", "हिन्दी", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("hu", "Hungarian", "Magyar", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("is", "Icelandic", "Íslenska", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("id", "Indonesian", "Bahasa Indonesia", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ga", "Irish", "Gaeilge", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("it", "Italian", "Italiano", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("ja", "Japanese", "日本語", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("kn", "Kannada", "ಕನ್ನಡ", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ko", "Korean", "한국어", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("lv", "Latvian", "Latviešu", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("lt", "Lithuanian", "Lietuvių", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("mk", "Macedonian", "Македонски", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ms", "Malay", "Bahasa Melayu", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ml", "Malayalam", "മലയാളം", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("mt", "Maltese", "Malti", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("mr", "Marathi", "मराठी", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("no", "Norwegian", "Norsk", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("fa", "Persian", "فارسی", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("pl", "Polish", "Polski", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("pt", "Portuguese", "Português", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("pa", "Punjabi", "ਪੰਜਾਬੀ", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ro", "Romanian", "Română", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ru", "Russian", "Русский", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("sr", "Serbian", "Српски", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("sk", "Slovak", "Slovenčina", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("sl", "Slovenian", "Slovenščina", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("es", "Spanish", "Español", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("sw", "Swahili", "Kiswahili", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("sv", "Swedish", "Svenska", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ta", "Tamil", "தமிழ்", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("te", "Telugu", "తెలుగు", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("th", "Thai", "ไทย", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("tr", "Turkish", "Türkçe", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("uk", "Ukrainian", "Українська", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("ur", "Urdu", "اردو", supportsTextTranslation = true, supportsCameraTranslation = true),
                Language("vi", "Vietnamese", "Tiếng Việt", supportsTextTranslation = true, supportsVoiceTranslation = true, supportsCameraTranslation = true),
                Language("cy", "Welsh", "Cymraeg", supportsTextTranslation = true, supportsCameraTranslation = true)
            )

            languageDao.insertLanguages(supportedLanguages)
        }
    }
}