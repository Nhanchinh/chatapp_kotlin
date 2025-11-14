package com.example.chatapp.data.encryption

import android.content.Context
import android.util.Log
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.EncryptedSessionKeyDto
import com.example.chatapp.data.remote.model.StoreKeysRequest
import kotlinx.coroutines.Dispatchers
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
    
    /**
     * Setup encryption for a new conversation
     * - Generates a new AES session key
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
        try {
            // Generate new AES session key
            val sessionKey = CryptoManager.generateAESKey()
            
            // Fetch public keys for all participants
            val userIdsParam = participantIds.joinToString(",")
            val publicKeysResponse = api.getPublicKeys("Bearer $token", userIdsParam)
            
            if (publicKeysResponse.items.isEmpty()) {
                Log.e(TAG, "No public keys found for participants")
                return@withContext false
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
                return@withContext false
            }
            
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
        }
    }
    
    /**
     * Get or fetch the session key for a conversation
     * 
     * @param conversationId The conversation ID
     * @param token Authentication token
     * @return SecretKey if found, null otherwise
     */
    suspend fun getSessionKey(conversationId: String, token: String): SecretKey? = withContext(Dispatchers.IO) {
        try {
            // Check if we already have the key locally
            keyManager.getSessionKey(conversationId)?.let { return@withContext it }
            
            // Fetch encrypted key from server
            val keyResponse = api.getMyConversationKey("Bearer $token", conversationId)
            
            // Store encrypted key and decrypt it
            keyManager.storeEncryptedSessionKey(conversationId, keyResponse.encryptedSessionKey)
            return@withContext keyManager.getAndDecryptSessionKey(conversationId)
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
     * Check if encryption is available for a conversation
     */
    fun isEncryptionAvailable(conversationId: String): Boolean {
        return keyManager.hasSessionKey(conversationId)
    }
}

