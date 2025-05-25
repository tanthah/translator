package com.example.translator.data.repository

import android.content.Context
import com.example.translator.data.local.AppDatabase
import com.example.translator.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val userPreferencesDao = AppDatabase.getDatabase(context).userPreferencesDao()

    fun getUserPreferences(): Flow<UserPreferences?> {
        return userPreferencesDao.getUserPreferences()
    }

    suspend fun updateUserPreferences(preferences: UserPreferences) {
        withContext(Dispatchers.IO) {
            userPreferencesDao.insertUserPreferences(preferences)
        }
    }

    suspend fun initializeDefaultPreferences() {
        withContext(Dispatchers.IO) {
            userPreferencesDao.insertUserPreferences(UserPreferences())
        }
    }
}