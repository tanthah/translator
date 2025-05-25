package com.example.translator.ui.settings

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.data.model.UserPreferences
import com.example.translator.ui.text.LanguageSpinnerAdapter
import com.example.translator.ui.text.LanguageSpinnerItem
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: SettingsViewModel

    private lateinit var spinnerDefaultSourceLanguage: Spinner
    private lateinit var spinnerDefaultTargetLanguage: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var spinnerFontSize: Spinner
    private lateinit var switchAutoDetectLanguage: Switch
    private lateinit var switchTtsEnabled: Switch
    private lateinit var switchCameraAutoTranslate: Switch
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupActionBar()
        initializeViews()
        setupViewModel()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    private fun initializeViews() {
        spinnerDefaultSourceLanguage = findViewById(R.id.spinner_default_source_language)
        spinnerDefaultTargetLanguage = findViewById(R.id.spinner_default_target_language)
        spinnerTheme = findViewById(R.id.spinner_theme)
        spinnerFontSize = findViewById(R.id.spinner_font_size)
        switchAutoDetectLanguage = findViewById(R.id.switch_auto_detect_language)
        switchTtsEnabled = findViewById(R.id.switch_tts_enabled)
        switchCameraAutoTranslate = findViewById(R.id.switch_camera_auto_translate)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun setupViewModel() {
        val application = application as TranslatorApplication
        val factory = SettingsViewModelFactory(
            application.userRepository,
            application.languageRepository
        )
        viewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun observeViewModel() {
        viewModel.supportedLanguages.observe(this) { languages ->
            setupLanguageSpinners(languages)
        }

        viewModel.userPreferences.observe(this) { preferences ->
            preferences?.let { loadSettings(it) }
        }
    }

    private fun setupLanguageSpinners(languages: List<com.example.translator.data.model.Language>) {
        val adapter = LanguageSpinnerAdapter(this, languages)

        spinnerDefaultSourceLanguage.adapter = adapter
        spinnerDefaultTargetLanguage.adapter = adapter

        // Setup theme spinner
        val themeOptions = arrayOf("Light", "Dark", "System")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themeOptions)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTheme.adapter = themeAdapter

        // Setup font size spinner
        val fontSizeOptions = arrayOf("Small", "Medium", "Large")
        val fontSizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizeOptions)
        fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFontSize.adapter = fontSizeAdapter
    }

    private fun loadSettings(preferences: UserPreferences) {
        // Set language selections
        val sourceLanguageAdapter = spinnerDefaultSourceLanguage.adapter as LanguageSpinnerAdapter
        val targetLanguageAdapter = spinnerDefaultTargetLanguage.adapter as LanguageSpinnerAdapter

        for (i in 0 until sourceLanguageAdapter.count) {
            val item = sourceLanguageAdapter.getItem(i)
            if (item.language.languageCode == preferences.defaultSourceLanguage) {
                spinnerDefaultSourceLanguage.setSelection(i)
                break
            }
        }

        for (i in 0 until targetLanguageAdapter.count) {
            val item = targetLanguageAdapter.getItem(i)
            if (item.language.languageCode == preferences.defaultTargetLanguage) {
                spinnerDefaultTargetLanguage.setSelection(i)
                break
            }
        }

        // Set theme selection
        val themeIndex = when (preferences.theme) {
            "light" -> 0
            "dark" -> 1
            "system" -> 2
            else -> 0
        }
        spinnerTheme.setSelection(themeIndex)

        // Set font size selection
        val fontSizeIndex = when (preferences.fontSize) {
            "small" -> 0
            "medium" -> 1
            "large" -> 2
            else -> 1
        }
        spinnerFontSize.setSelection(fontSizeIndex)

        // Set switches
        switchAutoDetectLanguage.isChecked = preferences.autoDetectLanguage
        switchTtsEnabled.isChecked = preferences.ttsEnabled
        switchCameraAutoTranslate.isChecked = preferences.cameraAutoTranslate
    }

    private fun saveSettings() {
        val sourceLanguage = (spinnerDefaultSourceLanguage.selectedItem as LanguageSpinnerItem).language.languageCode
        val targetLanguage = (spinnerDefaultTargetLanguage.selectedItem as LanguageSpinnerItem).language.languageCode

        val theme = when (spinnerTheme.selectedItemPosition) {
            0 -> "light"
            1 -> "dark"
            2 -> "system"
            else -> "light"
        }

        val fontSize = when (spinnerFontSize.selectedItemPosition) {
            0 -> "small"
            1 -> "medium"
            2 -> "large"
            else -> "medium"
        }

        val preferences = UserPreferences(
            defaultSourceLanguage = sourceLanguage,
            defaultTargetLanguage = targetLanguage,
            theme = theme,
            autoDetectLanguage = switchAutoDetectLanguage.isChecked,
            ttsEnabled = switchTtsEnabled.isChecked,
            cameraAutoTranslate = switchCameraAutoTranslate.isChecked,
            fontSize = fontSize
        )

        lifecycleScope.launch {
            viewModel.updateUserPreferences(preferences)
            Toast.makeText(this@SettingsActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}