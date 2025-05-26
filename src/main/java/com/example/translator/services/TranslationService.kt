package com.example.translator.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

class TranslationService(private val context: Context) {

    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val downloadedModels = mutableSetOf<String>()

    companion object {
        private const val TAG = "TranslationService"
        private const val TRANSLATION_TIMEOUT = 30000L // 30 seconds
        private const val LANGUAGE_DETECTION_TIMEOUT = 10000L // 10 seconds
        private const val MAX_TEXT_LENGTH = 5000
    }

    suspend fun detectLanguage(text: String): String? {
        if (!isValidInput(text)) {
            throw IllegalArgumentException("Invalid input text")
        }

        if (!isNetworkAvailable()) {
            throw NetworkException("No internet connection available")
        }

        return try {
            withTimeout(LANGUAGE_DETECTION_TIMEOUT) {
                val detectedLanguage = languageIdentifier.identifyLanguage(text).await()
                if (detectedLanguage == "und") null else detectedLanguage
            }
        } catch (e: Exception) {
            Log.e(TAG, "Language detection failed", e)
            handleTranslationException(e)
        }
    }

    suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? {
        if (!isValidInput(text)) {
            throw IllegalArgumentException("Invalid input text")
        }

        if (!isNetworkAvailable()) {
            throw NetworkException("No internet connection available")
        }

        if (sourceLanguage == targetLanguage) {
            return text // No translation needed
        }

        return try {
            val translatorKey = "${sourceLanguage}_$targetLanguage"
            val translator = getOrCreateTranslator(sourceLanguage, targetLanguage, translatorKey)

            // Ensure model is downloaded
            downloadModelIfNeeded(translator, translatorKey)

            // Perform translation with timeout
            withTimeout(TRANSLATION_TIMEOUT) {
                translator.translate(text).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed for $sourceLanguage -> $targetLanguage", e)
            handleTranslationException(e)
        }
    }

    private fun getOrCreateTranslator(
        sourceLanguage: String,
        targetLanguage: String,
        translatorKey: String
    ): Translator {
        return translators.getOrPut(translatorKey) {
            createTranslator(sourceLanguage, targetLanguage)
        }
    }

    private fun createTranslator(sourceLanguage: String, targetLanguage: String): Translator {
        val sourceTranslateLanguage = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: TranslateLanguage.ENGLISH
        val targetTranslateLanguage = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: TranslateLanguage.VIETNAMESE

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceTranslateLanguage)
            .setTargetLanguage(targetTranslateLanguage)
            .build()

        return Translation.getClient(options)
    }

    private suspend fun downloadModelIfNeeded(translator: Translator, translatorKey: String) {
        if (downloadedModels.contains(translatorKey)) {
            return // Model already downloaded
        }

        try {
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions).await()
            downloadedModels.add(translatorKey)
            Log.d(TAG, "Translation model downloaded for $translatorKey")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download translation model for $translatorKey", e)
            // Try without WiFi requirement
            try {
                val fallbackConditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(fallbackConditions).await()
                downloadedModels.add(translatorKey)
                Log.d(TAG, "Translation model downloaded (fallback) for $translatorKey")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Failed to download translation model (fallback) for $translatorKey", fallbackException)
                throw TranslationException("Failed to download translation model", fallbackException)
            }
        }
    }

    private fun isValidInput(text: String): Boolean {
        return text.isNotBlank() &&
                text.length <= MAX_TEXT_LENGTH &&
                !containsSuspiciousContent(text)
    }

    private fun containsSuspiciousContent(text: String): Boolean {
        // Basic validation to prevent misuse
        val suspiciousPatterns = listOf(
            "<script", "javascript:", "data:", "vbscript:"
        )
        return suspiciousPatterns.any { pattern ->
            text.contains(pattern, ignoreCase = true)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun handleTranslationException(exception: Exception): Nothing {
        when {
            exception.message?.contains("network", ignoreCase = true) == true ||
                    exception.message?.contains("internet", ignoreCase = true) == true -> {
                throw NetworkException("Network error: ${exception.message}")
            }
            exception.message?.contains("timeout", ignoreCase = true) == true -> {
                throw TranslationException("Translation timed out. Please try again.")
            }
            exception.message?.contains("model", ignoreCase = true) == true -> {
                throw TranslationException("Translation model not available. Please check your connection.")
            }
            else -> {
                throw TranslationException("Translation failed: ${exception.message ?: "Unknown error"}")
            }
        }
    }

    fun closeTranslators() {
        try {
            translators.values.forEach { translator ->
                try {
                    translator.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing translator", e)
                }
            }
            translators.clear()
            downloadedModels.clear()

            languageIdentifier.close()
            Log.d(TAG, "All translators closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing translation service", e)
        }
    }

    // Custom exception classes
    class NetworkException(message: String) : Exception(message)
    class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)
}