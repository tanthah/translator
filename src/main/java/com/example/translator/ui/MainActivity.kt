package com.example.translator.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.translator.R
import com.example.translator.ui.home.HomeFragment
import com.example.translator.ui.text.TextTranslationFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()

        // Load default fragment if not restoring state
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), R.id.nav_home)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation)

        // Set default selected item
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment(), R.id.nav_home)
                    true
                }
                R.id.nav_text -> {
                    loadFragment(TextTranslationFragment(), R.id.nav_text)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, menuItemId: Int) {
        // Avoid unnecessary fragment transactions
        if (currentFragment?.javaClass == fragment.javaClass) {
            return
        }

        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()

            currentFragment = fragment

            // Update bottom navigation selection
            if (bottomNavigation.selectedItemId != menuItemId) {
                bottomNavigation.selectedItemId = menuItemId
            }
        } catch (e: Exception) {
            // Handle fragment transaction errors
            e.printStackTrace()
        }
    }

    fun switchToTextTranslation(startVoice: Boolean = false) {
        val textFragment = TextTranslationFragment()
        if (startVoice) {
            val bundle = Bundle().apply {
                putBoolean("start_voice", true)
            }
            textFragment.arguments = bundle
        }
        loadFragment(textFragment, R.id.nav_text)
    }

    override fun onBackPressed() {
        // Handle back navigation properly
        if (bottomNavigation.selectedItemId != R.id.nav_home) {
            loadFragment(HomeFragment(), R.id.nav_home)
        } else {
            super.onBackPressed()
        }
    }
}