package com.example.translator.ui.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.ui.camera.CropOverlayView
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
import com.example.translator.services.SpeechService
import com.example.translator.services.TextSummarizationService
import kotlinx.coroutines.launch
import java.io.IOException

class ImageTranslationActivity : AppCompatActivity() {

    private lateinit var viewModel: ImageTranslationViewModel

    // Image selection views
    private lateinit var layoutImageSelection: LinearLayout
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnTakePhoto: MaterialButton

    // Image preview views
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var ivSelectedImage: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var btnConfirmCrop: MaterialButton
    private lateinit var btnRetake: MaterialButton

    // Common views
    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var tvDetectedText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var tvSummary: TextView
    private lateinit var layoutSummary: LinearLayout
    private lateinit var btnTranslate: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressSummarization: ProgressBar
    private lateinit var scrollResults: ScrollView

    // New speech and summary controls
    private lateinit var btnSpeakDetected: MaterialButton
    private lateinit var btnSpeakTranslated: MaterialButton
    private lateinit var btnSpeakSummary: MaterialButton
    private lateinit var btnSummarize: MaterialButton
    private lateinit var btnSpeechSettings: MaterialButton

    private var selectedImageBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    // Touch handling for zoom and pan
    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, ScaleListener())
    }
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Image transformation matrix
    private val imageMatrix = Matrix()
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    private val REQUEST_IMAGE_GALLERY = 1001
    private val REQUEST_IMAGE_CAMERA = 1002
    private val CAMERA_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_translation)

        initializeViews()
        setupViewModel()
        setupClickListeners()
        observeViewModel()

        showImageSelectionMode()
    }

    private fun initializeViews() {
        // Image selection views
        layoutImageSelection = findViewById(R.id.layout_image_selection)
        btnSelectImage = findViewById(R.id.btn_select_image)
        btnTakePhoto = findViewById(R.id.btn_take_photo)

        // Image preview views
        layoutImagePreview = findViewById(R.id.layout_image_preview)
        ivSelectedImage = findViewById(R.id.iv_selected_image)
        cropOverlay = findViewById(R.id.crop_overlay)
        btnConfirmCrop = findViewById(R.id.btn_confirm_crop)
        btnRetake = findViewById(R.id.btn_retake)

        // Common views
        spinnerSourceLanguage = findViewById(R.id.spinner_source_language)
        spinnerTargetLanguage = findViewById(R.id.spinner_target_language)
        tvDetectedText = findViewById(R.id.tv_detected_text)
        tvTranslatedText = findViewById(R.id.tv_translated_text)
        tvSummary = findViewById(R.id.tv_summary)
        layoutSummary = findViewById(R.id.layout_summary)
        btnTranslate = findViewById(R.id.btn_translate)
        progressBar = findViewById(R.id.progress_bar)
        progressSummarization = findViewById(R.id.progress_summarization)
        scrollResults = findViewById(R.id.scroll_results)

        // New speech and summary controls
        btnSpeakDetected = findViewById(R.id.btn_speak_detected)
        btnSpeakTranslated = findViewById(R.id.btn_speak_translated)
        btnSpeakSummary = findViewById(R.id.btn_speak_summary)
        btnSummarize = findViewById(R.id.btn_summarize)
        btnSpeechSettings = findViewById(R.id.btn_speech_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Image Translation"

        // Set ImageView scaleType to matrix for manual control
        ivSelectedImage.scaleType = ImageView.ScaleType.MATRIX
    }

    private fun setupViewModel() {
        val application = application as TranslatorApplication
        val factory = ImageTranslationViewModelFactory(
            application.userRepository,
            application.languageRepository,
            this
        )
        viewModel = ViewModelProvider(this, factory)[ImageTranslationViewModel::class.java]
    }

    private fun setupClickListeners() {
        btnSelectImage.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
        }

        btnTakePhoto.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        btnConfirmCrop.setOnClickListener {
            confirmCrop()
        }

        btnRetake.setOnClickListener {
            showImageSelectionMode()
        }

        btnTranslate.setOnClickListener {
            translateImage()
        }

        // New speech buttons
        btnSpeakDetected.setOnClickListener {
            val sourceLanguage = getSelectedSourceLanguageCode()
            viewModel.speakDetectedText(sourceLanguage)
        }

        btnSpeakTranslated.setOnClickListener {
            val targetLanguage = getSelectedTargetLanguageCode()
            viewModel.speakTranslatedText(targetLanguage)
        }

        btnSpeakSummary.setOnClickListener {
            val targetLanguage = getSelectedTargetLanguageCode()
            viewModel.speakSummary(targetLanguage)
        }

        // Summary button
        btnSummarize.setOnClickListener {
            showSummarizationDialog()
        }

        // Speech settings button
        btnSpeechSettings.setOnClickListener {
            showSpeechSettingsDialog()
        }

        setupImageTouchListeners()
    }

    private fun setupImageTouchListeners() {
        ivSelectedImage.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val deltaX = event.x - lastTouchX
                        val deltaY = event.y - lastTouchY

                        translateX += deltaX
                        translateY += deltaY

                        updateImageMatrix()

                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    true
                }
                else -> false
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)

            updateImageMatrix()
            return true
        }
    }

    private fun updateImageMatrix() {
        imageMatrix.reset()
        imageMatrix.postScale(scaleFactor, scaleFactor)
        imageMatrix.postTranslate(translateX, translateY)
        ivSelectedImage.imageMatrix = imageMatrix
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(this) { languages ->
            setupLanguageSpinners(languages.filter { it.supportsCameraTranslation })
        }

        viewModel.detectedText.observe(this) { text ->
            tvDetectedText.text = text ?: "No text detected"
            tvDetectedText.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            btnSpeakDetected.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            btnSummarize.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.translationResult.observe(this) { result ->
            tvTranslatedText.text = result ?: "Translation will appear here"
            tvTranslatedText.visibility = if (result.isNullOrEmpty()) View.GONE else View.VISIBLE
            btnSpeakTranslated.visibility = if (result.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Show results section when translation is available
            if (!result.isNullOrEmpty()) {
                scrollResults.visibility = View.VISIBLE
            }
        }

        viewModel.summaryResult.observe(this) { summary ->
            if (!summary.isNullOrEmpty()) {
                tvSummary.text = summary
                layoutSummary.visibility = View.VISIBLE
                btnSpeakSummary.visibility = View.VISIBLE
            } else {
                layoutSummary.visibility = View.GONE
                btnSpeakSummary.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnTranslate.isEnabled = !isLoading && (croppedBitmap != null || selectedImageBitmap != null)
            btnConfirmCrop.isEnabled = !isLoading
        }

        viewModel.isSummarizing.observe(this) { isSummarizing ->
            progressSummarization.visibility = if (isSummarizing) View.VISIBLE else View.GONE
            btnSummarize.isEnabled = !isSummarizing
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.speechRate.observe(this) { rate ->
            // Update speech settings UI if needed
        }
    }

    private fun showSummarizationDialog() {
        val options = arrayOf(
            "Brief Summary (1-2 sentences)",
            "Detailed Summary (3-5 sentences)",
            "Key Points (Bullet format)",
            "Key Phrases"
        )

        AlertDialog.Builder(this)
            .setTitle("Choose Summary Type")
            .setItems(options) { _, which ->
                val summaryType = when (which) {
                    0 -> TextSummarizationService.SummaryType.BRIEF
                    1 -> TextSummarizationService.SummaryType.DETAILED
                    2 -> TextSummarizationService.SummaryType.BULLET_POINTS
                    3 -> TextSummarizationService.SummaryType.KEY_PHRASES
                    else -> TextSummarizationService.SummaryType.BRIEF
                }

                val targetLanguage = getSelectedTargetLanguageCode()
                viewModel.summarizeDetectedText(summaryType, targetLanguage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSpeechSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_speech_settings, null)
        val speedSlider = dialogView.findViewById<Slider>(R.id.slider_speech_speed)
        val speedText = dialogView.findViewById<TextView>(R.id.tv_speed_text)

        // Set current speed
        speedSlider.value = viewModel.speechRate.value ?: SpeechService.SPEED_NORMAL
        speedText.text = "Speed: ${viewModel.getSpeechRateText(speedSlider.value)}"

        speedSlider.addOnChangeListener { _, value, _ ->
            speedText.text = "Speed: ${viewModel.getSpeechRateText(value)}"
        }

        AlertDialog.Builder(this)
            .setTitle("Speech Settings")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                viewModel.setSpeechRate(speedSlider.value)
                Toast.makeText(this, "Speech speed updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupLanguageSpinners(languages: List<com.example.translator.data.model.Language>) {
        val adapter = LanguageSpinnerAdapter(this, languages)

        spinnerSourceLanguage.adapter = adapter
        spinnerTargetLanguage.adapter = adapter

        // Set default selections
        val defaultSourceIndex = languages.indexOfFirst { it.languageCode == "en" }
        val defaultTargetIndex = languages.indexOfFirst { it.languageCode == "vi" }

        if (defaultSourceIndex != -1) spinnerSourceLanguage.setSelection(defaultSourceIndex)
        if (defaultTargetIndex != -1) spinnerTargetLanguage.setSelection(defaultTargetIndex)
    }

    private fun getSelectedSourceLanguageCode(): String {
        return try {
            (spinnerSourceLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "en"
        } catch (e: Exception) {
            "en"
        }
    }

    private fun getSelectedTargetLanguageCode(): String {
        return try {
            (spinnerTargetLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "vi"
        } catch (e: Exception) {
            "vi"
        }
    }

    private fun showImageSelectionMode() {
        layoutImageSelection.visibility = View.VISIBLE
        layoutImagePreview.visibility = View.GONE
        scrollResults.visibility = View.GONE

        // Clear previous data
        selectedImageBitmap = null
        croppedBitmap = null
        tvDetectedText.text = ""
        tvTranslatedText.text = ""
        tvSummary.text = ""
        layoutSummary.visibility = View.GONE
        btnTranslate.isEnabled = false

        // Reset matrix values
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        imageMatrix.reset()

        // Hide speech buttons
        btnSpeakDetected.visibility = View.GONE
        btnSpeakTranslated.visibility = View.GONE
        btnSpeakSummary.visibility = View.GONE
        btnSummarize.visibility = View.GONE
    }

    private fun showImagePreviewMode(bitmap: Bitmap) {
        layoutImageSelection.visibility = View.GONE
        layoutImagePreview.visibility = View.VISIBLE
        scrollResults.visibility = View.GONE

        selectedImageBitmap = bitmap

        // Set image and fit to ImageView
        ivSelectedImage.setImageBitmap(bitmap)
        fitImageToView(bitmap)

        // Show crop overlay
        cropOverlay.visibility = View.VISIBLE
        btnTranslate.isEnabled = true
    }

    private fun fitImageToView(bitmap: Bitmap) {
        val imageView = ivSelectedImage
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        if (viewWidth == 0f || viewHeight == 0f) {
            // If view hasn't been measured yet, post to run after layout
            imageView.post {
                fitImageToView(bitmap)
            }
            return
        }

        // Calculate scale to fit image in view
        val scaleX = viewWidth / bitmapWidth
        val scaleY = viewHeight / bitmapHeight
        scaleFactor = minOf(scaleX, scaleY)

        // Center the image
        translateX = (viewWidth - bitmapWidth * scaleFactor) / 2
        translateY = (viewHeight - bitmapHeight * scaleFactor) / 2

        updateImageMatrix()
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
            Toast.makeText(this, "Failed to open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAMERA)
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmCrop() {
        selectedImageBitmap?.let { bitmap ->
            try {
                val cropRect = cropOverlay.getCropRect()
                croppedBitmap = cropBitmap(bitmap, cropRect)

                Toast.makeText(this, "Area selected. Tap translate to process.", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error cropping image", e)
                Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap, cropRect: RectF): Bitmap? {
        return try {
            // Get the current image matrix values
            val values = FloatArray(9)
            imageMatrix.getValues(values)

            val scaleX = values[Matrix.MSCALE_X]
            val scaleY = values[Matrix.MSCALE_Y]
            val transX = values[Matrix.MTRANS_X]
            val transY = values[Matrix.MTRANS_Y]

            // Convert crop coordinates to bitmap coordinates
            val x = ((cropRect.left - transX) / scaleX).toInt().coerceAtLeast(0)
            val y = ((cropRect.top - transY) / scaleY).toInt().coerceAtLeast(0)
            val width = (cropRect.width() / scaleX).toInt().coerceAtMost(bitmap.width - x)
            val height = (cropRect.height() / scaleY).toInt().coerceAtMost(bitmap.height - y)

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, x, y, width, height)
            } else {
                bitmap // Return original if crop area is invalid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating cropped bitmap", e)
            bitmap // Return original bitmap on error
        }
    }

    private fun translateImage() {
        val bitmapToProcess = croppedBitmap ?: selectedImageBitmap

        bitmapToProcess?.let { bitmap ->
            val sourceLanguage = getSelectedSourceLanguageCode()
            val targetLanguage = getSelectedTargetLanguageCode()

            lifecycleScope.launch {
                viewModel.processImage(bitmap, sourceLanguage, targetLanguage)
            }
        } ?: run {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    data?.data?.let { uri ->
                        try {
                            val bitmap = loadBitmapFromUri(uri)
                            bitmap?.let {
                                showImagePreviewMode(it)
                            } ?: run {
                                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image from gallery", e)
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_IMAGE_CAMERA -> {
                    try {
                        val bitmap = data?.extras?.get("data") as? Bitmap
                        bitmap?.let {
                            showImagePreviewMode(it)
                        } ?: run {
                            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing camera result", e)
                        Toast.makeText(this, "Failed to process camera image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // Use content resolver to get input stream
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate sample size to avoid memory issues
                options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                options.inJustDecodeBounds = false

                // Decode the actual bitmap
                contentResolver.openInputStream(uri)?.use { newInputStream ->
                    android.graphics.BitmapFactory.decodeStream(newInputStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            // Older versions use READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - request READ_MEDIA_IMAGES
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), STORAGE_PERMISSION_CODE)
        } else {
            // Older versions - request READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopSpeaking()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val TAG = "ImageTranslationActivity"
    }
}