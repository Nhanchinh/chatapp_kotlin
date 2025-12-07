package com.example.chatapp.data.encryption

import android.content.Context
import android.util.Log
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.EncryptedSessionKeyDto
import com.example.chatapp.data.remote.model.StoreKeysRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * E2EEManager orchestrates the end-to-end encryption flow:
 * 1. Setting up session keys for conversations
 * 2. Encrypting messages before sending
 * 3. Decrypting received messages
 */
class E2EEManager(context: Context) {
    
    private val TAG = "E2EEManager"
    private val keyManager = KeyManager(context)
    private val api = ApiClient.apiService
    
    // Mutex to prevent duplicate setupConversationEncryption calls for same conversation
    private val setupMutex = Mutex()
    private val setupInProgress = mutableSetOf<String>()
    
    /**
     * Prepare encrypted keys for a new conversation (before conversation is created).
     * This method generates AES session key and encrypts it for all participants.
     * 
     * @param participantIds List of participant user IDs (including self)
     * @param token Authentication token
     * @return Pair of (encrypted keys list, session key) or null if failed
     */
    suspend fun prepareEncryptedKeys(
        participantIds: List<String>,
        token: String
    ): Pair<List<EncryptedSessionKeyDto>, SecretKey>? = withContext(Dispatchers.IO) {
        try {
            // Generate new AES session key
            val sessionKey = CryptoManager.generateAESKey()
            
            // Fetch public keys for all participants
            val userIdsParam = participantIds.joinToString(",")
            val publicKeysResponse = api.getPublicKeys("Bearer $token", userIdsParam)
            
            if (publicKeysResponse.items.isEmpty()) {
                Log.e(TAG, "No public keys found for participants")
                return@withContext null
            }
            
            // Encrypt session key for each participant
            val encryptedKeys = mutableListOf<EncryptedSessionKeyDto>()
            
            for (keyDto in publicKeysResponse.items) {
                val publicKeyBase64 = keyDto.publicKey
                if (publicKeyBase64 == null) {
                    Log.w(TAG, "No public key for user ${keyDto.userId}")
                    continue
                }
                
                try {
                    val publicKey = CryptoManager.decodePublicKey(publicKeyBase64)
                    val sessionKeyBytes = sessionKey.encoded
                    val encryptedKey = CryptoManager.rsaEncrypt(sessionKeyBytes, publicKey)
                    
                    encryptedKeys.add(
                        EncryptedSessionKeyDto(
                            userId = keyDto.userId,
                            encryptedSessionKey = encryptedKey
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error encrypting key for user ${keyDto.userId}", e)
                }
            }
            
            if (encryptedKeys.isEmpty()) {
                Log.e(TAG, "Failed to encrypt keys for any participant")
                return@withContext null
            }
            
            return@withContext Pair(encryptedKeys, sessionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing encrypted keys", e)
            null
        }
    }
    
    /**
     * Setup encryption for a conversation
     * - First tries to fetch existing key from server (peer may have already created it)
     * - If not found, generates a new AES session key
     * - Fetches public keys of all participants
     * - Encrypts session key for each participant
     * - Uploads encrypted keys to server
     * 
     * @param conversationId The conversation ID
     * @param participantIds List of participant user IDs (including self)
     * @param token Authentication token
     * @return true if setup successful, false otherwise
     */
    suspend fun setupConversationEncryption(
        conversationId: String,
        participantIds: List<String>,
        token: String
    ): Boolean = withContext(Dispatchers.IO) {
        // **CRITICAL**: Prevent duplicate setup calls for same conversation
        val shouldSetup = setupMutex.withLock {
            if (setupInProgress.contains(conversationId)) {
                Log.d(TAG, "Setup already in progress for conversation $conversationId, skipping...")
                return@withLock false
            }
            setupInProgress.add(conversationId)
            true
        }
        
        if (!shouldSetup) {
            // Another coroutine is already setting up, wait for it
            return@withContext false
        }
        
        try {
            // **CRITICAL**: Check if key already exists on server (peer may have created it)
            val existingKey = try {
                getSessionKey(conversationId, token, retryCount = 0) // Don't retry here
            } catch (e: Exception) {
                null
            }
            
            if (existingKey != null) {
                Log.d(TAG, "Key already exists for conversation $conversationId (fetched from server)")
                return@withContext true
            }
            
            Log.d(TAG, "No existing key found, generating new key for conversation $conversationId")
            
            // Use prepareEncryptedKeys to generate and encrypt keys
            val prepared = prepareEncryptedKeys(participantIds, token)
            if (prepared == null) {
                return@withContext false
            }
            
            val (encryptedKeys, sessionKey) = prepared
            
            // Upload encrypted keys to server
            val storeRequest = StoreKeysRequest(
                conversationId = conversationId,
                keys = encryptedKeys
            )
            api.storeConversationKeys("Bearer $token", storeRequest)
            
            // Store our own session key locally
            keyManager.storeSessionKey(conversationId, sessionKey)
            
            Log.d(TAG, "Successfully setup encryption for conversation $conversationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up conversation encryption", e)
            false
        } finally {
            // Remove from in-progress set
            setupMutex.withLock {
                setupInProgress.remove(conversationId)
            }
        }
    }
    
    /**
     * Get or fetch the session key for a conversation with retry logic
     * 
     * @param conversationId The conversation ID
     * @param token Authentication token
     * @param retryCount Current retry attempt (for internal use)
     * @return SecretKey if found, null otherwise
     */
    suspend fun getSessionKey(
        conversationId: String, 
        token: String,
        retryCount: Int = 0
    ): SecretKey? = withContext(Dispatchers.IO) {
        try {
            // Check if we already have the key locally
            keyManager.getSessionKey(conversationId)?.let { return@withContext it }
            
            // Fetch encrypted key from server
            val keyResponse = api.getMyConversationKey("Bearer $token", conversationId)
            
            // Store encrypted key and decrypt it
            keyManager.storeEncryptedSessionKey(conversationId, keyResponse.encryptedSessionKey)
            return@withContext keyManager.getAndDecryptSessionKey(conversationId)
        } catch (e: retrofit2.HttpException) {
            // If 404 and haven't retried much, wait and retry (key might be being created)
            if (e.code() == 404 && retryCount < 2) {
                Log.d(TAG, "Key not found (404) for conversation $conversationId, retry ${retryCount + 1}/2 after delay")
                kotlinx.coroutines.delay(1000) // Wait 1 second
                return@withContext getSessionKey(conversationId, token, retryCount + 1)
            }
            
            Log.e(TAG, "HTTP ${e.code()} getting session key for conversation $conversationId", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session key for conversation $conversationId", e)
            null
        }
    }
    
    /**
     * Encrypt a message with AES-GCM
     * 
     * @param plaintext The message to encrypt
     * @param conversationId The conversation ID
     * @param token Authentication token
     * @return AESEncryptedData if successful, null otherwise
     */
    suspend fun encryptMessage(
        plaintext: String,
        conversationId: String,
        token: String
    ): AESEncryptedData? = withContext(Dispatchers.IO) {
        try {
            val sessionKey = getSessionKey(conversationId, token) ?: run {
                Log.e(TAG, "No session key available for conversation $conversationId")
                return@withContext null
            }
            
            CryptoManager.aesEncrypt(plaintext, sessionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting message", e)
            null
        }
    }
    
    /**
     * Decrypt a message with AES-GCM
     * 
     * @param ciphertext The encrypted message (Base64)
     * @param iv The initialization vector (Base64)
     * @param conversationId The conversation ID
     * @param token Authentication token
     * @return Decrypted plaintext if successful, null otherwise
     */
    suspend fun decryptMessage(
        ciphertext: String,
        iv: String,
        conversationId: String,
        token: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val sessionKey = getSessionKey(conversationId, token) ?: run {
                Log.e(TAG, "No session key available for conversation $conversationId")
                return@withContext null
            }
            
            CryptoManager.aesDecrypt(ciphertext, iv, sessionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message", e)
            null
        }
    }
    
    /**
     * Encrypt raw bytes (media) with AES-GCM
     */
    suspend fun encryptBytes(
        data: ByteArray,
        conversationId: String,
        token: String
    ): AESEncryptedData? = withContext(Dispatchers.IO) {
        try {
            val sessionKey = getSessionKey(conversationId, token) ?: run {
                Log.e(TAG, "No session key available for conversation $conversationId")
                return@withContext null
            }
            CryptoManager.aesEncryptBytes(data, sessionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting bytes for conversation $conversationId", e)
            null
        }
    }

    /**
     * Decrypt raw bytes (media) with AES-GCM
     */
    suspend fun decryptBytes(
        ciphertextBase64: String,
        iv: String,
        conversationId: String,
        token: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val sessionKey = getSessionKey(conversationId, token) ?: run {
                Log.e(TAG, "No session key available for conversation $conversationId")
                return@withContext null
            }
            CryptoManager.aesDecryptToBytes(ciphertextBase64, iv, sessionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting bytes for conversation $conversationId", e)
            null
        }
    }
    
    /**
     * Check if encryption is available for a conversation
     * Thread-safe operation
     */
    suspend fun isEncryptionAvailable(conversationId: String): Boolean {
        return keyManager.hasSessionKey(conversationId)
    }
    
    /**
     * Store session key for a conversation (used when creating conversation with keys)
     */
    suspend fun storeSessionKeyForConversation(conversationId: String, sessionKey: SecretKey) {
        keyManager.storeSessionKey(conversationId, sessionKey)
    }
    
    /**
     * Clear session key for a conversation (used when deleting conversation)
     */
    suspend fun clearSessionKeyForConversation(conversationId: String) {
        keyManager.clearSessionKey(conversationId)
    }
}

