package com.example.translator.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "languages")
data class Language(
    @PrimaryKey
    val languageCode: String,
    val languageName: String,
    val nativeName: String,
    val isSupported: Boolean = true,
    val supportsTextTranslation: Boolean = true,
    val supportsVoiceTranslation: Boolean = false,
    val supportsCameraTranslation: Boolean = false
)