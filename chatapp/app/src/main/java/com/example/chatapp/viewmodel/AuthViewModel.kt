//package com.example.chatapp.viewmodel
//
//import android.app.Application
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.chatapp.data.encryption.KeyManager
//import com.example.chatapp.data.local.AuthManager
//import com.example.chatapp.data.repository.AuthRepository
//import com.example.chatapp.util.FCMManager
//import com.example.chatapp.data.remote.ApiClient
//import com.example.chatapp.data.remote.model.ZegoTokenRequest
//import com.example.chatapp.data.remote.model.ZegoTokenResponse
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//data class AuthState(
//    val isLoggedIn: Boolean = false,
//    val accessToken: String? = null,
//    val isLoading: Boolean = false,
//    val isInitialized: Boolean = false,
//    val userId: String? = null,
//    val userEmail: String? = null,
//    val userFullName: String? = null,
//    val userRole: String? = null,
//    val userFriendCount: Int? = null,
//    val userLocation: String? = null,
//    val userHometown: String? = null,
//    val userBirthYear: Int? = null
//)
//
//class AuthViewModel(application: Application) : AndroidViewModel(application) {
//    private val authManager = AuthManager(application)
//    private val repository = AuthRepository(application)
//    private val keyManager = KeyManager(application)
//    // Giữ nguyên logic FCM của bạn
//    private val fcmManager = FCMManager(application, ApiClient.apiService)
//
//    private val _authState = MutableStateFlow(AuthState())
//    val authState: StateFlow<AuthState> = _authState.asStateFlow()
//
//    fun getFCMManager(): FCMManager = fcmManager
//
//    init {
//        // Load saved state on initialization
//        viewModelScope.launch {
//            loadAuthState()
//        }
//    }
//
//    private suspend fun loadAuthState() {
//        val wasLoggedIn = authManager.isLoggedInOnce()
//        val token = if (wasLoggedIn) authManager.getValidAccessToken() else null
//        val isLoggedIn = token != null && authManager.isLoggedInOnce()
//        val user = if (isLoggedIn) authManager.getUserProfileOnce() else null
//
//        // Set active user in KeyManager if user is logged in
//        if (isLoggedIn && user?.id != null) {
//            keyManager.setActiveUser(user.id)
//        } else {
//            keyManager.setActiveUser(null)
//        }
//
//        _authState.value = AuthState(
//            isLoggedIn = isLoggedIn,
//            accessToken = if (isLoggedIn) token else null,
//            isInitialized = true,
//            userId = user?.id,
//            userEmail = user?.email,
//            userFullName = user?.fullName,
//            userRole = user?.role,
//            userFriendCount = user?.friendCount,
//            userLocation = user?.location,
//            userHometown = user?.hometown,
//            userBirthYear = user?.birthYear
//        )
//    }
//
//    // --- HÀM MỚI: LẤY ZEGO TOKEN TỪ BACKEND ---
//    suspend fun fetchZegoToken(roomId: String? = "", expirySeconds: Int = 3600): Result<ZegoTokenResponse> {
//        return withContext(Dispatchers.IO) {
//            val bearer = authManager.getValidAccessToken()
//                ?: return@withContext Result.failure(Exception("Not authenticated"))
//            try {
//                // Sửa lại tham số cho khớp với ApiService (Authorization Header)
//                val resp = ApiClient.apiService.getZegoToken(
//                    authorization = "Bearer $bearer",
//                    body = ZegoTokenRequest(roomId = roomId, expirySeconds = expirySeconds)
//                )
//                Result.success(resp)
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }
//    // ------------------------------------------
//
//    fun login(
//        accessToken: String,
//        expiryTime: Long? = null,
//        refreshToken: String? = null,
//        refreshExpiryTime: Long? = null
//    ) {
//        viewModelScope.launch {
//            _authState.value = _authState.value.copy(isLoading = true)
//            authManager.saveAuthSession(
//                accessToken = accessToken,
//                accessTokenExpiryTime = expiryTime,
//                refreshTokenValue = refreshToken,
//                refreshTokenExpiryTime = refreshExpiryTime
//            )
//            val profile = authManager.getUserProfileOnce()
//
//            // Cập nhật KeyManager
//            profile?.id?.let { keyManager.setActiveUser(it) }
//
//            _authState.value = AuthState(
//                isLoggedIn = true,
//                accessToken = accessToken,
//                isLoading = false,
//                isInitialized = true,
//                userId = profile?.id,
//                userEmail = profile?.email,
//                userFullName = profile?.fullName,
//                userRole = profile?.role,
//                userFriendCount = profile?.friendCount,
//                userLocation = profile?.location,
//                userHometown = profile?.hometown,
//                userBirthYear = profile?.birthYear
//            )
//        }
//    }
//
//    fun logout() {
//        viewModelScope.launch {
//            authManager.logout()
//            keyManager.setActiveUser(null) // Xóa session mã hóa
//            _authState.value = AuthState(isLoggedIn = false, accessToken = null, isInitialized = true)
//        }
//    }
//
//    fun refreshToken() {
//        viewModelScope.launch {
//            _authState.value = _authState.value.copy(isLoading = true)
//            val result = repository.refreshAccessToken()
//            if (result.isSuccess) {
//                val newToken = result.getOrNull()
//                val profile = authManager.getUserProfileOnce()
//                _authState.value = _authState.value.copy(
//                    isLoggedIn = newToken != null,
//                    accessToken = newToken,
//                    isLoading = false,
//                    userId = profile?.id,
//                    userEmail = profile?.email,
//                    userFullName = profile?.fullName,
//                    userRole = profile?.role,
//                    userFriendCount = profile?.friendCount,
//                    userLocation = profile?.location,
//                    userHometown = profile?.hometown,
//                    userBirthYear = profile?.birthYear
//                )
//            } else {
//                logout()
//            }
//        }
//    }
//
//    // Network-based auth
//    suspend fun loginWithNetwork(username: String, password: String): Result<Unit> {
//        _authState.value = _authState.value.copy(isLoading = true)
//        return try {
//            val result = repository.login(username, password)
//            if (result.isSuccess) {
//                val response = result.getOrNull()
//                val token = response?.accessToken ?: authManager.getValidAccessToken()
//                val profile = authManager.getUserProfileOnce()
//                _authState.value = AuthState(
//                    isLoggedIn = true,
//                    accessToken = token,
//                    isLoading = false,
//                    isInitialized = true,
//                    userId = profile?.id ?: _authState.value.userId,
//                    userEmail = profile?.email ?: _authState.value.userEmail,
//                    userFullName = profile?.fullName ?: _authState.value.userFullName,
//                    userRole = profile?.role ?: _authState.value.userRole,
//                    userFriendCount = profile?.friendCount ?: _authState.value.userFriendCount,
//                    userLocation = profile?.location ?: _authState.value.userLocation,
//                    userHometown = profile?.hometown ?: _authState.value.userHometown,
//                    userBirthYear = profile?.birthYear ?: _authState.value.userBirthYear
//                )
//                Result.success(Unit)
//            } else {
//                _authState.value = _authState.value.copy(isLoading = false)
//                Result.failure(result.exceptionOrNull() ?: Exception("Login failed"))
//            }
//        } catch (e: Exception) {
//            _authState.value = _authState.value.copy(isLoading = false)
//            Result.failure(e)
//        }
//    }
//
//    suspend fun registerAccount(email: String, password: String, fullName: String): Result<Unit> {
//        _authState.value = _authState.value.copy(isLoading = true)
//        return try {
//            val result = repository.register(email, password, fullName)
//            _authState.value = _authState.value.copy(isLoading = false)
//            result
//        } catch (e: Exception) {
//            _authState.value = _authState.value.copy(isLoading = false)
//            Result.failure(e)
//        }
//    }
//
//    suspend fun updateProfile(
//        fullName: String? = null,
//        location: String? = null,
//        hometown: String? = null,
//        birthYear: Int? = null
//    ): Result<Unit> {
//        _authState.value = _authState.value.copy(isLoading = true)
//        return try {
//            val result = repository.updateProfile(fullName, location, hometown, birthYear)
//            if (result.isSuccess) {
//                val user = result.getOrNull()
//                _authState.value = _authState.value.copy(
//                    isLoading = false,
//                    userFullName = user?.fullName ?: _authState.value.userFullName,
//                    userLocation = user?.location ?: _authState.value.userLocation,
//                    userHometown = user?.hometown ?: _authState.value.userHometown,
//                    userBirthYear = user?.birthYear ?: _authState.value.userBirthYear,
//                    userFriendCount = user?.friendCount ?: _authState.value.userFriendCount
//                )
//                Result.success(Unit)
//            } else {
//                _authState.value = _authState.value.copy(isLoading = false)
//                Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
//            }
//        } catch (e: Exception) {
//            _authState.value = _authState.value.copy(isLoading = false)
//            Result.failure(e)
//        }
//    }
//
//    suspend fun changePassword(
//        oldPassword: String,
//        newPassword: String
//    ): Result<Unit> {
//        return repository.changePassword(oldPassword, newPassword)
//    }
//}

package com.example.chatapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.encryption.KeyManager
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.util.FCMManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.ZegoTokenRequest
import com.example.chatapp.data.remote.model.ZegoTokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuthState(
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val userId: String? = null,
    val userEmail: String? = null,
    val userFullName: String? = null,
    val userRole: String? = null,
    val userFriendCount: Int? = null,
    val userLocation: String? = null,
    val userHometown: String? = null,
    val userBirthYear: Int? = null,
    val userAvatar: String? = null  // Relative path: /static/avatars/xxx.jpg
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = AuthManager(application)
    private val repository = AuthRepository(application)
    private val keyManager = KeyManager(application)
    // Giữ nguyên logic FCM của bạn
    private val fcmManager = FCMManager(application, ApiClient.apiService)

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun getFCMManager(): FCMManager = fcmManager

    init {
        // Load saved state on initialization
        viewModelScope.launch {
            loadAuthState()
        }
    }

    private suspend fun loadAuthState() {
        val wasLoggedIn = authManager.isLoggedInOnce()
        val token = if (wasLoggedIn) authManager.getValidAccessToken() else null
        val isLoggedIn = token != null && authManager.isLoggedInOnce()
        val user = if (isLoggedIn) authManager.getUserProfileOnce() else null

        // Set active user in KeyManager if user is logged in
        if (isLoggedIn && user?.id != null) {
            keyManager.setActiveUser(user.id)
        } else {
            keyManager.setActiveUser(null)
        }

        _authState.value = AuthState(
            isLoggedIn = isLoggedIn,
            accessToken = if (isLoggedIn) token else null,
            isInitialized = true,
            userId = user?.id,
            userEmail = user?.email,
            userFullName = user?.fullName,
            userRole = user?.role,
            userFriendCount = user?.friendCount,
            userLocation = user?.location,
            userHometown = user?.hometown,
            userBirthYear = user?.birthYear,
            userAvatar = user?.avatar
        )
    }

    // --- HÀM MỚI: LẤY ZEGO TOKEN TỪ BACKEND (ĐÃ SỬA LỖI GỌI API) ---
    suspend fun fetchZegoToken(roomId: String? = "", expirySeconds: Int = 3600): Result<ZegoTokenResponse> {
        return withContext(Dispatchers.IO) {
            val bearer = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            try {
                // Sửa tham số truyền vào: 'authorization' thay vì 'token' để khớp với ApiService
                val resp = ApiClient.apiService.getZegoToken(
                    authorization = "Bearer $bearer",
                    body = ZegoTokenRequest(roomId = roomId, expirySeconds = expirySeconds)
                )

                // Validate cơ bản
                if (resp.token.isNotBlank()) {
                    Result.success(resp)
                } else {
                    Result.failure(Exception("Server returned empty token"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    // -------------------------------------------------------------

    fun login(
        accessToken: String,
        expiryTime: Long? = null,
        refreshToken: String? = null,
        refreshExpiryTime: Long? = null
    ) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            authManager.saveAuthSession(
                accessToken = accessToken,
                accessTokenExpiryTime = expiryTime,
                refreshTokenValue = refreshToken,
                refreshTokenExpiryTime = refreshExpiryTime
            )
            val profile = authManager.getUserProfileOnce()

            // Cập nhật KeyManager
            profile?.id?.let { keyManager.setActiveUser(it) }

            _authState.value = AuthState(
                isLoggedIn = true,
                accessToken = accessToken,
                isLoading = false,
                isInitialized = true,
                userId = profile?.id,
                userEmail = profile?.email,
                userFullName = profile?.fullName,
                userRole = profile?.role,
                userFriendCount = profile?.friendCount,
                userLocation = profile?.location,
                userHometown = profile?.hometown,
                userBirthYear = profile?.birthYear,
                userAvatar = profile?.avatar
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            keyManager.setActiveUser(null) // Xóa session mã hóa
            _authState.value = AuthState(isLoggedIn = false, accessToken = null, isInitialized = true)
        }
    }

    fun refreshToken() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            val result = repository.refreshAccessToken()
            if (result.isSuccess) {
                val newToken = result.getOrNull()
                val profile = authManager.getUserProfileOnce()
                _authState.value = _authState.value.copy(
                    isLoggedIn = newToken != null,
                    accessToken = newToken,
                    isLoading = false,
                    userId = profile?.id,
                    userEmail = profile?.email,
                    userFullName = profile?.fullName,
                    userRole = profile?.role,
                    userFriendCount = profile?.friendCount,
                    userLocation = profile?.location,
                    userHometown = profile?.hometown,
                    userBirthYear = profile?.birthYear,
                    userAvatar = profile?.avatar
                )
            } else {
                logout()
            }
        }
    }

    // Network-based auth
    suspend fun loginWithNetwork(username: String, password: String): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.login(username, password)
            if (result.isSuccess) {
                val response = result.getOrNull()
                val token = response?.accessToken ?: authManager.getValidAccessToken()
                val profile = authManager.getUserProfileOnce()
                _authState.value = AuthState(
                    isLoggedIn = true,
                    accessToken = token,
                    isLoading = false,
                    isInitialized = true,
                    userId = profile?.id ?: _authState.value.userId,
                    userEmail = profile?.email ?: _authState.value.userEmail,
                    userFullName = profile?.fullName ?: _authState.value.userFullName,
                    userRole = profile?.role ?: _authState.value.userRole,
                    userFriendCount = profile?.friendCount ?: _authState.value.userFriendCount,
                    userLocation = profile?.location ?: _authState.value.userLocation,
                    userHometown = profile?.hometown ?: _authState.value.userHometown,
                    userBirthYear = profile?.birthYear ?: _authState.value.userBirthYear,
                    userAvatar = profile?.avatar ?: _authState.value.userAvatar
                )
                Result.success(Unit)
            } else {
                _authState.value = _authState.value.copy(isLoading = false)
                Result.failure(result.exceptionOrNull() ?: Exception("Login failed"))
            }
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun registerAccount(email: String, password: String, fullName: String): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.register(email, password, fullName)
            _authState.value = _authState.value.copy(isLoading = false)
            result
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        fullName: String? = null,
        location: String? = null,
        hometown: String? = null,
        birthYear: Int? = null
    ): Result<Unit> {
        _authState.value = _authState.value.copy(isLoading = true)
        return try {
            val result = repository.updateProfile(fullName, location, hometown, birthYear)
            if (result.isSuccess) {
                val user = result.getOrNull()
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    userFullName = user?.fullName ?: _authState.value.userFullName,
                    userLocation = user?.location ?: _authState.value.userLocation,
                    userHometown = user?.hometown ?: _authState.value.userHometown,
                    userBirthYear = user?.birthYear ?: _authState.value.userBirthYear,
                    userFriendCount = user?.friendCount ?: _authState.value.userFriendCount,
                    userAvatar = user?.avatar ?: _authState.value.userAvatar
                )
                Result.success(Unit)
            } else {
                _authState.value = _authState.value.copy(isLoading = false)
                Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
            }
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false)
            Result.failure(e)
        }
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return repository.changePassword(oldPassword, newPassword)
    }

    /**
     * Upload avatar image for current user.
     * @param avatarPath Relative path returned from upload API
     */
    fun updateAvatarInState(avatarPath: String) {
        _authState.value = _authState.value.copy(userAvatar = avatarPath)
        // Persist to AuthManager
        viewModelScope.launch {
            authManager.saveUserAvatar(avatarPath)
        }
    }
    
    /**
     * Update user's full name via API and update local state
     * @param newName New full name to set
     */
    suspend fun updateFullName(newName: String): Result<Unit> {
        return try {
            val token = authManager.getAccessTokenOnce()
                ?: return Result.failure(Exception("Not authenticated"))
            
            // Call API to update profile with new name
            val response = ApiClient.apiService.updateProfile(
                token = "Bearer $token",
                body = com.example.chatapp.data.remote.model.ProfileUpdateRequest(fullName = newName)
            )
            
            // Update local state
            _authState.value = _authState.value.copy(userFullName = response.fullName ?: newName)
            
            // Persist to AuthManager - use existing values for id and role
            authManager.saveUserProfile(
                id = _authState.value.userId,
                email = _authState.value.userEmail,
                fullName = response.fullName ?: newName,
                role = _authState.value.userRole,
                avatar = _authState.value.userAvatar
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Refresh user profile from API
     * Call this after actions that change profile data (like unfriend)
     */
    fun refreshProfile() {
        viewModelScope.launch {
            try {
                val token = authManager.getAccessTokenOnce() ?: return@launch
                val profile = ApiClient.apiService.getProfile("Bearer $token")
                
                // Update local state with fresh data
                _authState.value = _authState.value.copy(
                    userFriendCount = profile.friendCount,
                    userFullName = profile.fullName,
                    userLocation = profile.location,
                    userHometown = profile.hometown,
                    userBirthYear = profile.birthYear,
                    userAvatar = profile.avatar
                )
                
                // Persist to AuthManager
                authManager.saveUserProfile(
                    id = _authState.value.userId,
                    email = _authState.value.userEmail,
                    fullName = profile.fullName,
                    role = _authState.value.userRole,
                    avatar = profile.avatar,
                    location = profile.location,
                    hometown = profile.hometown,
                    birthYear = profile.birthYear,
                    friendCount = profile.friendCount
                )
            } catch (e: Exception) {
                // Silently fail - profile refresh is not critical
            }
        }
    }
}