package com.example.chatapp.data.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey

/**
 * KeyManager handles:
 * - Secure storage of RSA private key in Android Keystore
 * - Caching of RSA public key
 * - In-memory caching of AES session keys per conversation
 * - Encrypted storage of session keys (for persistence across app restarts)
 */
class KeyManager(private val context: Context) {
    
    private val TAG = "KeyManager"
    
    // Android Keystore for hardware-backed key storage
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    private val RSA_KEY_ALIAS_PREFIX = "e2ee_rsa_key_"
    private val ACTIVE_USER_PREF_KEY = "active_user_id"
    
    // EncryptedSharedPreferences for storing sensitive data
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefsSafely()
    }

    /**
     * Safely create EncryptedSharedPreferences. If the stored XML cannot be decrypted
     * (common after uninstall/reinstall with restored data), delete the file and recreate.
     */
    private fun createEncryptedPrefsSafely(): SharedPreferences {
        val fileName = "e2ee_encrypted_prefs"
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            createEncryptedPrefs(masterKey, fileName)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Failed to open encrypted prefs (security). Resetting file.", e)
            deleteEncryptedPrefsFile(fileName)
            createEncryptedPrefs(masterKey, fileName)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open encrypted prefs (IO). Resetting file.", e)
            deleteEncryptedPrefsFile(fileName)
            createEncryptedPrefs(masterKey, fileName)
        }
    }

    private fun createEncryptedPrefs(masterKey: MasterKey, fileName: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun deleteEncryptedPrefsFile(fileName: String) {
        try {
            val prefsFile = File(context.filesDir.parent + "/shared_prefs/$fileName.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete corrupted encrypted prefs file", e)
        }
    }
    
    // In-memory cache of AES session keys (conversationId -> SecretKey) per user
    private val sessionKeyCache = mutableMapOf<String, SecretKey>()
    
    // Mutex for thread-safe access to session key cache
    private val sessionKeyCacheMutex = Mutex()

    private fun rsaAlias(userId: String) = "$RSA_KEY_ALIAS_PREFIX$userId"
    private fun publicKeyPref(userId: String) = "rsa_public_key_$userId"
    private fun sessionKeyPref(userId: String, conversationId: String) = "session_key_${userId}_$conversationId"
    private fun encryptedSessionKeyPref(userId: String, conversationId: String) = "encrypted_session_key_${userId}_$conversationId"
    private fun cacheKey(userId: String, conversationId: String) = "${userId}_$conversationId"

    fun setActiveUser(userId: String?) {
        encryptedPrefs.edit().apply {
            if (userId == null) {
                remove(ACTIVE_USER_PREF_KEY)
            } else {
                putString(ACTIVE_USER_PREF_KEY, userId)
            }
        }.apply()
    }

    fun getActiveUserId(): String? = encryptedPrefs.getString(ACTIVE_USER_PREF_KEY, null)

    private fun requireActiveUserId(): String {
        return getActiveUserId()
            ?: throw IllegalStateException("Active user not set for KeyManager")
    }
    
    // ========== RSA Keypair Management ==========
    
    /**
     * Generate and store a new RSA keypair in Android Keystore
     * Returns the public key (to be uploaded to server)
     */
    fun generateAndStoreRSAKeyPair(): PublicKey {
        val userId = requireActiveUserId()
        val alias = rsaAlias(userId)

        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
        
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1) // Support both for OAEP with MGF1
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(false) // Can be true for extra security
            .build()
        
        keyPairGenerator.initialize(keyGenParameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        // Cache public key for quick access
        val publicKeyBase64 = CryptoManager.encodePublicKey(keyPair.public)
        encryptedPrefs.edit().putString(publicKeyPref(userId), publicKeyBase64).apply()
        
        Log.d(TAG, "Generated new RSA keypair for user $userId")
        return keyPair.public
    }
    
    /**
     * Check if RSA keypair exists
     */
    fun hasRSAKeyPair(): Boolean {
        val userId = getActiveUserId() ?: return false
        return keyStore.containsAlias(rsaAlias(userId))
    }
    
    /**
     * Verify that the current private key can decrypt data encrypted with the cached public key
     * This helps diagnose key mismatch issues
     */
    fun verifyRSAKeyPairMatch(): Boolean {
        return try {
            val userId = getActiveUserId() ?: run {
                Log.e(TAG, "No active user set, cannot verify key pair")
                return false
            }
            
            val publicKey = getRSAPublicKey()
            val privateKey = getRSAPrivateKey()
            
            if (publicKey == null || privateKey == null) {
                Log.e(TAG, "Public or private key missing, cannot verify")
                return false
            }
            
            // Test encryption/decryption
            val testData = "test_key_verification_${System.currentTimeMillis()}".toByteArray()
            val encrypted = CryptoManager.rsaEncrypt(testData, publicKey)
            val decrypted = CryptoManager.rsaDecrypt(encrypted, privateKey)
            
            val match = testData.contentEquals(decrypted)
            if (match) {
                Log.d(TAG, "✅ RSA key pair verification PASSED - keys match!")
            } else {
                Log.e(TAG, "❌ RSA key pair verification FAILED - keys DO NOT match!")
            }
            
            match
        } catch (e: Exception) {
            Log.e(TAG, "❌ RSA key pair verification FAILED with exception", e)
            false
        }
    }
    
    /**
     * Get the RSA private key from Android Keystore
     */
    fun getRSAPrivateKey(): PrivateKey? {
        return try {
            val userId = requireActiveUserId()
            val alias = rsaAlias(userId)
            Log.d(TAG, "Looking for RSA private key with alias: $alias")
            
            if (!keyStore.containsAlias(alias)) {
                Log.e(TAG, "❌ Private key NOT FOUND in keystore for alias: $alias")
                return null
            }
            
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            if (entry == null) {
                Log.e(TAG, "❌ Keystore entry exists but is not a PrivateKeyEntry for alias: $alias")
                return null
            }
            
            Log.d(TAG, "✅ Private key found in keystore for alias: $alias")
            entry.privateKey
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting RSA private key", e)
            null
        }
    }
    
    /**
     * Get the cached RSA public key
     */
    fun getRSAPublicKey(): PublicKey? {
        return try {
            val publicKeyBase64 = encryptedPrefs.getString(publicKeyPref(requireActiveUserId()), null) ?: return null
            CryptoManager.decodePublicKey(publicKeyBase64)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RSA public key", e)
            null
        }
    }
    
    /**
     * Get RSA public key as Base64 string (for uploading to server)
     */
    fun getRSAPublicKeyBase64(): String? {
        val userId = getActiveUserId() ?: return null
        val cachedKey = encryptedPrefs.getString(publicKeyPref(userId), null)
        Log.d(TAG, "Getting public key for user $userId: ${if (cachedKey != null) "Found (length: ${cachedKey.length})" else "NOT FOUND"}")
        return cachedKey
    }
    
    // ========== Session Key Management ==========
    
    /**
     * Store AES session key for a conversation (in-memory + persistent storage)
     * Thread-safe with mutex lock
     * @param conversationId The conversation ID
     * @param sessionKey The AES session key
     */
    suspend fun storeSessionKey(conversationId: String, sessionKey: SecretKey) {
        val userId = requireActiveUserId()
        val cacheKey = cacheKey(userId, conversationId)
        sessionKeyCacheMutex.withLock {
            // Store in memory cache
            sessionKeyCache[cacheKey] = sessionKey
            
            // Persist to encrypted storage
            try {
                val keyBase64 = CryptoManager.encodeSecretKey(sessionKey)
                encryptedPrefs.edit().putString(sessionKeyPref(userId, conversationId), keyBase64).apply()
                Log.d(TAG, "Stored session key for user $userId conversation: $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error storing session key", e)
            }
        }
    }
    
    /**
     * Get AES session key for a conversation
     * First checks memory cache, then loads from persistent storage
     * Thread-safe with mutex lock
     */
    suspend fun getSessionKey(conversationId: String): SecretKey? {
        val userId = requireActiveUserId()
        val cacheKey = cacheKey(userId, conversationId)
        return sessionKeyCacheMutex.withLock {
            // Check memory cache first
            sessionKeyCache[cacheKey]?.let { return@withLock it }
            
            // Load from persistent storage
            try {
                val keyBase64 = encryptedPrefs.getString(sessionKeyPref(userId, conversationId), null)
                    ?: return@withLock null
                
                val sessionKey = CryptoManager.decodeSecretKey(keyBase64)
                
                // Cache in memory
                sessionKeyCache[cacheKey] = sessionKey
                
                Log.d(TAG, "Loaded session key for user $userId conversation: $conversationId")
                sessionKey
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session key", e)
                null
            }
        }
    }
    
    /**
     * Store encrypted session key (received from server)
     * This is the session key encrypted with our RSA public key
     */
    fun storeEncryptedSessionKey(conversationId: String, encryptedKeyBase64: String) {
        val userId = requireActiveUserId()
        try {
            encryptedPrefs.edit()
                .putString(encryptedSessionKeyPref(userId, conversationId), encryptedKeyBase64)
                .apply()
            Log.d(TAG, "Stored encrypted session key for user $userId conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing encrypted session key", e)
        }
    }
    
    /**
     * Get and decrypt session key for a conversation
     * Retrieves the encrypted session key and decrypts it with our RSA private key
     * Thread-safe with mutex lock
     */
    suspend fun getAndDecryptSessionKey(conversationId: String): SecretKey? {
        // Check if we already have the decrypted key
        getSessionKey(conversationId)?.let { return it }
        
        val userId = requireActiveUserId()
        Log.d(TAG, "Attempting to decrypt session key for user $userId conversation: $conversationId")
        
        // Get encrypted session key
        val encryptedKeyBase64 = encryptedPrefs.getString(encryptedSessionKeyPref(userId, conversationId), null)
        if (encryptedKeyBase64 == null) {
            Log.e(TAG, "No encrypted session key found in storage for conversation: $conversationId")
            return null
        }
        Log.d(TAG, "Found encrypted session key in storage (length: ${encryptedKeyBase64.length})")
        
        // Decrypt with our RSA private key
        val privateKey = getRSAPrivateKey()
        if (privateKey == null) {
            Log.e(TAG, "RSA private key not found! Cannot decrypt session key for conversation: $conversationId")
            return null
        }
        Log.d(TAG, "RSA private key found, algorithm: ${privateKey.algorithm}, format: ${privateKey.format}")
        
        return try {
            Log.d(TAG, "Attempting RSA decryption...")
            val sessionKeyBytes = CryptoManager.rsaDecrypt(encryptedKeyBase64, privateKey)
            Log.d(TAG, "RSA decryption successful, session key bytes length: ${sessionKeyBytes.size}")
            
            val sessionKey = CryptoManager.decodeSecretKey(
                Base64.encodeToString(sessionKeyBytes, Base64.NO_WRAP)
            )
            Log.d(TAG, "Session key decoded successfully")
            
            // Cache the decrypted key
            storeSessionKey(conversationId, sessionKey)
            
            Log.d(TAG, "✅ Successfully decrypted and cached session key for user $userId conversation: $conversationId")
            sessionKey
        } catch (e: javax.crypto.BadPaddingException) {
            Log.e(TAG, "❌ RSA decryption failed: BadPaddingException - Wrong private key or corrupted data", e)
            Log.e(TAG, "This usually means the encrypted key was encrypted with a DIFFERENT public key than the current private key")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error decrypting session key: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }
    
    /**
     * Clear session key for a conversation
     * Thread-safe with mutex lock
     */
    suspend fun clearSessionKey(conversationId: String) {
        val userId = getActiveUserId() ?: return
        val cacheKey = cacheKey(userId, conversationId)
        sessionKeyCacheMutex.withLock {
            sessionKeyCache.remove(cacheKey)
            encryptedPrefs.edit()
                .remove(sessionKeyPref(userId, conversationId))
                .remove(encryptedSessionKeyPref(userId, conversationId))
                .apply()
        }
    }
    
    /**
     * Clear all session keys for the active user
     * Thread-safe with mutex lock
     */
    suspend fun clearAllSessionKeys() {
        val userId = getActiveUserId() ?: return
        sessionKeyCacheMutex.withLock {
            sessionKeyCache.keys.removeAll { it.startsWith("${userId}_") }
            val editor = encryptedPrefs.edit()
            val sessionPrefix = "session_key_${userId}_"
            val encryptedPrefix = "encrypted_session_key_${userId}_"
            encryptedPrefs.all.keys
                .filter { it.startsWith(sessionPrefix) || it.startsWith(encryptedPrefix) }
                .forEach { editor.remove(it) }
            editor.apply()
        }
    }
    
    /**
     * Check if we have a session key for a conversation
     * Thread-safe with mutex lock
     */
    suspend fun hasSessionKey(conversationId: String): Boolean {
        val userId = getActiveUserId() ?: return false
        val cacheKey = cacheKey(userId, conversationId)
        return sessionKeyCacheMutex.withLock {
            sessionKeyCache.containsKey(cacheKey) ||
                    encryptedPrefs.contains(sessionKeyPref(userId, conversationId))
        }
    }
}

