package com.example.translator.ui.image

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.example.translator.R
import com.example.translator.TranslatorApplication
import kotlinx.coroutines.launch

class ImageTranslationBaseActivity : AppCompatActivity() {

    protected lateinit var viewModel: ImageTranslationViewModel
    protected lateinit var imageHandler: ImageHandler
    protected lateinit var languageHandler: LanguageHandler
    protected lateinit var speechHandler: SpeechHandler
    protected lateinit var uiHandler: UIHandler

    // Views
    protected lateinit var layoutImageSelection: LinearLayout
    protected lateinit var layoutImagePreview: LinearLayout
    protected lateinit var ivSelectedImage: ImageView
    protected lateinit var cropOverlay: CropOverlayView
    protected lateinit var spinnerSourceLanguage: Spinner
    protected lateinit var spinnerTargetLanguage: Spinner
    protected lateinit var btnAutoDetect: MaterialButton
    protected lateinit var tvDetectedLanguage: TextView
    protected lateinit var tvDetectedText: TextView
    protected lateinit var tvTranslatedText: TextView
    protected lateinit var tvSummary: TextView
    protected lateinit var layoutSummary: LinearLayout
    protected lateinit var btnTranslate: MaterialButton
    protected lateinit var progressBar: ProgressBar
    protected lateinit var progressLanguageDetection: ProgressBar
    protected lateinit var progressSummarization: ProgressBar
    protected lateinit var scrollResults: ScrollView

    // Action buttons
    protected lateinit var btnSelectImage: MaterialButton
    protected lateinit var btnTakePhoto: MaterialButton
    protected lateinit var btnConfirmCrop: MaterialButton
    protected lateinit var btnRetake: MaterialButton
    protected lateinit var btnSwapLanguages: ImageButton
    protected lateinit var btnSpeakDetected: MaterialButton
    protected lateinit var btnSpeakTranslated: MaterialButton
    protected lateinit var btnSpeakSummary: MaterialButton
    protected lateinit var btnSummarize: MaterialButton
    protected lateinit var btnSpeechSettings: MaterialButton

    protected var supportedLanguages: List<com.example.translator.data.model.Language> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_translation)

        initializeComponents()
        initializeViews()
        setupViewModel()
        setupClickListeners()
        observeViewModel()

        // Check if launched from camera intent
        if (intent.getBooleanExtra("start_camera", false)) {
            imageHandler.openCamera()
        } else {
            uiHandler.showImageSelectionMode()
        }
    }

    private fun initializeComponents() {
        imageHandler = ImageHandler(this)
        languageHandler = LanguageHandler(this)
        speechHandler = SpeechHandler(this)
        uiHandler = UIHandler(this)
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

        // Language selection views
        spinnerSourceLanguage = findViewById(R.id.spinner_source_language)
        spinnerTargetLanguage = findViewById(R.id.spinner_target_language)
        btnSwapLanguages = findViewById(R.id.btn_swap_languages)
        btnAutoDetect = findViewById(R.id.btn_auto_detect)
        tvDetectedLanguage = findViewById(R.id.tv_detected_language)

        // Result views
        tvDetectedText = findViewById(R.id.tv_detected_text)
        tvTranslatedText = findViewById(R.id.tv_translated_text)
        tvSummary = findViewById(R.id.tv_summary)
        layoutSummary = findViewById(R.id.layout_summary)
        btnTranslate = findViewById(R.id.btn_translate)
        progressBar = findViewById(R.id.progress_bar)
        progressLanguageDetection = findViewById(R.id.progress_language_detection)
        progressSummarization = findViewById(R.id.progress_summarization)
        scrollResults = findViewById(R.id.scroll_results)

        // Speech and summary controls
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
            imageHandler.selectFromGallery()
        }

        btnTakePhoto.setOnClickListener {
            imageHandler.openCamera()
        }

        btnConfirmCrop.setOnClickListener {
            imageHandler.confirmCrop()
        }

        btnRetake.setOnClickListener {
            uiHandler.showImageSelectionMode()
        }

        btnTranslate.setOnClickListener {
            translateImage()
        }

        btnAutoDetect.setOnClickListener {
            languageHandler.autoDetectLanguage()
        }

        btnSwapLanguages.setOnClickListener {
            languageHandler.swapLanguages()
        }

        // Speech buttons
        btnSpeakDetected.setOnClickListener {
            speechHandler.speakDetectedText()
        }

        btnSpeakTranslated.setOnClickListener {
            speechHandler.speakTranslatedText()
        }

        btnSpeakSummary.setOnClickListener {
            speechHandler.speakSummary()
        }

        btnSummarize.setOnClickListener {
            speechHandler.showSummarizationDialog()
        }

        btnSpeechSettings.setOnClickListener {
            speechHandler.showSpeechSettingsDialog()
        }

        // Setup image touch listeners
        imageHandler.setupImageTouchListeners()
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(this) { languages ->
            supportedLanguages = languages.filter { it.supportsCameraTranslation }
            languageHandler.setupLanguageSpinners(supportedLanguages)
        }

        viewModel.detectedText.observe(this) { text ->
            uiHandler.updateDetectedText(text)
        }

        viewModel.detectedLanguage.observe(this) { languageCode ->
            languageHandler.updateDetectedLanguage(languageCode)
        }

        viewModel.detectedLanguageName.observe(this) { languageName ->
            uiHandler.updateDetectedLanguageName(languageName)
        }

        viewModel.translationResult.observe(this) { result ->
            uiHandler.updateTranslationResult(result)
        }

        viewModel.summaryResult.observe(this) { summary ->
            uiHandler.updateSummaryResult(summary)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            uiHandler.updateLoadingState(isLoading)
        }

        viewModel.isDetectingLanguage.observe(this) { isDetecting ->
            uiHandler.updateLanguageDetectionState(isDetecting)
        }

        viewModel.isSummarizing.observe(this) { isSummarizing ->
            uiHandler.updateSummarizationState(isSummarizing)
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                uiHandler.showError(it)
            }
        }
    }

    private fun translateImage() {
        val bitmapToProcess = imageHandler.getBitmapToProcess()

        bitmapToProcess?.let { bitmap ->
            val sourceLanguage = languageHandler.getSelectedSourceLanguageCode()
            val targetLanguage = languageHandler.getSelectedTargetLanguageCode()

            lifecycleScope.launch {
                // Use auto-detect if user hasn't manually selected a specific source language
                if (sourceLanguage == "en" && viewModel.detectedLanguage.value.isNullOrEmpty()) {
                    viewModel.processImageWithAutoDetect(bitmap, targetLanguage)
                } else {
                    viewModel.processImage(bitmap, sourceLanguage, targetLanguage)
                }
            }
        } ?: run {
            uiHandler.showError("Please select an image first")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        imageHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        imageHandler.handlePermissionResult(requestCode, permissions, grantResults)
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