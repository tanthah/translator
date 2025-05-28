package com.example.translator.ui.image

import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem

class LanguageHandler(private val activity: ImageTranslationBaseActivity) {

    fun setupLanguageSpinners(languages: List<com.example.translator.data.model.Language>) {
        if (languages.isEmpty()) return

        val adapter = LanguageSpinnerAdapter(activity, languages)

        activity.spinnerSourceLanguage.adapter = adapter
        activity.spinnerTargetLanguage.adapter = adapter

        // Set default selections
        val defaultSourceIndex = languages.indexOfFirst { it.languageCode == "en" }
        val defaultTargetIndex = languages.indexOfFirst { it.languageCode == "vi" }

        if (defaultSourceIndex != -1) activity.spinnerSourceLanguage.setSelection(defaultSourceIndex)
        if (defaultTargetIndex != -1) activity.spinnerTargetLanguage.setSelection(defaultTargetIndex)
    }

    fun autoDetectLanguage() {
        val detectedText = activity.viewModel.detectedText.value
        if (detectedText.isNullOrEmpty()) {
            activity.uiHandler.showError("No text available for language detection")
            return
        }

        activity.viewModel.detectLanguageFromText(detectedText)
    }

    fun swapLanguages() {
        val sourcePosition = activity.spinnerSourceLanguage.selectedItemPosition
        val targetPosition = activity.spinnerTargetLanguage.selectedItemPosition

        activity.spinnerSourceLanguage.setSelection(targetPosition)
        activity.spinnerTargetLanguage.setSelection(sourcePosition)
    }

    fun updateDetectedLanguage(languageCode: String?) {
        if (!languageCode.isNullOrEmpty()) {
            // Find and select the detected language in the spinner
            val languageIndex = activity.supportedLanguages.indexOfFirst { it.languageCode == languageCode }
            if (languageIndex != -1) {
                activity.spinnerSourceLanguage.setSelection(languageIndex)
            }
        }
    }

    fun getSelectedSourceLanguageCode(): String {
        return try {
            (activity.spinnerSourceLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "en"
        } catch (e: Exception) {
            "en"
        }
    }

    fun getSelectedTargetLanguageCode(): String {
        return try {
            (activity.spinnerTargetLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "vi"
        } catch (e: Exception) {
            "vi"
        }
    }
}