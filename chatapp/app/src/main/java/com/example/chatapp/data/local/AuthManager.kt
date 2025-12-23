 package com.example.chatapp.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chatapp.data.encryption.KeyManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.RefreshTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * AuthManager with EncryptedSharedPreferences for secure token storage.
 * All auth data (tokens, user info) is encrypted with AES-256-GCM.
 */
class AuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_FILE_NAME = "auth_encrypted_prefs"
        
        // Keys for encrypted storage
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_TOKEN_EXPIRY_TIME = "token_expiry_time"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_TOKEN_EXPIRY_TIME = "refresh_token_expiry_time"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_FULL_NAME = "user_full_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_FRIEND_COUNT = "user_friend_count"
        private const val KEY_USER_LOCATION = "user_location"
        private const val KEY_USER_HOMETOWN = "user_hometown"
        private const val KEY_USER_BIRTH_YEAR = "user_birth_year"
        private const val KEY_USER_AVATAR = "user_avatar"
    }

    private val refreshMutex = Mutex()
    
    // EncryptedSharedPreferences with AES-256-GCM encryption
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefsSafely()
    }
    
    /**
     * Safely create EncryptedSharedPreferences. 
     * If the stored file cannot be decrypted (e.g., after reinstall), delete and recreate.
     */
    private fun createEncryptedPrefsSafely(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return try {
            createEncryptedPrefs(masterKey)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Failed to open encrypted prefs (security). Resetting file.", e)
            deleteEncryptedPrefsFile()
            createEncryptedPrefs(masterKey)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open encrypted prefs (IO). Resetting file.", e)
            deleteEncryptedPrefsFile()
            createEncryptedPrefs(masterKey)
        }
    }
    
    private fun createEncryptedPrefs(masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    
    private fun deleteEncryptedPrefsFile() {
        try {
            val prefsFile = File(context.filesDir.parent + "/shared_prefs/$PREFS_FILE_NAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
                Log.d(TAG, "Deleted corrupted encrypted prefs file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete corrupted encrypted prefs file", e)
        }
    }

    // StateFlows to maintain reactive API compatibility
    private val _accessToken = MutableStateFlow<String?>(null)
    private val _isLoggedIn = MutableStateFlow(false)
    private val _tokenExpiryTime = MutableStateFlow<String?>(null)
    private val _refreshToken = MutableStateFlow<String?>(null)
    private val _refreshTokenExpiryTime = MutableStateFlow<String?>(null)
    private val _userId = MutableStateFlow<String?>(null)
    private val _userEmail = MutableStateFlow<String?>(null)
    private val _userFullName = MutableStateFlow<String?>(null)
    private val _userRole = MutableStateFlow<String?>(null)
    private val _userFriendCount = MutableStateFlow<String?>(null)
    private val _userLocation = MutableStateFlow<String?>(null)
    private val _userHometown = MutableStateFlow<String?>(null)
    private val _userBirthYear = MutableStateFlow<String?>(null)
    private val _userAvatar = MutableStateFlow<String?>(null)
    
    // SharedPreferences listener to sync StateFlows when data changes (cross-instance sync)
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        // Reload the changed value into StateFlow
        when (key) {
            KEY_ACCESS_TOKEN -> _accessToken.value = prefs.getString(key, null)
            KEY_IS_LOGGED_IN -> _isLoggedIn.value = prefs.getBoolean(key, false)
            KEY_TOKEN_EXPIRY_TIME -> _tokenExpiryTime.value = prefs.getString(key, null)
            KEY_REFRESH_TOKEN -> _refreshToken.value = prefs.getString(key, null)
            KEY_REFRESH_TOKEN_EXPIRY_TIME -> _refreshTokenExpiryTime.value = prefs.getString(key, null)
            KEY_USER_ID -> _userId.value = prefs.getString(key, null)
            KEY_USER_EMAIL -> _userEmail.value = prefs.getString(key, null)
            KEY_USER_FULL_NAME -> _userFullName.value = prefs.getString(key, null)
            KEY_USER_ROLE -> _userRole.value = prefs.getString(key, null)
            KEY_USER_FRIEND_COUNT -> _userFriendCount.value = prefs.getString(key, null)
            KEY_USER_LOCATION -> _userLocation.value = prefs.getString(key, null)
            KEY_USER_HOMETOWN -> _userHometown.value = prefs.getString(key, null)
            KEY_USER_BIRTH_YEAR -> _userBirthYear.value = prefs.getString(key, null)
            KEY_USER_AVATAR -> _userAvatar.value = prefs.getString(key, null)
        }
    }
    
    init {
        // Load initial values from encrypted storage
        loadFromEncryptedPrefs()
        
        // Register listener to sync StateFlows when data changes
        encryptedPrefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }
    
    private fun loadFromEncryptedPrefs() {
        _accessToken.value = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        _isLoggedIn.value = encryptedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        _tokenExpiryTime.value = encryptedPrefs.getString(KEY_TOKEN_EXPIRY_TIME, null)
        _refreshToken.value = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        _refreshTokenExpiryTime.value = encryptedPrefs.getString(KEY_REFRESH_TOKEN_EXPIRY_TIME, null)
        _userId.value = encryptedPrefs.getString(KEY_USER_ID, null)
        _userEmail.value = encryptedPrefs.getString(KEY_USER_EMAIL, null)
        _userFullName.value = encryptedPrefs.getString(KEY_USER_FULL_NAME, null)
        _userRole.value = encryptedPrefs.getString(KEY_USER_ROLE, null)
        _userFriendCount.value = encryptedPrefs.getString(KEY_USER_FRIEND_COUNT, null)
        _userLocation.value = encryptedPrefs.getString(KEY_USER_LOCATION, null)
        _userHometown.value = encryptedPrefs.getString(KEY_USER_HOMETOWN, null)
        _userBirthYear.value = encryptedPrefs.getString(KEY_USER_BIRTH_YEAR, null)
        _userAvatar.value = encryptedPrefs.getString(KEY_USER_AVATAR, null)
    }

    /**
     * Get the current access token (reactive)
     */
    val accessToken: Flow<String?> = _accessToken.asStateFlow()

    /**
     * Get the current login state (reactive)
     */
    val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    /**
     * Get the token expiry time (reactive)
     */
    val tokenExpiryTime: Flow<String?> = _tokenExpiryTime.asStateFlow()

    val refreshToken: Flow<String?> = _refreshToken.asStateFlow()

    val refreshTokenExpiryTime: Flow<String?> = _refreshTokenExpiryTime.asStateFlow()

    /**
     * User profile fields (reactive)
     */
    val userId: Flow<String?> = _userId.asStateFlow()
    val userEmail: Flow<String?> = _userEmail.asStateFlow()
    val userFullName: Flow<String?> = _userFullName.asStateFlow()
    val userRole: Flow<String?> = _userRole.asStateFlow()
    val userFriendCount: Flow<String?> = _userFriendCount.asStateFlow()
    val userLocation: Flow<String?> = _userLocation.asStateFlow()
    val userHometown: Flow<String?> = _userHometown.asStateFlow()
    val userBirthYear: Flow<String?> = _userBirthYear.asStateFlow()
    val userAvatar: Flow<String?> = _userAvatar.asStateFlow()

    /**
     * Save access token and set login state to true
     */
    suspend fun saveAccessToken(token: String, expiryTime: Long? = null) {
        saveAuthSession(token, expiryTime, null, null)
    }

    suspend fun saveAuthSessionWithRelativeExpiry(
        accessToken: String,
        accessTokenExpiresInSeconds: Long?,
        refreshToken: String?,
        refreshTokenExpiresInSeconds: Long?
    ) {
        val now = System.currentTimeMillis()
        val accessExpiry = accessTokenExpiresInSeconds?.let { now + it * 1000 }
        val refreshExpiry = refreshTokenExpiresInSeconds?.let { now + it * 1000 }
        saveAuthSession(accessToken, accessExpiry, refreshToken, refreshExpiry)
    }

    suspend fun saveAuthSession(
        accessToken: String,
        accessTokenExpiryTime: Long?,
        refreshTokenValue: String?,
        refreshTokenExpiryTime: Long?
    ) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putBoolean(KEY_IS_LOGGED_IN, true)
            
            if (accessTokenExpiryTime != null) {
                putString(KEY_TOKEN_EXPIRY_TIME, accessTokenExpiryTime.toString())
            } else {
                remove(KEY_TOKEN_EXPIRY_TIME)
            }
            
            if (refreshTokenValue != null) {
                putString(KEY_REFRESH_TOKEN, refreshTokenValue)
            } else {
                remove(KEY_REFRESH_TOKEN)
            }
            
            if (refreshTokenExpiryTime != null) {
                putString(KEY_REFRESH_TOKEN_EXPIRY_TIME, refreshTokenExpiryTime.toString())
            } else {
                remove(KEY_REFRESH_TOKEN_EXPIRY_TIME)
            }
        }.apply()
        
        // Update StateFlows
        _accessToken.value = accessToken
        _isLoggedIn.value = true
        _tokenExpiryTime.value = accessTokenExpiryTime?.toString()
        _refreshToken.value = refreshTokenValue
        _refreshTokenExpiryTime.value = refreshTokenExpiryTime?.toString()
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
        birthYear: Int? = null,
        avatar: String? = null
    ) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().apply {
            id?.let { putString(KEY_USER_ID, it) }
            email?.let { putString(KEY_USER_EMAIL, it) }
            fullName?.let { putString(KEY_USER_FULL_NAME, it) }
            role?.let { putString(KEY_USER_ROLE, it) }
            friendCount?.let { putString(KEY_USER_FRIEND_COUNT, it.toString()) }
            location?.let { putString(KEY_USER_LOCATION, it) }
            hometown?.let { putString(KEY_USER_HOMETOWN, it) }
            birthYear?.let { putString(KEY_USER_BIRTH_YEAR, it.toString()) }
            avatar?.let { putString(KEY_USER_AVATAR, it) }
        }.apply()
        
        // Update StateFlows
        id?.let { _userId.value = it }
        email?.let { _userEmail.value = it }
        fullName?.let { _userFullName.value = it }
        role?.let { _userRole.value = it }
        friendCount?.let { _userFriendCount.value = it.toString() }
        location?.let { _userLocation.value = it }
        hometown?.let { _userHometown.value = it }
        birthYear?.let { _userBirthYear.value = it.toString() }
        avatar?.let { _userAvatar.value = it }
    }

    /**
     * Save user avatar path (relative path only)
     */
    suspend fun saveUserAvatar(avatarPath: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_USER_AVATAR, avatarPath).apply()
        _userAvatar.value = avatarPath
    }

    /**
     * Clear access token and reset login state (logout)
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val keyManager = KeyManager(context)
        keyManager.clearAllSessionKeys()
        keyManager.setActiveUser(null)
        
        encryptedPrefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_TOKEN_EXPIRY_TIME)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_REFRESH_TOKEN_EXPIRY_TIME)
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_FULL_NAME)
            remove(KEY_USER_ROLE)
            remove(KEY_USER_FRIEND_COUNT)
            remove(KEY_USER_LOCATION)
            remove(KEY_USER_HOMETOWN)
            remove(KEY_USER_BIRTH_YEAR)
            remove(KEY_USER_AVATAR)
        }.apply()
        
        // Reset StateFlows
        _accessToken.value = null
        _isLoggedIn.value = false
        _tokenExpiryTime.value = null
        _refreshToken.value = null
        _refreshTokenExpiryTime.value = null
        _userId.value = null
        _userEmail.value = null
        _userFullName.value = null
        _userRole.value = null
        _userFriendCount.value = null
        _userLocation.value = null
        _userHometown.value = null
        _userBirthYear.value = null
        _userAvatar.value = null
    }

    /**
     * Check if token is expired
     * Reads directly from encrypted storage to ensure fresh value
     */
    suspend fun isTokenExpired(): Boolean = withContext(Dispatchers.IO) {
        val expiryTime = encryptedPrefs.getString(KEY_TOKEN_EXPIRY_TIME, null)?.toLongOrNull()
        expiryTime?.let { System.currentTimeMillis() >= it } ?: false
    }

    /**
     * Get current access token as a String (one-time read)
     * Reads directly from encrypted storage to ensure fresh value
     */
    suspend fun getAccessTokenOnce(): String? = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    suspend fun getRefreshTokenOnce(): String? = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Get user ID as a String (one-time read)
     * Reads directly from encrypted storage to ensure fresh value
     */
    suspend fun getUserIdOnce(): String? = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(KEY_USER_ID, null)
    }

    suspend fun isRefreshTokenExpired(): Boolean = withContext(Dispatchers.IO) {
        val expiryTime = encryptedPrefs.getString(KEY_REFRESH_TOKEN_EXPIRY_TIME, null)?.toLongOrNull()
        expiryTime?.let { System.currentTimeMillis() >= it } ?: false
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        // Read directly from encrypted storage for fresh values
        val currentToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiryTime = encryptedPrefs.getString(KEY_TOKEN_EXPIRY_TIME, null)?.toLongOrNull()
        val isExpired = expiryTime?.let { System.currentTimeMillis() >= it } ?: false
        
        if (!isExpired && !currentToken.isNullOrBlank()) {
            return@withContext currentToken
        }

        refreshMutex.withLock {
            // Re-check after acquiring lock
            val latestToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
            val latestExpiryTime = encryptedPrefs.getString(KEY_TOKEN_EXPIRY_TIME, null)?.toLongOrNull()
            val latestExpired = latestExpiryTime?.let { System.currentTimeMillis() >= it } ?: false
            
            if (!latestExpired && !latestToken.isNullOrBlank()) {
                return@withLock latestToken
            }
            refreshAccessTokenInternal()
        }
    }

    private suspend fun refreshAccessTokenInternal(): String? {
        // Read refresh token directly from storage
        val refreshTokenValue = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshTokenValue.isNullOrBlank()) {
            logout()
            return null
        }

        val refreshExpiryTime = encryptedPrefs.getString(KEY_REFRESH_TOKEN_EXPIRY_TIME, null)?.toLongOrNull()
        val isRefreshExpired = refreshExpiryTime?.let { System.currentTimeMillis() >= it } ?: false
        if (isRefreshExpired) {
            logout()
            return null
        }

        return try {
            val response = ApiClient.apiService.refreshTokens(
                RefreshTokenRequest(refreshTokenValue)
            )
            saveAuthSessionWithRelativeExpiry(
                accessToken = response.accessToken,
                accessTokenExpiresInSeconds = response.expiresIn,
                refreshToken = response.refreshToken,
                refreshTokenExpiresInSeconds = response.refreshExpiresIn
            )
            response.accessToken
        } catch (e: Exception) {
            logout()
            null
        }
    }

    suspend fun getUserProfileOnce(): UserProfileLocal? = withContext(Dispatchers.IO) {
        val id = encryptedPrefs.getString(KEY_USER_ID, null)
        val email = encryptedPrefs.getString(KEY_USER_EMAIL, null)
        val fullName = encryptedPrefs.getString(KEY_USER_FULL_NAME, null)
        val role = encryptedPrefs.getString(KEY_USER_ROLE, null)
        val friendCount = encryptedPrefs.getString(KEY_USER_FRIEND_COUNT, null)?.toIntOrNull()
        val location = encryptedPrefs.getString(KEY_USER_LOCATION, null)
        val hometown = encryptedPrefs.getString(KEY_USER_HOMETOWN, null)
        val birthYear = encryptedPrefs.getString(KEY_USER_BIRTH_YEAR, null)?.toIntOrNull()
        val avatar = encryptedPrefs.getString(KEY_USER_AVATAR, null)
        
        if (id != null || email != null || fullName != null || role != null) {
            UserProfileLocal(
                id = id,
                email = email,
                fullName = fullName,
                role = role,
                friendCount = friendCount,
                location = location,
                hometown = hometown,
                birthYear = birthYear,
                avatar = avatar
            )
        } else null
    }
    
    /**
     * Check login state one time
     * Reads directly from encrypted storage to ensure fresh value
     */
    suspend fun isLoggedInOnce(): Boolean = withContext(Dispatchers.IO) {
        encryptedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
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
    val birthYear: Int?,
    val avatar: String? = null  // Relative path: /static/avatars/xxx.jpg
)
