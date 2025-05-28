package com.example.translator.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        private const val RECOGNITION_TIMEOUT = 30000L // Increased to 30 seconds
        private const val MIN_TEXT_LENGTH = 1
        private const val MAX_TEXT_LENGTH = 10000 // Increased limit
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String? {
        if (!isValidBitmap(bitmap)) {
            Log.w(TAG, "Invalid bitmap provided")
            return null
        }

        return try {
            // Enhance bitmap for better OCR results
            val enhancedBitmap = enhanceBitmapForOCR(bitmap)
            val image = InputImage.fromBitmap(enhancedBitmap, 0)
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
                        cleanupRecognizedText(recognizedText.take(MAX_TEXT_LENGTH))
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

    private fun enhanceBitmapForOCR(originalBitmap: Bitmap): Bitmap {
        try {
            Log.d(TAG, "Enhancing bitmap for OCR")

            // Create a mutable copy of the bitmap
            val config = if (originalBitmap.config != null) originalBitmap.config else Bitmap.Config.ARGB_8888
            val enhancedBitmap = Bitmap.createBitmap(
                originalBitmap.width,
                originalBitmap.height,
                config
            )

            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()

            // Apply contrast and brightness adjustments
            val colorMatrix = ColorMatrix()

            // Increase contrast (values > 1.0 increase contrast)
            val contrast = 1.5f
            val brightness = 10f // Slight brightness increase

            // Set contrast
            colorMatrix.setScale(contrast, contrast, contrast, 1f)

            // Add brightness
            val brightnessMatrix = ColorMatrix()
            brightnessMatrix.set(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))

            colorMatrix.postConcat(brightnessMatrix)

            // Apply the color matrix
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

            // Draw the enhanced image
            canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

            Log.d(TAG, "Bitmap enhancement completed")
            return enhancedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing bitmap, using original", e)
            return originalBitmap
        }
    }

    private fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
                !bitmap.isRecycled &&
                bitmap.width > 0 &&
                bitmap.height > 0 &&
                bitmap.width <= 8192 && // Increased max dimensions
                bitmap.height <= 8192
    }

    private fun cleanupRecognizedText(text: String): String {
        return text
            .trim()
            .replace(Regex("\\s+"), " ") // Replace multiple whitespaces with single space
            .replace(Regex("[\r\n]+"), "\n") // Normalize line breaks
            .replace(Regex("\\n{3,}"), "\n\n") // Limit consecutive line breaks
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "") // Remove control characters
            .let { cleanedText ->
                // Remove common OCR artifacts
                cleanedText
                    .replace(Regex("\\|+"), "I") // Replace pipe characters with I
                    .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") // Add space between lowercase and uppercase
                    .replace(Regex("\\s+"), " ") // Clean up spaces again
                    .trim()
            }
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
            exception.message?.contains("service", ignoreCase = true) == true -> {
                Log.e(TAG, "ML Kit service error")
                null
            }
            else -> {
                Log.e(TAG, "Unknown text recognition error: ${exception.message}")
                null
            }
        }
    }

    // Additional method for batch processing multiple images
    suspend fun recognizeTextFromMultipleBitmaps(bitmaps: List<Bitmap>): List<String?> {
        return bitmaps.map { bitmap ->
            try {
                recognizeTextFromBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bitmap in batch", e)
                null
            }
        }
    }

    // Method to get confidence scores (if needed in the future)
    suspend fun recognizeTextWithConfidence(inputImage: InputImage): Pair<String?, Float> {
        return try {
            withTimeout(RECOGNITION_TIMEOUT) {
                val result = textRecognizer.process(inputImage).await()
                val text = result.text

                // Calculate average confidence from text blocks
                val avgConfidence = if (result.textBlocks.isNotEmpty()) {
                    result.textBlocks.mapNotNull { block ->
                        // ML Kit doesn't provide confidence directly, so we estimate based on text quality
                        estimateTextConfidence(block.text)
                    }.average().toFloat()
                } else {
                    0f
                }

                val cleanedText = if (text.isNotBlank()) cleanupRecognizedText(text) else null
                Pair(cleanedText, avgConfidence)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition with confidence failed", e)
            Pair(null, 0f)
        }
    }

    private fun estimateTextConfidence(text: String): Float {
        // Simple heuristic to estimate text recognition confidence
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return 0f

        var score = 1.0f

        // Penalize for too many special characters
        val specialCharRatio = cleanText.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / cleanText.length
        if (specialCharRatio > 0.3f) score *= 0.7f

        // Penalize for too many single characters
        val words = cleanText.split("\\s+".toRegex())
        val singleCharWords = words.count { it.length == 1 }
        if (singleCharWords > words.size / 2) score *= 0.8f

        // Reward for reasonable text length
        if (cleanText.length in 10..1000) score *= 1.1f

        return score.coerceIn(0f, 1f)
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