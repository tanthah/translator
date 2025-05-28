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
        private const val TRANSLATION_TIMEOUT = 45000L // Increased to 45 seconds
        private const val LANGUAGE_DETECTION_TIMEOUT = 15000L // Increased to 15 seconds
        private const val MAX_TEXT_LENGTH = 10000 // Increased limit
        private const val MIN_TEXT_LENGTH_FOR_DETECTION = 10 // Minimum text for reliable detection
    }

    suspend fun detectLanguage(text: String): String? {
        if (!isValidInputForDetection(text)) {
            throw IllegalArgumentException("Text too short for reliable language detection")
        }

        if (!isNetworkAvailable()) {
            throw NetworkException("No internet connection available")
        }

        return try {
            withTimeout(LANGUAGE_DETECTION_TIMEOUT) {
                Log.d(TAG, "Detecting language for text: ${text.take(50)}...")

                // Clean text for better detection
                val cleanedText = preprocessTextForDetection(text)

                val detectedLanguage = languageIdentifier.identifyLanguage(cleanedText).await()

                Log.d(TAG, "Raw detection result: $detectedLanguage")

                if (detectedLanguage == "und" || detectedLanguage.isNullOrEmpty()) {
                    // Try with different text preprocessing
                    val alternativeText = text.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    if (alternativeText.length >= MIN_TEXT_LENGTH_FOR_DETECTION) {
                        val secondAttempt = languageIdentifier.identifyLanguage(alternativeText).await()
                        Log.d(TAG, "Second detection attempt: $secondAttempt")
                        if (secondAttempt != "und") return secondAttempt
                    }

                    null
                } else {
                    // Validate detected language is supported
                    val mappedLanguage = mapToSupportedLanguage(detectedLanguage)
                    Log.d(TAG, "Mapped language: $mappedLanguage")
                    mappedLanguage
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Language detection failed", e)
            handleTranslationException(e)
        }
    }

    private fun preprocessTextForDetection(text: String): String {
        return text
            .take(1000) // Use first 1000 characters for detection
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control characters
            .trim()
    }

    private fun mapToSupportedLanguage(detectedCode: String): String {
        // Map ML Kit language codes to our supported language codes
        return when (detectedCode.lowercase()) {
            "zh-cn", "zh-hans" -> "zh-CN"
            "zh-tw", "zh-hant" -> "zh-TW"
            "pt-br" -> "pt"
            "es-419", "es-us" -> "es"
            "en-us", "en-gb" -> "en"
            "fr-ca" -> "fr"
            "ar-eg", "ar-sa" -> "ar"
            else -> detectedCode
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
            Log.d(TAG, "Translating from $sourceLanguage to $targetLanguage")

            val translatorKey = "${sourceLanguage}_$targetLanguage"
            val translator = getOrCreateTranslator(sourceLanguage, targetLanguage, translatorKey)

            // Ensure model is downloaded
            downloadModelIfNeeded(translator, translatorKey)

            // Split long text into chunks if necessary
            val chunks = if (text.length > 4000) {
                splitTextIntoChunks(text, 4000)
            } else {
                listOf(text)
            }

            val translatedChunks = mutableListOf<String>()

            for (chunk in chunks) {
                val translatedChunk = withTimeout(TRANSLATION_TIMEOUT) {
                    translator.translate(chunk).await()
                }
                translatedChunks.add(translatedChunk)
            }

            val result = translatedChunks.joinToString(" ")
            Log.d(TAG, "Translation successful: ${result.length} characters")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Translation failed for $sourceLanguage -> $targetLanguage", e)
            handleTranslationException(e)
        }
    }

    private fun splitTextIntoChunks(text: String, maxChunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val endIndex = minOf(currentIndex + maxChunkSize, text.length)
            var chunk = text.substring(currentIndex, endIndex)

            // Try to break at sentence boundaries for better translation
            if (endIndex < text.length) {
                val lastSentenceEnd = chunk.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                if (lastSentenceEnd > chunk.length / 2) {
                    chunk = chunk.substring(0, lastSentenceEnd + 1)
                    currentIndex += lastSentenceEnd + 1
                } else {
                    // Break at word boundary
                    val lastSpace = chunk.lastIndexOf(' ')
                    if (lastSpace > chunk.length / 2) {
                        chunk = chunk.substring(0, lastSpace)
                        currentIndex += lastSpace + 1
                    } else {
                        currentIndex = endIndex
                    }
                }
            } else {
                currentIndex = endIndex
            }

            chunks.add(chunk.trim())
        }

        return chunks.filter { it.isNotEmpty() }
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
        val sourceTranslateLanguage = mapToMLKitLanguage(sourceLanguage)
        val targetTranslateLanguage = mapToMLKitLanguage(targetLanguage)

        Log.d(TAG, "Creating translator: $sourceLanguage ($sourceTranslateLanguage) -> $targetLanguage ($targetTranslateLanguage)")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceTranslateLanguage)
            .setTargetLanguage(targetTranslateLanguage)
            .build()

        return Translation.getClient(options)
    }

    private fun mapToMLKitLanguage(languageCode: String): String {
        // Map our language codes to ML Kit TranslateLanguage constants
        return when (languageCode.lowercase()) {
            "zh-cn" -> TranslateLanguage.CHINESE
            "zh-tw" -> TranslateLanguage.CHINESE
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "th" -> TranslateLanguage.THAI
            "vi" -> TranslateLanguage.VIETNAMESE
            "tr" -> TranslateLanguage.TURKISH
            "pl" -> TranslateLanguage.POLISH
            "nl" -> TranslateLanguage.DUTCH
            "sv" -> TranslateLanguage.SWEDISH
            "da" -> TranslateLanguage.DANISH
            "no" -> TranslateLanguage.NORWEGIAN
            "fi" -> TranslateLanguage.FINNISH
            "cs" -> TranslateLanguage.CZECH
            "sk" -> TranslateLanguage.SLOVAK
            "hu" -> TranslateLanguage.HUNGARIAN
            "ro" -> TranslateLanguage.ROMANIAN
            "bg" -> TranslateLanguage.BULGARIAN
            "hr" -> TranslateLanguage.CROATIAN
            "sr" -> TranslateLanguage.SERBIAN
            "sl" -> TranslateLanguage.SLOVENIAN
            "et" -> TranslateLanguage.ESTONIAN
            "lv" -> TranslateLanguage.LATVIAN
            "lt" -> TranslateLanguage.LITHUANIAN
            "mt" -> TranslateLanguage.MALTESE
            "ga" -> TranslateLanguage.IRISH
            "cy" -> TranslateLanguage.WELSH
            "is" -> TranslateLanguage.ICELANDIC
            "mk" -> TranslateLanguage.MACEDONIAN
            "af" -> TranslateLanguage.AFRIKAANS
            "sw" -> TranslateLanguage.SWAHILI
            "ms" -> TranslateLanguage.MALAY
            "id" -> TranslateLanguage.INDONESIAN
            "tl" -> TranslateLanguage.TAGALOG
            "bn" -> TranslateLanguage.BENGALI
            "gu" -> TranslateLanguage.GUJARATI
            "kn" -> TranslateLanguage.KANNADA
            "ml" -> TranslateLanguage.MALAYALAM
            "mr" -> TranslateLanguage.MARATHI
            "pa" -> TranslateLanguage.PUNJABI
            "ta" -> TranslateLanguage.TAMIL
            "te" -> TranslateLanguage.TELUGU
            "ur" -> TranslateLanguage.URDU
            "fa" -> TranslateLanguage.PERSIAN
            "he" -> TranslateLanguage.HEBREW
            "ka" -> TranslateLanguage.GEORGIAN
            "el" -> TranslateLanguage.GREEK
            "uk" -> TranslateLanguage.UKRAINIAN
            "ht" -> TranslateLanguage.HAITIAN_CREOLE
            "ca" -> TranslateLanguage.CATALAN
            "gl" -> TranslateLanguage.GALICIAN
            else -> {
                Log.w(TAG, "Unknown language code: $languageCode, using English as fallback")
                TranslateLanguage.ENGLISH
            }
        }
    }

    private suspend fun downloadModelIfNeeded(translator: Translator, translatorKey: String) {
        if (downloadedModels.contains(translatorKey)) {
            return // Model already downloaded
        }

        try {
            Log.d(TAG, "Checking if model download is needed for $translatorKey")

            // First, try with WiFi requirement
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions).await()
            downloadedModels.add(translatorKey)
            Log.d(TAG, "Translation model downloaded for $translatorKey")

        } catch (e: Exception) {
            Log.w(TAG, "Failed to download translation model with WiFi requirement for $translatorKey", e)

            // Try without WiFi requirement as fallback
            try {
                val fallbackConditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(fallbackConditions).await()
                downloadedModels.add(translatorKey)
                Log.d(TAG, "Translation model downloaded (fallback) for $translatorKey")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Failed to download translation model (fallback) for $translatorKey", fallbackException)
                throw TranslationException("Failed to download translation model. Please check your internet connection.", fallbackException)
            }
        }
    }

    private fun isValidInput(text: String): Boolean {
        return text.isNotBlank() &&
                text.length <= MAX_TEXT_LENGTH &&
                !containsSuspiciousContent(text)
    }

    private fun isValidInputForDetection(text: String): Boolean {
        val cleanText = text.trim()
        return cleanText.length >= MIN_TEXT_LENGTH_FOR_DETECTION &&
                cleanText.length <= MAX_TEXT_LENGTH &&
                !containsSuspiciousContent(cleanText)
    }

    private fun containsSuspiciousContent(text: String): Boolean {
        // Basic validation to prevent misuse
        val suspiciousPatterns = listOf(
            "<script", "javascript:", "data:", "vbscript:", "<?php"
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
                throw TranslationException("Translation timed out. The text might be too long or the connection is slow.")
            }
            exception.message?.contains("model", ignoreCase = true) == true -> {
                throw TranslationException("Translation model not available. Please check your connection and try again.")
            }
            exception.message?.contains("quota", ignoreCase = true) == true ||
                    exception.message?.contains("limit", ignoreCase = true) == true -> {
                throw TranslationException("Translation service temporarily unavailable. Please try again later.")
            }
            exception.message?.contains("language", ignoreCase = true) == true -> {
                throw TranslationException("Unsupported language pair. Please try different languages.")
            }
            else -> {
                throw TranslationException("Translation failed: ${exception.message ?: "Unknown error"}")
            }
        }
    }

    // Method to check if a language pair is supported
    fun isLanguagePairSupported(sourceLanguage: String, targetLanguage: String): Boolean {
        return try {
            val sourceMapped = mapToMLKitLanguage(sourceLanguage)
            val targetMapped = mapToMLKitLanguage(targetLanguage)
            sourceMapped != TranslateLanguage.ENGLISH || targetMapped != TranslateLanguage.ENGLISH ||
                    sourceLanguage != "unknown" && targetLanguage != "unknown"
        } catch (e: Exception) {
            false
        }
    }

    // Method to get all available translation models
    suspend fun getAvailableModels(): Set<String> {
        return downloadedModels.toSet()
    }

    // Method to preload common language models
    suspend fun preloadCommonModels() {
        val commonPairs = listOf(
            "en_vi", "vi_en", "en_zh", "zh_en", "en_es", "es_en",
            "en_fr", "fr_en", "en_de", "de_en", "en_ja", "ja_en"
        )

        for (pair in commonPairs) {
            try {
                val parts = pair.split("_")
                if (parts.size == 2) {
                    val translator = getOrCreateTranslator(parts[0], parts[1], pair)
                    downloadModelIfNeeded(translator, pair)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to preload model for $pair", e)
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