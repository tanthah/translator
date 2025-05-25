package com.example.translator.ui.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
import kotlinx.coroutines.launch
import java.io.IOException

class ImageTranslationActivity : AppCompatActivity() {

    private lateinit var viewModel: ImageTranslationViewModel

    private lateinit var ivSelectedImage: ImageView
    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var tvDetectedText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var btnTranslate: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var selectedImageBitmap: Bitmap? = null

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
    }

    private fun initializeViews() {
        ivSelectedImage = findViewById(R.id.iv_selected_image)
        spinnerSourceLanguage = findViewById(R.id.spinner_source_language)
        spinnerTargetLanguage = findViewById(R.id.spinner_target_language)
        tvDetectedText = findViewById(R.id.tv_detected_text)
        tvTranslatedText = findViewById(R.id.tv_translated_text)
        btnSelectImage = findViewById(R.id.btn_select_image)
        btnTakePhoto = findViewById(R.id.btn_take_photo)
        btnTranslate = findViewById(R.id.btn_translate)
        progressBar = findViewById(R.id.progress_bar)

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

        btnTranslate.setOnClickListener {
            selectedImageBitmap?.let { bitmap ->
                val sourceLanguage = (spinnerSourceLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
                val targetLanguage = (spinnerTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode

                lifecycleScope.launch {
                    viewModel.processImage(bitmap, sourceLanguage, targetLanguage)
                }
            } ?: run {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            }
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
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnTranslate.isEnabled = !isLoading && selectedImageBitmap != null
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

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAMERA)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    data?.data?.let { uri ->
                        try {
                            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            setSelectedImage(bitmap)
                        } catch (e: IOException) {
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_IMAGE_CAMERA -> {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    bitmap?.let { setSelectedImage(it) }
                }
            }
        }
    }

    private fun setSelectedImage(bitmap: Bitmap) {
        selectedImageBitmap = bitmap
        ivSelectedImage.setImageBitmap(bitmap)
        ivSelectedImage.visibility = View.VISIBLE
        btnTranslate.isEnabled = true

        // Clear previous results
        tvDetectedText.text = ""
        tvTranslatedText.text = ""
        tvDetectedText.visibility = View.GONE
        tvTranslatedText.visibility = View.GONE
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
}
