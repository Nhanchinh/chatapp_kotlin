package com.example.chatapp.data.encryption

import android.content.Context
import android.util.Log
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.CreateBackupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * KeyBackupManager handles backup and restore of conversation AES keys.
 * Keys are encrypted with a PIN-derived key (using PBKDF2) before uploading.
 * 
 * Flow:
 * 1. Backup: Collect all session keys -> JSON -> Encrypt with PIN -> Upload
 * 2. Restore: Download -> Decrypt with PIN -> Re-encrypt with new RSA key -> Store locally
 */
class KeyBackupManager(context: Context) {
    
    private val TAG = "KeyBackupManager"
    private val keyManager = KeyManager(context)
    private val e2eeManager = E2EEManager(context)
    private val api = ApiClient.apiService
    
    companion object {
        const val MAX_PIN_ATTEMPTS = 5
    }
    
    /**
     * Data class representing a backup
     */
    data class BackupData(
        val encryptedBackup: String,
        val salt: String,
        val iv: String,
        val conversationIds: List<String>
    )
    
    /**
     * Export all conversation keys and encrypt with PIN
     * @param pin User's backup PIN (6 digits recommended)
     * @param token Authentication token (needed to verify which conversations exist)
     * @return BackupData or null if failed
     */
    suspend fun createBackup(pin: String, token: String? = null): BackupData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating key backup...")
            
            // Get all session keys from local storage
            val keysJson = exportAllSessionKeys()
            
            if (keysJson == null) {
                Log.e(TAG, "No session keys found to backup")
                return@withContext null
            }
            
            // Filter out keys for deleted conversations if token is provided
            val filteredKeysJson = if (token != null) {
                filterKeysForExistingConversations(keysJson, token)
            } else {
                keysJson
            }
            
            val conversationIds = filteredKeysJson.keys().asSequence().toList()
            
            if (conversationIds.isEmpty()) {
                Log.e(TAG, "No valid conversation keys to backup (all conversations may have been deleted)")
                return@withContext null
            }
            
            Log.d(TAG, "Found ${conversationIds.size} conversation keys to backup (filtered from ${keysJson.length()} total)")
            
            // Encrypt with PIN
            val (encrypted, salt, iv) = CryptoManager.encryptWithPin(filteredKeysJson.toString(), pin)
            
            Log.d(TAG, "✅ Backup created successfully")
            BackupData(
                encryptedBackup = encrypted,
                salt = salt,
                iv = iv,
                conversationIds = conversationIds
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
            null
        }
    }
    
    /**
     * Filter keys to only include those for conversations that still exist
     */
    private suspend fun filterKeysForExistingConversations(keysJson: JSONObject, token: String): JSONObject {
        try {
            // Get list of existing conversations
            val response = api.getConversations("Bearer $token", limit = 100)
            val existingConversationIds = response.items.map { it.id }.toSet()
            
            Log.d(TAG, "User has ${existingConversationIds.size} existing conversations")
            
            // Filter keys
            val filteredJson = JSONObject()
            val iterator = keysJson.keys()
            var removedCount = 0
            
            while (iterator.hasNext()) {
                val conversationId = iterator.next()
                if (conversationId in existingConversationIds) {
                    filteredJson.put(conversationId, keysJson.getString(conversationId))
                } else {
                    Log.d(TAG, "⏭️ Skipping orphan key for deleted conversation: $conversationId")
                    removedCount++
                }
            }
            
            if (removedCount > 0) {
                Log.d(TAG, "Removed $removedCount orphan keys from backup")
            }
            
            return filteredJson
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering conversations, using all keys", e)
            return keysJson
        }
    }
    
    /**
     * Clean up local orphan keys for conversations that no longer exist.
     * This removes keys from local storage that are for deleted conversations.
     * 
     * @param token Authentication token
     * @return Number of orphan keys removed
     */
    suspend fun cleanupOrphanKeys(token: String): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cleaning up orphan local keys...")
            
            // Get all local keys
            val allLocalKeys = keyManager.getAllSessionKeys()
            if (allLocalKeys.isEmpty()) {
                Log.d(TAG, "No local keys to clean up")
                return@withContext 0
            }
            
            // Get list of existing conversations
            val response = api.getConversations("Bearer $token", limit = 100)
            val existingConversationIds = response.items.map { it.id }.toSet()
            
            Log.d(TAG, "Found ${allLocalKeys.size} local keys, ${existingConversationIds.size} existing conversations")
            
            // Find orphan keys
            var removedCount = 0
            for ((conversationId, _) in allLocalKeys) {
                if (conversationId !in existingConversationIds) {
                    Log.d(TAG, "🗑️ Removing orphan key for: $conversationId")
                    keyManager.clearSessionKey(conversationId)
                    removedCount++
                }
            }
            
            Log.d(TAG, "✅ Cleaned up $removedCount orphan keys")
            removedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orphan keys", e)
            0
        }
    }
    
    /**
     * Upload backup to server
     * @param backup BackupData from createBackup
     * @param token Auth token
     * @return true if successful
     */
    suspend fun uploadBackup(backup: BackupData, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading backup to server...")
            
            val request = CreateBackupRequest(
                encryptedBackup = backup.encryptedBackup,
                salt = backup.salt,
                iv = backup.iv,
                conversationIds = backup.conversationIds
            )
            
            api.createKeyBackup("Bearer $token", request)
            Log.d(TAG, "✅ Backup uploaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading backup", e)
            false
        }
    }
    
    /**
     * Check if backup exists on server
     */
    suspend fun hasBackup(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.checkKeyBackupExists("Bearer $token")
            response.exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking backup exists", e)
            false
        }
    }
    
    /**
     * Get backup metadata (without decrypting)
     */
    suspend fun getBackupInfo(token: String): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            val response = api.getKeyBackup("Bearer $token")
            Pair(response.conversationIds.size, response.updatedAt)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) null else throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting backup info", e)
            null
        }
    }
    
    /**
     * Restore keys from backup
     * @param pin PIN used during backup creation
     * @param token Auth token
     * @return Pair(success, errorMessage)
     */
    suspend fun restoreBackup(pin: String, token: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Restoring backup...")
            
            // 1. Download backup from server
            val backup = try {
                api.getKeyBackup("Bearer $token")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    return@withContext Pair(false, "Không tìm thấy backup")
                }
                throw e
            }
            
            Log.d(TAG, "Downloaded backup with ${backup.conversationIds.size} conversations")
            
            // 2. Decrypt with PIN
            val keysJson = try {
                val decrypted = CryptoManager.decryptWithPin(
                    backup.encryptedBackup,
                    backup.salt,
                    backup.iv,
                    pin
                )
                JSONObject(decrypted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt backup - wrong PIN?", e)
                return@withContext Pair(false, "Sai mã PIN")
            }
            
            Log.d(TAG, "✅ Backup decrypted successfully")
            
            // 3. Store session keys locally
            val restoredCount = importSessionKeys(keysJson, token)
            
            Log.d(TAG, "✅ Restored $restoredCount conversation keys")
            Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup", e)
            Pair(false, "Lỗi khôi phục: ${e.message}")
        }
    }
    
    /**
     * Delete backup from server
     */
    suspend fun deleteBackup(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            api.deleteKeyBackup("Bearer $token")
            Log.d(TAG, "✅ Backup deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backup", e)
            false
        }
    }
    
    /**
     * Export all session keys to JSON format
     * Format: { "conversation_id_1": "base64_encoded_key", ... }
     */
    private suspend fun exportAllSessionKeys(): JSONObject? {
        val userId = keyManager.getActiveUserId() ?: return null
        
        // Get all keys from EncryptedSharedPreferences
        // Note: We need to iterate through known conversation IDs
        // This requires tracking which conversations have keys
        
        val keysJson = JSONObject()
        
        // Get the encrypted prefs to find all session keys
        // The key pattern is: session_key_{userId}_{conversationId}
        val prefix = "session_key_${userId}_"
        
        // Access encryptedPrefs through reflection or add a method to KeyManager
        // For now, we'll add a helper method to KeyManager
        val allKeys = keyManager.getAllSessionKeys()
        
        if (allKeys.isEmpty()) {
            return null
        }
        
        for ((conversationId, keyBase64) in allKeys) {
            keysJson.put(conversationId, keyBase64)
        }
        
        return keysJson
    }
    
    /**
     * Import session keys from JSON and store locally
     * Also re-encrypts keys with new RSA public key and uploads to server
     * 
     * **IMPORTANT**: Only restores keys if:
     * 1. No key exists on server for this conversation, OR
     * 2. Existing key on server cannot be decrypted (outdated)
     * This prevents overwriting newer keys with old backup keys.
     */
    private suspend fun importSessionKeys(keysJson: JSONObject, token: String): Int {
        var restoredCount = 0
        
        // Get RSA public key for re-encryption
        val publicKey = keyManager.getRSAPublicKey()
        if (publicKey == null) {
            Log.e(TAG, "❌ No RSA public key available for re-encryption!")
            return 0
        }
        
        val iterator = keysJson.keys()
        while (iterator.hasNext()) {
            val conversationId = iterator.next()
            val keyBase64 = keysJson.getString(conversationId)
            
            try {
                // **CRITICAL**: Check if key already exists on server and can be decrypted
                val shouldRestore = try {
                    val existingKey = api.getMyConversationKey("Bearer $token", conversationId)
                    // Key exists on server, check if we can decrypt it
                    Log.d(TAG, "Key exists on server for $conversationId, checking if it works...")
                    
                    // Try to decrypt existing key
                    val privateKey = keyManager.getRSAPrivateKey()
                    if (privateKey != null) {
                        try {
                            CryptoManager.rsaDecrypt(existingKey.encryptedSessionKey, privateKey)
                            // Decryption succeeded - existing key is valid, DO NOT overwrite
                            Log.d(TAG, "✅ Existing key for $conversationId works, skipping restore")
                            false
                        } catch (e: Exception) {
                            // Decryption failed - existing key is outdated, restore backup
                            Log.w(TAG, "⚠️ Existing key for $conversationId is outdated, will restore from backup")
                            true
                        }
                    } else {
                        Log.e(TAG, "❌ No private key available to verify existing key")
                        false
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) {
                        // No key on server - safe to restore
                        Log.d(TAG, "No key on server for $conversationId, will restore from backup")
                        true
                    } else {
                        Log.e(TAG, "HTTP ${e.code()} checking key for $conversationId", e)
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking existing key for $conversationId", e)
                    false
                }
                
                if (!shouldRestore) {
                    Log.d(TAG, "⏭️ Skipping restore for $conversationId (key exists and works)")
                    continue
                }
                
                // 1. Decode the session key from backup
                val sessionKey = CryptoManager.decodeSecretKey(keyBase64)
                
                // 2. Store session key locally
                keyManager.storeSessionKey(conversationId, sessionKey)
                
                // 3. Re-encrypt with new public key
                val encryptedKey = CryptoManager.rsaEncrypt(sessionKey.encoded, publicKey)
                
                // 4. Store encrypted key locally (for cache)
                keyManager.storeEncryptedSessionKey(conversationId, encryptedKey)
                
                // 5. Upload re-encrypted key to server
                try {
                    val storeRequest = com.example.chatapp.data.remote.model.StoreKeysRequest(
                        conversationId = conversationId,
                        keys = listOf(
                            com.example.chatapp.data.remote.model.EncryptedSessionKeyDto(
                                userId = keyManager.getActiveUserId() ?: "",
                                encryptedSessionKey = encryptedKey
                            )
                        )
                    )
                    api.storeConversationKeys("Bearer $token", storeRequest)
                    Log.d(TAG, "✅ Uploaded re-encrypted key for conversation: $conversationId")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Failed to upload key for $conversationId, but local restore OK", e)
                }
                
                restoredCount++
                Log.d(TAG, "✅ Restored key for conversation: $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore key for conversation: $conversationId", e)
            }
        }
        
        return restoredCount
    }
}
