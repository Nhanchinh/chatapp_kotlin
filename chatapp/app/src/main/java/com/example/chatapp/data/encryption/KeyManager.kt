package com.example.chatapp.data.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    
    // Alias for RSA keypair in Android Keystore
    private val RSA_KEY_ALIAS = "e2ee_rsa_key"
    
    // EncryptedSharedPreferences for storing sensitive data
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "e2ee_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // In-memory cache of AES session keys (conversationId -> SecretKey)
    private val sessionKeyCache = mutableMapOf<String, SecretKey>()
    
    // ========== RSA Keypair Management ==========
    
    /**
     * Generate and store a new RSA keypair in Android Keystore
     * Returns the public key (to be uploaded to server)
     */
    fun generateAndStoreRSAKeyPair(): PublicKey {
        // Delete existing key if any
        if (keyStore.containsAlias(RSA_KEY_ALIAS)) {
            keyStore.deleteEntry(RSA_KEY_ALIAS)
        }
        
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            RSA_KEY_ALIAS,
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
        encryptedPrefs.edit().putString("rsa_public_key", publicKeyBase64).apply()
        
        Log.d(TAG, "Generated new RSA keypair")
        return keyPair.public
    }
    
    /**
     * Check if RSA keypair exists
     */
    fun hasRSAKeyPair(): Boolean {
        return keyStore.containsAlias(RSA_KEY_ALIAS)
    }
    
    /**
     * Get the RSA private key from Android Keystore
     */
    fun getRSAPrivateKey(): PrivateKey? {
        return try {
            val entry = keyStore.getEntry(RSA_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RSA private key", e)
            null
        }
    }
    
    /**
     * Get the cached RSA public key
     */
    fun getRSAPublicKey(): PublicKey? {
        return try {
            val publicKeyBase64 = encryptedPrefs.getString("rsa_public_key", null) ?: return null
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
        return encryptedPrefs.getString("rsa_public_key", null)
    }
    
    // ========== Session Key Management ==========
    
    /**
     * Store AES session key for a conversation (in-memory + persistent storage)
     * @param conversationId The conversation ID
     * @param sessionKey The AES session key
     */
    fun storeSessionKey(conversationId: String, sessionKey: SecretKey) {
        // Store in memory cache
        sessionKeyCache[conversationId] = sessionKey
        
        // Persist to encrypted storage
        try {
            val keyBase64 = CryptoManager.encodeSecretKey(sessionKey)
            encryptedPrefs.edit().putString("session_key_$conversationId", keyBase64).apply()
            Log.d(TAG, "Stored session key for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing session key", e)
        }
    }
    
    /**
     * Get AES session key for a conversation
     * First checks memory cache, then loads from persistent storage
     */
    fun getSessionKey(conversationId: String): SecretKey? {
        // Check memory cache first
        sessionKeyCache[conversationId]?.let { return it }
        
        // Load from persistent storage
        return try {
            val keyBase64 = encryptedPrefs.getString("session_key_$conversationId", null)
                ?: return null
            
            val sessionKey = CryptoManager.decodeSecretKey(keyBase64)
            
            // Cache in memory
            sessionKeyCache[conversationId] = sessionKey
            
            Log.d(TAG, "Loaded session key for conversation: $conversationId")
            sessionKey
        } catch (e: Exception) {
            Log.e(TAG, "Error loading session key", e)
            null
        }
    }
    
    /**
     * Store encrypted session key (received from server)
     * This is the session key encrypted with our RSA public key
     */
    fun storeEncryptedSessionKey(conversationId: String, encryptedKeyBase64: String) {
        try {
            encryptedPrefs.edit()
                .putString("encrypted_session_key_$conversationId", encryptedKeyBase64)
                .apply()
            Log.d(TAG, "Stored encrypted session key for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing encrypted session key", e)
        }
    }
    
    /**
     * Get and decrypt session key for a conversation
     * Retrieves the encrypted session key and decrypts it with our RSA private key
     */
    fun getAndDecryptSessionKey(conversationId: String): SecretKey? {
        // Check if we already have the decrypted key
        getSessionKey(conversationId)?.let { return it }
        
        // Get encrypted session key
        val encryptedKeyBase64 = encryptedPrefs.getString("encrypted_session_key_$conversationId", null)
            ?: return null
        
        // Decrypt with our RSA private key
        val privateKey = getRSAPrivateKey() ?: return null
        
        return try {
            val sessionKeyBytes = CryptoManager.rsaDecrypt(encryptedKeyBase64, privateKey)
            val sessionKey = CryptoManager.decodeSecretKey(
                Base64.encodeToString(sessionKeyBytes, Base64.NO_WRAP)
            )
            
            // Cache the decrypted key
            storeSessionKey(conversationId, sessionKey)
            
            Log.d(TAG, "Decrypted session key for conversation: $conversationId")
            sessionKey
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting session key", e)
            null
        }
    }
    
    /**
     * Clear session key for a conversation
     */
    fun clearSessionKey(conversationId: String) {
        sessionKeyCache.remove(conversationId)
        encryptedPrefs.edit()
            .remove("session_key_$conversationId")
            .remove("encrypted_session_key_$conversationId")
            .apply()
    }
    
    /**
     * Clear all session keys
     */
    fun clearAllSessionKeys() {
        sessionKeyCache.clear()
        encryptedPrefs.edit().clear().apply()
    }
    
    /**
     * Check if we have a session key for a conversation
     */
    fun hasSessionKey(conversationId: String): Boolean {
        return sessionKeyCache.containsKey(conversationId) ||
                encryptedPrefs.contains("session_key_$conversationId")
    }
}

