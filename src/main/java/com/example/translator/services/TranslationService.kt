package com.example.translator.services

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationService(private val context: Context) {

    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<String, Translator>()

    suspend fun detectLanguage(text: String): String? {
        return try {
            languageIdentifier.identifyLanguage(text).await()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? {
        return try {
            val translatorKey = "${sourceLanguage}_$targetLanguage"
            val translator = translators.getOrPut(translatorKey) {
                createTranslator(sourceLanguage, targetLanguage)
            }

            // Ensure model is downloaded
            downloadModelIfNeeded(translator)

            translator.translate(text).await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createTranslator(sourceLanguage: String, targetLanguage: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(sourceLanguage) ?: TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(targetLanguage) ?: TranslateLanguage.VIETNAMESE)
            .build()

        return Translation.getClient(options)
    }

    private suspend fun downloadModelIfNeeded(translator: Translator) {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions).await()
    }

    fun closeTranslators() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}