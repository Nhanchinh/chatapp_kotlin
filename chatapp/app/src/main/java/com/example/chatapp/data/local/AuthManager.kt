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
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_FULL_NAME_KEY = stringPreferencesKey("user_full_name")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val USER_FRIEND_COUNT_KEY = stringPreferencesKey("user_friend_count")
        private val USER_LOCATION_KEY = stringPreferencesKey("user_location")
        private val USER_HOMETOWN_KEY = stringPreferencesKey("user_hometown")
        private val USER_BIRTH_YEAR_KEY = stringPreferencesKey("user_birth_year")
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
     * User profile fields
     */
    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL_KEY]
    }

    val userFullName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_FULL_NAME_KEY]
    }

    val userRole: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ROLE_KEY]
    }

    val userFriendCount: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_FRIEND_COUNT_KEY]
    }

    val userLocation: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_LOCATION_KEY]
    }

    val userHometown: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_HOMETOWN_KEY]
    }

    val userBirthYear: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_BIRTH_YEAR_KEY]
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
     * Save user profile fields
     */
    suspend fun saveUserProfile(
        id: String?, 
        email: String?, 
        fullName: String?, 
        role: String?, 
        friendCount: Int? = null,
        location: String? = null,
        hometown: String? = null,
        birthYear: Int? = null
    ) {
        context.dataStore.edit { preferences ->
            id?.let { preferences[USER_ID_KEY] = it }
            email?.let { preferences[USER_EMAIL_KEY] = it }
            fullName?.let { preferences[USER_FULL_NAME_KEY] = it }
            role?.let { preferences[USER_ROLE_KEY] = it }
            friendCount?.let { preferences[USER_FRIEND_COUNT_KEY] = it.toString() }
            location?.let { preferences[USER_LOCATION_KEY] = it }
            hometown?.let { preferences[USER_HOMETOWN_KEY] = it }
            birthYear?.let { preferences[USER_BIRTH_YEAR_KEY] = it.toString() }
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
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(USER_FULL_NAME_KEY)
            preferences.remove(USER_ROLE_KEY)
            preferences.remove(USER_FRIEND_COUNT_KEY)
            preferences.remove(USER_LOCATION_KEY)
            preferences.remove(USER_HOMETOWN_KEY)
            preferences.remove(USER_BIRTH_YEAR_KEY)
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

    suspend fun getUserProfileOnce(): UserProfileLocal? {
        val id = userId.first()
        val email = userEmail.first()
        val fullName = userFullName.first()
        val role = userRole.first()
        val friendCount = userFriendCount.first()?.toIntOrNull()
        val location = userLocation.first()
        val hometown = userHometown.first()
        val birthYear = userBirthYear.first()?.toIntOrNull()
        return if (id != null || email != null || fullName != null || role != null) {
            UserProfileLocal(
                id = id,
                email = email,
                fullName = fullName,
                role = role,
                friendCount = friendCount,
                location = location,
                hometown = hometown,
                birthYear = birthYear
            )
        } else null
    }
    
    /**
     * Check login state one time
     */
    suspend fun isLoggedInOnce(): Boolean {
        return isLoggedIn.first()
    }
}

data class UserProfileLocal(
    val id: String?,
    val email: String?,
    val fullName: String?,
    val role: String?,
    val friendCount: Int?,
    val location: String?,
    val hometown: String?,
    val birthYear: Int?
)

