package com.divonr.pdftomd.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {
    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val SAVED_API_KEYS = stringSetPreferencesKey("saved_api_keys")
        val MODEL_ID = stringPreferencesKey("model_id")

        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    val apiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }

    val savedApiKeys: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[SAVED_API_KEYS] ?: emptySet()
        }

    val modelId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[MODEL_ID] ?: DEFAULT_MODEL
        }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            // Save as active key
            preferences[API_KEY] = key

            // Add to saved keys
            val currentKeys = preferences[SAVED_API_KEYS] ?: emptySet()
            preferences[SAVED_API_KEYS] = currentKeys + key
        }
    }

    suspend fun setActiveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun setModelId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_ID] = id
        }
    }
}
