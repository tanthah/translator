package com.example.translator.services

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class TextRecognitionService {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "TextRecognitionService"
        private const val RECOGNITION_TIMEOUT = 15000L // 15 seconds
        private const val MIN_TEXT_LENGTH = 1
        private const val MAX_TEXT_LENGTH = 5000
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String? {
        if (!isValidBitmap(bitmap)) {
            Log.w(TAG, "Invalid bitmap provided")
            return null
        }

        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizeTextFromImage(image)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating InputImage from bitmap", e)
            null
        }
    }

    suspend fun recognizeTextFromImage(inputImage: InputImage): String? {
        return try {
            withTimeout(RECOGNITION_TIMEOUT) {
                Log.d(TAG, "Starting text recognition...")
                val result = textRecognizer.process(inputImage).await()

                val recognizedText = result.text

                when {
                    recognizedText.isBlank() -> {
                        Log.d(TAG, "No text detected in image")
                        null
                    }
                    recognizedText.length < MIN_TEXT_LENGTH -> {
                        Log.d(TAG, "Detected text too short: ${recognizedText.length} chars")
                        null
                    }
                    recognizedText.length > MAX_TEXT_LENGTH -> {
                        Log.w(TAG, "Detected text too long, truncating: ${recognizedText.length} chars")
                        recognizedText.take(MAX_TEXT_LENGTH)
                    }
                    else -> {
                        Log.d(TAG, "Text recognition successful: ${recognizedText.length} chars")
                        cleanupRecognizedText(recognizedText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition failed", e)
            handleRecognitionError(e)
        }
    }

    private fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
                !bitmap.isRecycled &&
                bitmap.width > 0 &&
                bitmap.height > 0 &&
                bitmap.width <= 4096 &&
                bitmap.height <= 4096
    }

    private fun cleanupRecognizedText(text: String): String {
        return text
            .trim()
            .replace(Regex("\\s+"), " ") // Replace multiple whitespaces with single space
            .replace(Regex("[\r\n]+"), "\n") // Normalize line breaks
            .replace(Regex("\\n{3,}"), "\n\n") // Limit consecutive line breaks
    }

    private fun handleRecognitionError(exception: Exception): String? {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> {
                Log.e(TAG, "Text recognition timed out")
                null
            }
            exception.message?.contains("memory", ignoreCase = true) == true -> {
                Log.e(TAG, "Memory error during text recognition")
                null
            }
            exception.message?.contains("network", ignoreCase = true) == true -> {
                Log.e(TAG, "Network error during text recognition")
                null
            }
            else -> {
                Log.e(TAG, "Unknown text recognition error: ${exception.message}")
                null
            }
        }
    }

    fun close() {
        try {
            textRecognizer.close()
            Log.d(TAG, "TextRecognitionService closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TextRecognitionService", e)
        }
    }
}