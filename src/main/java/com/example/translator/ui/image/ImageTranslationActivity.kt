package com.example.translator.ui.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.ui.camera.CropOverlayView
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
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
    private lateinit var btnTranslate: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollResults: ScrollView

    private var selectedImageBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    // Touch handling for zoom and pan
    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, ScaleListener())
    }
    private var lastTouchX = 0f
    private var lastTouchY = 0f

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
        btnTranslate = findViewById(R.id.btn_translate)
        progressBar = findViewById(R.id.progress_bar)
        scrollResults = findViewById(R.id.scroll_results)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Image Translation"
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

                        ivSelectedImage.translationX += deltaX * 0.5f
                        ivSelectedImage.translationY += deltaY * 0.5f

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
            val scaleFactor = detector.scaleFactor
            val currentScale = ivSelectedImage.scaleX
            val newScale = (currentScale * scaleFactor).coerceIn(0.5f, 3.0f)

            ivSelectedImage.scaleX = newScale
            ivSelectedImage.scaleY = newScale

            return true
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(this) { languages ->
            setupLanguageSpinners(languages.filter { it.supportsCameraTranslation })
        }

        viewModel.detectedText.observe(this) { text ->
            tvDetectedText.text = text ?: "No text detected"
            tvDetectedText.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.translationResult.observe(this) { result ->
            tvTranslatedText.text = result ?: "Translation will appear here"
            tvTranslatedText.visibility = if (result.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Show results section when translation is available
            if (!result.isNullOrEmpty()) {
                scrollResults.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnTranslate.isEnabled = !isLoading && (croppedBitmap != null || selectedImageBitmap != null)
            btnConfirmCrop.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
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

    private fun showImageSelectionMode() {
        layoutImageSelection.visibility = View.VISIBLE
        layoutImagePreview.visibility = View.GONE
        scrollResults.visibility = View.GONE

        // Clear previous data
        selectedImageBitmap = null
        croppedBitmap = null
        tvDetectedText.text = ""
        tvTranslatedText.text = ""
        btnTranslate.isEnabled = false
    }

    private fun showImagePreviewMode(bitmap: Bitmap) {
        layoutImageSelection.visibility = View.GONE
        layoutImagePreview.visibility = View.VISIBLE
        scrollResults.visibility = View.GONE

        selectedImageBitmap = bitmap
        ivSelectedImage.setImageBitmap(bitmap)

        // Reset image transformations
        ivSelectedImage.scaleX = 1.0f
        ivSelectedImage.scaleY = 1.0f
        ivSelectedImage.translationX = 0f
        ivSelectedImage.translationY = 0f

        // Show crop overlay
        cropOverlay.visibility = View.VISIBLE
        btnTranslate.isEnabled = true
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
            // Calculate scale factors based on ImageView dimensions vs bitmap dimensions
            val imageView = ivSelectedImage
            val scaleX = bitmap.width.toFloat() / imageView.width
            val scaleY = bitmap.height.toFloat() / imageView.height

            // Apply the current zoom scale
            val currentScale = imageView.scaleX
            val adjustedScaleX = scaleX / currentScale
            val adjustedScaleY = scaleY / currentScale

            // Calculate crop coordinates
            val x = (cropRect.left * adjustedScaleX).toInt().coerceAtLeast(0)
            val y = (cropRect.top * adjustedScaleY).toInt().coerceAtLeast(0)
            val width = (cropRect.width() * adjustedScaleX).toInt().coerceAtMost(bitmap.width - x)
            val height = (cropRect.height() * adjustedScaleY).toInt().coerceAtMost(bitmap.height - y)

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
            val sourceLanguage = (spinnerSourceLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
            val targetLanguage = (spinnerTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode

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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val TAG = "ImageTranslationActivity"
    }
}