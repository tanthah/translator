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
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var viewModel: CameraViewModel
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var previewView: PreviewView
    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var tvDetectedText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var btnCapture: MaterialButton
    private lateinit var btnSwapLanguages: ImageButton
    private lateinit var progressBar: ProgressBar

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

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
            captureImage()
        }

        btnSwapLanguages.setOnClickListener {
            val sourcePosition = spinnerSourceLanguage.selectedItemPosition
            val targetPosition = spinnerTargetLanguage.selectedItemPosition

            spinnerSourceLanguage.setSelection(targetPosition)
            spinnerTargetLanguage.setSelection(sourcePosition)
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(viewLifecycleOwner) { languages ->
            setupLanguageSpinners(languages.filter { it.supportsCameraTranslation })
        }

        viewModel.detectedText.observe(viewLifecycleOwner) { text ->
            tvDetectedText.text = text ?: "No text detected"
        }

        viewModel.translationResult.observe(viewLifecycleOwner) { result ->
            tvTranslatedText.text = result ?: "Translation will appear here"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupLanguageSpinners(languages: List<com.example.translator.data.model.Language>) {
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
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            lifecycleScope.launch {
                val detectedText = viewModel.recognizeText(image)
                if (!detectedText.isNullOrEmpty()) {
                    val sourceLanguage = (spinnerSourceLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "en"
                    val targetLanguage = (spinnerTargetLanguage.selectedItem as? LanguageSpinnerItem)?.language?.languageCode ?: "vi"

                    viewModel.translateDetectedText(detectedText, sourceLanguage, targetLanguage)
                }
            }
        }
        imageProxy.close()
    }

    private fun captureImage() {
        // For now, just trigger text recognition on current frame
        Toast.makeText(requireContext(), "Analyzing current frame...", Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(), REQUIRED_PERMISSIONS, CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
