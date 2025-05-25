package com.example.translator.ui.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.translator.R

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.camera_container, CameraFragment())
                .commit()
        }
    }
}