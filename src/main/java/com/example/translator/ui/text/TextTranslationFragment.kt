package com.example.translator.ui.text

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.services.SpeechService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TextTranslationFragment : Fragment() {

    private lateinit var viewModel: TextTranslationViewModel
    private lateinit var speechService: SpeechService

    // Views
    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var etSourceText: TextInputEditText
    private lateinit var tvTranslatedText: TextView
    private lateinit var btnTranslate: MaterialButton
    private lateinit var btnVoiceInput: MaterialButton
    private lateinit var btnSpeakSource: MaterialButton  // Thêm nút đọc text nguồn
    private lateinit var btnSpeak: MaterialButton
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnSwapLanguages: ImageButton
    private lateinit var progressBar: ProgressBar

    // Voice recognition
    private var voiceRecognitionJob: Job? = null
    private var isListening = false

    private val RECORD_AUDIO_PERMISSION_CODE = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_text_translation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupViewModel()
        setupSpeechService()
        setupClickListeners()
        observeViewModel()

        // Check if voice translation was requested
        arguments?.getBoolean("start_voice", false)?.let { startVoice ->
            if (startVoice) {
                requestAudioPermissionAndStartRecording()
            }
        }
    }

    private fun initializeViews(view: View) {
        spinnerSourceLanguage = view.findViewById(R.id.spinner_source_language)
        spinnerTargetLanguage = view.findViewById(R.id.spinner_target_language)
        etSourceText = view.findViewById(R.id.et_source_text)
        tvTranslatedText = view.findViewById(R.id.tv_translated_text)
        btnTranslate = view.findViewById(R.id.btn_translate)
        btnVoiceInput = view.findViewById(R.id.btn_voice_input)
        btnSpeakSource = view.findViewById(R.id.btn_speak_source)  // Thêm button mới
        btnSpeak = view.findViewById(R.id.btn_speak)
        btnCopy = view.findViewById(R.id.btn_copy)
        btnSwapLanguages = view.findViewById(R.id.btn_swap_languages)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupViewModel() {
        val application = requireActivity().application as TranslatorApplication
        val factory = TextTranslationViewModelFactory(
            application.userRepository,
            application.languageRepository,
            requireContext()
        )
        viewModel = ViewModelProvider(this, factory)[TextTranslationViewModel::class.java]
    }

    private fun setupSpeechService() {
        speechService = SpeechService(requireContext())
        speechService.initializeTextToSpeech { success ->
            if (!success) {
                showToast(getString(R.string.tts_not_available))
            }
        }
    }

    private fun setupClickListeners() {
        btnTranslate.setOnClickListener {
            performTranslation()
        }

        btnVoiceInput.setOnClickListener {
            if (isListening) {
                stopVoiceRecording()
            } else {
                requestAudioPermissionAndStartRecording()
            }
        }

        // Thêm listener cho nút đọc text nguồn
        btnSpeakSource.setOnClickListener {
            speakSourceText()
        }

        btnSpeak.setOnClickListener {
            speakTranslation()
        }

        btnCopy.setOnClickListener {
            copyTranslationToClipboard()
        }

        btnSwapLanguages.setOnClickListener {
            swapLanguages()
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(viewLifecycleOwner) { languages ->
            setupLanguageSpinners(languages)
        }

        viewModel.translationResult.observe(viewLifecycleOwner) { result ->
            tvTranslatedText.text = result ?: getString(R.string.translation_failed)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnTranslate.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showToast(it)
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

    private fun performTranslation() {
        val sourceText = etSourceText.text?.toString()?.trim()

        if (sourceText.isNullOrEmpty()) {
            showToast(getString(R.string.enter_text_hint))
            return
        }

        if (sourceText.length > 5000) {
            showToast("Text too long. Maximum 5000 characters allowed.")
            return
        }

        val sourceLanguage = getSelectedSourceLanguageCode()
        val targetLanguage = getSelectedTargetLanguageCode()

        if (sourceLanguage == targetLanguage) {
            tvTranslatedText.text = sourceText
            return
        }

        lifecycleScope.launch {
            try {
                viewModel.translateText(sourceText, sourceLanguage, targetLanguage)
            } catch (e: Exception) {
                showToast(getString(R.string.translation_failed))
            }
        }
    }

    // Thêm chức năng đọc text nguồn
    private fun speakSourceText() {
        val sourceText = etSourceText.text?.toString()?.trim()

        if (sourceText.isNullOrEmpty()) {
            showToast("No text to speak")
            return
        }

        val sourceLanguage = getSelectedSourceLanguageCode()
        speechService.speakText(sourceText, sourceLanguage)
    }

    private fun requestAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            startVoiceRecording()
        }
    }

    private fun startVoiceRecording() {
        if (isListening) return

        val sourceLanguage = getSelectedSourceLanguageCode()
        isListening = true
        btnVoiceInput.text = getString(R.string.listening)
        btnVoiceInput.isEnabled = false

        voiceRecognitionJob = lifecycleScope.launch {
            try {
                speechService.startSpeechRecognition(sourceLanguage).collect { result ->
                    when (result) {
                        is SpeechService.SpeechResult.Ready -> {
                            // Voice recognition is ready
                        }
                        is SpeechService.SpeechResult.FinalResult -> {
                            etSourceText.setText(result.text)
                            stopVoiceRecording()

                            // Auto-translate if text is recognized
                            val targetLanguage = getSelectedTargetLanguageCode()
                            if (result.text.isNotEmpty()) {
                                viewModel.translateText(result.text, sourceLanguage, targetLanguage)
                            }
                        }
                        is SpeechService.SpeechResult.Error -> {
                            stopVoiceRecording()
                            showToast(getString(R.string.speech_recognition_failed))
                        }
                        else -> {
                            // Handle other speech results if needed
                        }
                    }
                }
            } catch (e: Exception) {
                stopVoiceRecording()
                showToast(getString(R.string.speech_recognition_failed))
            }
        }
    }

    private fun stopVoiceRecording() {
        voiceRecognitionJob?.cancel()
        speechService.stopSpeechRecognition()
        isListening = false
        btnVoiceInput.text = getString(R.string.voice_input)
        btnVoiceInput.isEnabled = true
    }

    private fun speakTranslation() {
        val translatedText = tvTranslatedText.text?.toString()

        if (translatedText.isNullOrEmpty() || translatedText == getString(R.string.translation_result)) {
            showToast("No translation to speak")
            return
        }

        val targetLanguage = getSelectedTargetLanguageCode()
        speechService.speakText(translatedText, targetLanguage)
    }

    private fun copyTranslationToClipboard() {
        val translatedText = tvTranslatedText.text?.toString()

        if (translatedText.isNullOrEmpty() || translatedText == getString(R.string.translation_result)) {
            showToast("No translation to copy")
            return
        }

        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Translated Text", translatedText)
            clipboard.setPrimaryClip(clip)
            showToast(getString(R.string.text_copied))
        } catch (e: Exception) {
            showToast("Failed to copy text")
        }
    }

    private fun swapLanguages() {
        val sourcePosition = spinnerSourceLanguage.selectedItemPosition
        val targetPosition = spinnerTargetLanguage.selectedItemPosition

        spinnerSourceLanguage.setSelection(targetPosition)
        spinnerTargetLanguage.setSelection(sourcePosition)

        // Swap text if both fields have content
        val sourceText = etSourceText.text?.toString()
        val targetText = tvTranslatedText.text?.toString()

        if (!sourceText.isNullOrEmpty() && !targetText.isNullOrEmpty() &&
            targetText != getString(R.string.translation_result)) {
            etSourceText.setText(targetText)
            tvTranslatedText.text = sourceText
        }
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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording()
            } else {
                showToast(getString(R.string.audio_permission_required))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopVoiceRecording()
        speechService.stopSpeaking()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceRecognitionJob?.cancel()
        speechService.release()
    }
}