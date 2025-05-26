package com.example.translator.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var viewModel: CameraViewModel
    private lateinit var cameraExecutor: ExecutorService

    // Views
    private lateinit var previewView: PreviewView
    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var tvDetectedText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var btnSwapLanguages: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var progressBar: ProgressBar

    // Camera components
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    // Processing control
    private var lastProcessTime = 0L
    private var processingJob: Job? = null
    private val PROCESSING_INTERVAL = 2000L // 2 seconds between processing

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupViewModel()
        setupClickListeners()
        observeViewModel()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun initializeViews(view: View) {
        previewView = view.findViewById(R.id.preview_view)
        spinnerSourceLanguage = view.findViewById(R.id.spinner_source_language)
        spinnerTargetLanguage = view.findViewById(R.id.spinner_target_language)
        tvDetectedText = view.findViewById(R.id.tv_detected_text)
        tvTranslatedText = view.findViewById(R.id.tv_translated_text)
        btnCapture = view.findViewById(R.id.btn_capture)
        btnSwapLanguages = view.findViewById(R.id.btn_swap_languages)
        btnFlash = view.findViewById(R.id.btn_flash)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupViewModel() {
        val application = requireActivity().application as TranslatorApplication
        val factory = CameraViewModelFactory(
            application.userRepository,
            application.languageRepository,
            requireContext()
        )
        viewModel = ViewModelProvider(this, factory)[CameraViewModel::class.java]
    }

    private fun setupClickListeners() {
        btnCapture.setOnClickListener {
            captureCurrentFrame()
        }

        btnSwapLanguages.setOnClickListener {
            swapLanguages()
        }

        btnFlash.setOnClickListener {
            toggleFlash()
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(viewLifecycleOwner) { languages ->
            setupLanguageSpinners(languages.filter { it.supportsCameraTranslation })
        }

        viewModel.detectedText.observe(viewLifecycleOwner) { text ->
            tvDetectedText.text = text ?: getString(R.string.no_text_detected)
        }

        viewModel.translationResult.observe(viewLifecycleOwner) { result ->
            tvTranslatedText.text = result ?: getString(R.string.translation_result)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupLanguageSpinners(languages: List<com.example.translator.data.model.Language>) {
        if (languages.isEmpty()) return

        val adapter = LanguageSpinnerAdapter(requireContext(), languages)

        spinnerSourceLanguage.adapter = adapter
        spinnerTargetLanguage.adapter = adapter

        // Set default selections
        val defaultSourceIndex = languages.indexOfFirst { it.languageCode == "en" }
        val defaultTargetIndex = languages.indexOfFirst { it.languageCode == "vi" }

        if (defaultSourceIndex != -1) spinnerSourceLanguage.setSelection(defaultSourceIndex)
        if (defaultTargetIndex != -1) spinnerTargetLanguage.setSelection(defaultTargetIndex)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showError("Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageSafely(imageProxy)
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            showError("Camera binding failed")
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageSafely(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Throttle processing to avoid overwhelming the system
        if (currentTime - lastProcessTime < PROCESSING_INTERVAL) {
            imageProxy.close()
            return
        }

        lastProcessTime = currentTime

        // Cancel previous processing job
        processingJob?.cancel()

        try {
            // Use the experimental @OptIn annotation to access image
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                processingJob = lifecycleScope.launch {
                    try {
                        val detectedText = viewModel.recognizeText(image)
                        if (!detectedText.isNullOrEmpty()) {
                            val sourceLanguage = getSelectedSourceLanguageCode()
                            val targetLanguage = getSelectedTargetLanguageCode()

                            viewModel.translateDetectedText(detectedText, sourceLanguage, targetLanguage)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Image processing failed", e)
                    } finally {
                        imageProxy.close()
                    }
                }
            } else {
                Log.w(TAG, "MediaImage is null")
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            imageProxy.close()
        }
    }

    private fun captureCurrentFrame() {
        Toast.makeText(requireContext(), getString(R.string.analyzing_frame), Toast.LENGTH_SHORT).show()
        // The current frame will be processed by the ongoing image analysis
    }

    private fun swapLanguages() {
        val sourcePosition = spinnerSourceLanguage.selectedItemPosition
        val targetPosition = spinnerTargetLanguage.selectedItemPosition

        spinnerSourceLanguage.setSelection(targetPosition)
        spinnerTargetLanguage.setSelection(sourcePosition)
    }

    private fun toggleFlash() {
        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }

        // Update flash icon
        val flashIcon = if (flashMode == ImageCapture.FLASH_MODE_ON) {
            android.R.drawable.ic_menu_day
        } else {
            android.R.drawable.ic_menu_day
        }
        btnFlash.setImageResource(flashIcon)

        // Rebuild camera with new flash setting
        bindCameraUseCases()
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(), REQUIRED_PERMISSIONS, CAMERA_PERMISSION_CODE
        )
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showError(getString(R.string.camera_permission_required))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted() && cameraProvider == null) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel any ongoing processing
        processingJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up camera resources
        cameraProvider?.unbindAll()
        camera = null
        cameraProvider = null

        // Cancel processing job
        processingJob?.cancel()

        // Shutdown executor
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}