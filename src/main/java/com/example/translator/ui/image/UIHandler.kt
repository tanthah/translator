package com.example.translator.ui.image

import android.view.View
import android.widget.Toast

class UIHandler(private val activity: ImageTranslationBaseActivity) {

    fun showImageSelectionMode() {
        activity.layoutImageSelection.visibility = View.VISIBLE
        activity.layoutImagePreview.visibility = View.GONE
        activity.scrollResults.visibility = View.GONE

        // Clear previous data
        activity.tvDetectedText.text = ""
        activity.tvTranslatedText.text = ""
        activity.tvSummary.text = ""
        activity.tvDetectedLanguage.text = ""
        activity.layoutSummary.visibility = View.GONE
        activity.tvDetectedLanguage.visibility = View.GONE
        activity.btnTranslate.isEnabled = false
        activity.btnAutoDetect.isEnabled = false

        // Hide speech buttons
        activity.btnSpeakDetected.visibility = View.GONE
        activity.btnSpeakTranslated.visibility = View.GONE
        activity.btnSpeakSummary.visibility = View.GONE
        activity.btnSummarize.visibility = View.GONE

        // Reset image state
        activity.imageHandler.resetImageState()

        // Clear ViewModel state
        activity.viewModel.clearResults()
    }

    fun showImagePreviewMode() {
        activity.layoutImageSelection.visibility = View.GONE
        activity.layoutImagePreview.visibility = View.VISIBLE
        activity.scrollResults.visibility = View.GONE
    }

    fun updateDetectedText(text: String?) {
        activity.tvDetectedText.text = text ?: "No text detected"
        activity.tvDetectedText.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        activity.btnSpeakDetected.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        activity.btnSummarize.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        activity.btnAutoDetect.isEnabled = !text.isNullOrEmpty()
    }

    fun updateDetectedLanguageName(languageName: String?) {
        if (!languageName.isNullOrEmpty()) {
            activity.tvDetectedLanguage.text = "Detected: $languageName"
            activity.tvDetectedLanguage.visibility = View.VISIBLE
        } else {
            activity.tvDetectedLanguage.visibility = View.GONE
        }
    }

    fun updateTranslationResult(result: String?) {
        activity.tvTranslatedText.text = result ?: "Translation will appear here"
        activity.tvTranslatedText.visibility = if (result.isNullOrEmpty()) View.GONE else View.VISIBLE
        activity.btnSpeakTranslated.visibility = if (result.isNullOrEmpty()) View.GONE else View.VISIBLE

        // Show results section when translation is available
        if (!result.isNullOrEmpty()) {
            activity.scrollResults.visibility = View.VISIBLE
        }
    }

    fun updateSummaryResult(summary: String?) {
        if (!summary.isNullOrEmpty()) {
            activity.tvSummary.text = summary
            activity.layoutSummary.visibility = View.VISIBLE
            activity.btnSpeakSummary.visibility = View.VISIBLE
        } else {
            activity.layoutSummary.visibility = View.GONE
            activity.btnSpeakSummary.visibility = View.GONE
        }
    }

    fun updateLoadingState(isLoading: Boolean) {
        activity.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        activity.btnTranslate.isEnabled = !isLoading && (activity.imageHandler.getBitmapToProcess() != null)
        activity.btnConfirmCrop.isEnabled = !isLoading
    }

    fun updateLanguageDetectionState(isDetecting: Boolean) {
        activity.progressLanguageDetection.visibility = if (isDetecting) View.VISIBLE else View.GONE
        activity.btnAutoDetect.isEnabled = !isDetecting && !activity.viewModel.detectedText.value.isNullOrEmpty()
    }

    fun updateSummarizationState(isSummarizing: Boolean) {
        activity.progressSummarization.visibility = if (isSummarizing) View.VISIBLE else View.GONE
        activity.btnSummarize.isEnabled = !isSummarizing
    }

    fun showError(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}