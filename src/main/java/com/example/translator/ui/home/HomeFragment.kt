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
    }

    private fun setupViewModel() {
        val application = requireActivity().application as TranslatorApplication
        val factory = HomeViewModelFactory(application.userRepository, application.languageRepository)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        // Initialize default preferences if needed
        lifecycleScope.launch {
            viewModel.initializeDefaultPreferences()
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialCardView>(R.id.card_camera_translation).setOnClickListener {
            startActivity(Intent(requireContext(), CameraActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.card_text_translation).setOnClickListener {
            // Switch to text translation tab
            (requireActivity() as MainActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.example.translator.ui.text.TextTranslationFragment())
                .commit()
        }

        view.findViewById<MaterialCardView>(R.id.card_image_translation).setOnClickListener {
            startActivity(Intent(requireContext(), ImageTranslationActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.card_voice_translation).setOnClickListener {
            // Switch to voice translation (can be in text fragment)
            val textFragment = com.example.translator.ui.text.TextTranslationFragment()
            val bundle = Bundle().apply {
                putBoolean("start_voice", true)
            }
            textFragment.arguments = bundle

            (requireActivity() as MainActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, textFragment)
                .commit()
        }

        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }
}