package com.example.translator.data.local.dao

import androidx.room.*
import com.example.translator.data.model.Language
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageDao {
    @Query("SELECT * FROM languages WHERE isSupported = 1")
    fun getAllSupportedLanguages(): Flow<List<Language>>

    @Query("SELECT * FROM languages WHERE languageCode = :code")
    suspend fun getLanguageByCode(code: String): Language?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<Language>)

    @Query("DELETE FROM languages")
    suspend fun clearAll()
}