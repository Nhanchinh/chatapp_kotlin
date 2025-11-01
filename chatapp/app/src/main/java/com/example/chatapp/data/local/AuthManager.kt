package com.example.chatapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property to access DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthManager(private val context: Context) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val TOKEN_EXPIRY_TIME_KEY = stringPreferencesKey("token_expiry_time")
    }

    /**
     * Get the current access token
     */
    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }

    /**
     * Get the current login state
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN_KEY] ?: false
    }

    /**
     * Get the token expiry time
     */
    val tokenExpiryTime: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_EXPIRY_TIME_KEY]
    }

    /**
     * Save access token and set login state to true
     */
    suspend fun saveAccessToken(token: String, expiryTime: Long? = null) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = token
            preferences[IS_LOGGED_IN_KEY] = true
            expiryTime?.let {
                preferences[TOKEN_EXPIRY_TIME_KEY] = it.toString()
            }
        }
    }

    /**
     * Clear access token and reset login state (logout)
     */
    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
            preferences.remove(TOKEN_EXPIRY_TIME_KEY)
        }
    }

    /**
     * Check if token is expired
     */
    suspend fun isTokenExpired(): Boolean {
        val expiryTime = tokenExpiryTime.first()
        return expiryTime?.let {
            try {
                val expiry = it.toLong()
                System.currentTimeMillis() >= expiry
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /**
     * Get current access token as a String (one-time read)
     */
    suspend fun getAccessTokenOnce(): String? {
        return accessToken.first()
    }
    
    /**
     * Check login state one time
     */
    suspend fun isLoggedInOnce(): Boolean {
        return isLoggedIn.first()
    }
}

