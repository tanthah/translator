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
import kotlinx.coroutines.launch

class TextTranslationFragment : Fragment() {

    private lateinit var viewModel: TextTranslationViewModel
    private lateinit var speechService: SpeechService

    private lateinit var spinnerSourceLanguage: Spinner
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var etSourceText: TextInputEditText
    private lateinit var tvTranslatedText: TextView
    private lateinit var btnTranslate: MaterialButton
    private lateinit var btnVoiceInput: MaterialButton
    private lateinit var btnSpeak: MaterialButton
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnSwapLanguages: ImageButton
    private lateinit var progressBar: ProgressBar

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
                Toast.makeText(requireContext(), "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        btnTranslate.setOnClickListener {
            val sourceText = etSourceText.text.toString().trim()
            if (sourceText.isNotEmpty()) {
                val sourceLanguage = (spinnerSourceLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
                val targetLanguage = (spinnerTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode

                lifecycleScope.launch {
                    viewModel.translateText(sourceText, sourceLanguage, targetLanguage)
                }
            } else {
                Toast.makeText(requireContext(), "Please enter text to translate", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoiceInput.setOnClickListener {
            requestAudioPermissionAndStartRecording()
        }

        btnSpeak.setOnClickListener {
            val translatedText = tvTranslatedText.text.toString()
            if (translatedText.isNotEmpty() && translatedText != "Translation will appear here") {
                val targetLanguage = (spinnerTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
                speechService.speakText(translatedText, targetLanguage)
            }
        }

        btnCopy.setOnClickListener {
            val translatedText = tvTranslatedText.text.toString()
            if (translatedText.isNotEmpty() && translatedText != "Translation will appear here") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Translated Text", translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnSwapLanguages.setOnClickListener {
            val sourcePosition = spinnerSourceLanguage.selectedItemPosition
            val targetPosition = spinnerTargetLanguage.selectedItemPosition

            spinnerSourceLanguage.setSelection(targetPosition)
            spinnerTargetLanguage.setSelection(sourcePosition)

            // Swap text if both fields have content
            val sourceText = etSourceText.text.toString()
            val targetText = tvTranslatedText.text.toString()

            if (sourceText.isNotEmpty() && targetText.isNotEmpty() && targetText != "Translation will appear here") {
                etSourceText.setText(targetText)
                tvTranslatedText.text = sourceText
            }
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(viewLifecycleOwner) { languages ->
            setupLanguageSpinners(languages)
        }

        viewModel.translationResult.observe(viewLifecycleOwner) { result ->
            tvTranslatedText.text = result ?: "Translation failed"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnTranslate.isEnabled = !isLoading
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
        val sourceLanguage = (spinnerSourceLanguage.selectedItem as LanguageSpinnerItem).language.languageCode

        lifecycleScope.launch {
            speechService.startSpeechRecognition(sourceLanguage).collect { result ->
                when (result) {
                    is SpeechService.SpeechResult.Ready -> {
                        btnVoiceInput.text = "Listening..."
                        btnVoiceInput.isEnabled = false
                    }
                    is SpeechService.SpeechResult.FinalResult -> {
                        etSourceText.setText(result.text)
                        btnVoiceInput.text = "Voice Input"
                        btnVoiceInput.isEnabled = true

                        // Auto-translate if text is recognized
                        val targetLanguage = (spinnerTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
                        viewModel.translateText(result.text, sourceLanguage, targetLanguage)
                    }
                    is SpeechService.SpeechResult.Error -> {
                        btnVoiceInput.text = "Voice Input"
                        btnVoiceInput.isEnabled = true
                        Toast.makeText(requireContext(), "Speech recognition error", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
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
                Toast.makeText(requireContext(), "Audio permission required for voice input", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.release()
    }
}