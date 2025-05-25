package com.example.translator.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey
    val id: Int = 1,
    val defaultSourceLanguage: String = "en",
    val defaultTargetLanguage: String = "vi",
    val theme: String = "light", // light, dark, system
    val autoDetectLanguage: Boolean = true,
    val ttsEnabled: Boolean = true,
    val cameraAutoTranslate: Boolean = true,
    val fontSize: String = "medium" // small, medium, large
)
