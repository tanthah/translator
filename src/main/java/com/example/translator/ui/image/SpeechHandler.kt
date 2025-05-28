package com.example.translator.ui.image

import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.slider.Slider
import com.example.translator.R
import com.example.translator.services.SpeechService
import com.example.translator.services.TextSummarizationService

class SpeechHandler(private val activity: ImageTranslationBaseActivity) {

    fun speakDetectedText() {
        val sourceLanguage = activity.languageHandler.getSelectedSourceLanguageCode()
        activity.viewModel.speakDetectedText(sourceLanguage)
    }

    fun speakTranslatedText() {
        val targetLanguage = activity.languageHandler.getSelectedTargetLanguageCode()
        activity.viewModel.speakTranslatedText(targetLanguage)
    }

    fun speakSummary() {
        val targetLanguage = activity.languageHandler.getSelectedTargetLanguageCode()
        activity.viewModel.speakSummary(targetLanguage)
    }

    fun showSummarizationDialog() {
        val options = arrayOf(
            "Brief Summary (1-2 sentences)",
            "Detailed Summary (3-5 sentences)",
            "Key Points (Bullet format)",
            "Key Phrases"
        )

        AlertDialog.Builder(activity)
            .setTitle("Choose Summary Type")
            .setItems(options) { _, which ->
                val summaryType = when (which) {
                    0 -> TextSummarizationService.SummaryType.BRIEF
                    1 -> TextSummarizationService.SummaryType.DETAILED
                    2 -> TextSummarizationService.SummaryType.BULLET_POINTS
                    3 -> TextSummarizationService.SummaryType.KEY_PHRASES
                    else -> TextSummarizationService.SummaryType.BRIEF
                }

                val targetLanguage = activity.languageHandler.getSelectedTargetLanguageCode()
                activity.viewModel.summarizeDetectedText(summaryType, targetLanguage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showSpeechSettingsDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_speech_settings, null)
        val speedSlider = dialogView.findViewById<Slider>(R.id.slider_speech_speed)
        val speedText = dialogView.findViewById<TextView>(R.id.tv_speed_text)

        // Set current speed
        speedSlider.value = activity.viewModel.speechRate.value ?: SpeechService.SPEED_NORMAL
        speedText.text = "Speed: ${activity.viewModel.getSpeechRateText(speedSlider.value)}"

        speedSlider.addOnChangeListener { _, value, _ ->
            speedText.text = "Speed: ${activity.viewModel.getSpeechRateText(value)}"
        }

        AlertDialog.Builder(activity)
            .setTitle("Speech Settings")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                activity.viewModel.setSpeechRate(speedSlider.value)
                Toast.makeText(activity, "Speech speed updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}