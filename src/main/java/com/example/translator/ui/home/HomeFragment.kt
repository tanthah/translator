package com.example.translator.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.example.translator.R
import com.example.translator.TranslatorApplication
import com.example.translator.ui.MainActivity
import com.example.translator.ui.camera.CameraActivity
import com.example.translator.ui.image.ImageTranslationActivity
import com.example.translator.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupClickListeners(view)
        initializeDefaultPreferences()
    }

    private fun setupViewModel() {
        val application = requireActivity().application as TranslatorApplication
        val factory = HomeViewModelFactory(application.userRepository, application.languageRepository)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    private fun setupClickListeners(view: View) {
        // Camera Translation Card
        view.findViewById<MaterialCardView>(R.id.card_camera_translation)?.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), CameraActivity::class.java))
            } catch (e: Exception) {
                // Handle activity not found or other errors
                e.printStackTrace()
            }
        }

        // Text Translation Card
        view.findViewById<MaterialCardView>(R.id.card_text_translation)?.setOnClickListener {
            try {
                (requireActivity() as? MainActivity)?.switchToTextTranslation(false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Image Translation Card
        view.findViewById<MaterialCardView>(R.id.card_image_translation)?.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), ImageTranslationActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Voice Translation Card
        view.findViewById<MaterialCardView>(R.id.card_voice_translation)?.setOnClickListener {
            try {
                (requireActivity() as? MainActivity)?.switchToTextTranslation(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Settings Button
        view.findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initializeDefaultPreferences() {
        lifecycleScope.launch {
            try {
                viewModel.initializeDefaultPreferences()
            } catch (e: Exception) {
                // Handle initialization error silently
                e.printStackTrace()
            }
        }
    }
}