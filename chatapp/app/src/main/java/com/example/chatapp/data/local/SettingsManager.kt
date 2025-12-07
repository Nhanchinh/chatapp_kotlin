package com.example.chatapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property to access DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val E2EE_ENABLED_KEY = booleanPreferencesKey("e2ee_enabled")
    }

    /**
     * Get E2EE enabled state
     * Default: true (enabled by default)
     */
    val isE2EEEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[E2EE_ENABLED_KEY] ?: true  // Default to enabled
    }

    /**
     * Set E2EE enabled state
     */
    suspend fun setE2EEEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[E2EE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get current E2EE enabled state (synchronous, for one-time reads)
     */
    suspend fun getE2EEEnabled(): Boolean {
        return isE2EEEnabled.map { it }.first()
    }
}

