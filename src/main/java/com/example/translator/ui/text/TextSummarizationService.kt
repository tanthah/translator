package com.example.translator.services

import android.content.Context
import android.util.Log
import com.example.translator.services.TranslationService
import kotlinx.coroutines.withTimeout

class TextSummarizationService(private val context: Context) {

    private val translationService = TranslationService(context)

    companion object {
        private const val TAG = "TextSummarizationService"
        private const val SUMMARIZATION_TIMEOUT = 30000L // 30 seconds
        private const val MAX_TEXT_LENGTH = 10000
        private const val MIN_TEXT_LENGTH = 100
    }

    suspend fun summarizeText(
        text: String,
        summaryType: SummaryType = SummaryType.BRIEF,
        targetLanguage: String = "en"
    ): SummaryResult {

        if (!isValidInput(text)) {
            return SummaryResult.Error("Text is too short or too long for summarization")
        }

        return try {
            withTimeout(SUMMARIZATION_TIMEOUT) {
                val summary = when (summaryType) {
                    SummaryType.BRIEF -> createBriefSummary(text)
                    SummaryType.DETAILED -> createDetailedSummary(text)
                    SummaryType.BULLET_POINTS -> createBulletPointSummary(text)
                    SummaryType.KEY_PHRASES -> extractKeyPhrases(text)
                }

                // Translate summary if needed
                val finalSummary = if (targetLanguage != "en") {
                    translationService.translateText(summary, "en", targetLanguage) ?: summary
                } else {
                    summary
                }

                SummaryResult.Success(finalSummary, summaryType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed", e)
            SummaryResult.Error("Failed to summarize text: ${e.message}")
        }
    }

    private fun isValidInput(text: String): Boolean {
        val cleanText = text.trim()
        return cleanText.length in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH
    }

    private fun createBriefSummary(text: String): String {
        val sentences = splitIntoSentences(text)
        val importantSentences = extractImportantSentences(sentences, maxSentences = 2)
        return importantSentences.joinToString(" ")
    }

    private fun createDetailedSummary(text: String): String {
        val sentences = splitIntoSentences(text)
        val importantSentences = extractImportantSentences(sentences, maxSentences = 5)
        return importantSentences.joinToString(" ")
    }

    private fun createBulletPointSummary(text: String): String {
        val sentences = splitIntoSentences(text)
        val keyPoints = extractImportantSentences(sentences, maxSentences = 4)
        return keyPoints.mapIndexed { index, sentence ->
            "• ${sentence.trim()}"
        }.joinToString("\n")
    }

    private fun extractKeyPhrases(text: String): String {
        val words = text.toLowerCase()
            .replace(Regex("[^a-zA-Z\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 }

        val wordFrequency = words.groupingBy { it }.eachCount()
        val topWords = wordFrequency.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }

        return "Key terms: ${topWords.joinToString(", ")}"
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 10 }
    }

    private fun extractImportantSentences(sentences: List<String>, maxSentences: Int): List<String> {
        if (sentences.size <= maxSentences) {
            return sentences
        }

        // Simple scoring based on sentence length and position
        val scoredSentences = sentences.mapIndexed { index, sentence ->
            val positionScore = when {
                index == 0 -> 3.0 // First sentence is important
                index == sentences.size - 1 -> 2.0 // Last sentence
                index < sentences.size * 0.3 -> 1.5 // Early sentences
                else -> 1.0
            }

            val lengthScore = when {
                sentence.length < 50 -> 0.5 // Too short
                sentence.length > 200 -> 0.7 // Too long
                else -> 1.0 // Good length
            }

            val keywordScore = countKeywords(sentence)

            val totalScore = positionScore * lengthScore * (1 + keywordScore * 0.1)

            ScoredSentence(sentence, totalScore)
        }

        return scoredSentences
            .sortedByDescending { it.score }
            .take(maxSentences)
            .sortedBy { sentences.indexOf(it.sentence) } // Maintain original order
            .map { it.sentence }
    }

    private fun countKeywords(sentence: String): Int {
        val keywords = listOf(
            "important", "significant", "key", "main", "primary", "essential",
            "critical", "major", "fundamental", "crucial", "vital", "notable",
            "first", "second", "third", "finally", "conclusion", "result",
            "because", "therefore", "however", "although", "moreover"
        )

        val lowerSentence = sentence.toLowerCase()
        return keywords.count { lowerSentence.contains(it) }
    }

    fun close() {
        translationService.closeTranslators()
    }

    data class ScoredSentence(val sentence: String, val score: Double)

    enum class SummaryType {
        BRIEF,          // 1-2 sentences
        DETAILED,       // 3-5 sentences
        BULLET_POINTS,  // Key points as bullets
        KEY_PHRASES     // Important terms
    }

    sealed class SummaryResult {
        data class Success(val summary: String, val type: SummaryType) : SummaryResult()
        data class Error(val message: String) : SummaryResult()
    }
}