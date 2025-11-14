package com.example.chatapp.data.encryption

import android.util.Base64
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager handles all cryptographic operations for E2EE:
 * - RSA key generation and encryption/decryption
 * - AES-GCM encryption/decryption
 * - Key encoding/decoding
 */
object CryptoManager {
    
    private const val RSA_KEY_SIZE = 2048
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_SIZE = 12 // 96 bits
    private const val GCM_TAG_SIZE = 128 // bits
    
    // RSA transformation with OAEP padding (more secure than PKCS1)
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    
    // AES-GCM transformation
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    
    /**
     * Generate a new RSA-2048 keypair
     * Returns: Pair<PublicKey, PrivateKey>
     */
    fun generateRSAKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(RSA_KEY_SIZE, SecureRandom())
        return keyGen.generateKeyPair()
    }
    
    /**
     * Generate a new AES-256 session key
     */
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE, SecureRandom())
        return keyGen.generateKey()
    }
    
    /**
     * Encrypt data with RSA public key (for encrypting AES session keys)
     * Returns Base64-encoded ciphertext
     */
    fun rsaEncrypt(plaintext: ByteArray, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        
        // Use OAEP with SHA-256 main digest and SHA-1 for MGF1 (for Android compatibility)
        val oaepParams = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,  // Changed to SHA1 for Android Keystore compatibility
            PSource.PSpecified.DEFAULT
        )
        
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams)
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt data with RSA private key (for decrypting AES session keys)
     * Input: Base64-encoded ciphertext
     */
    fun rsaDecrypt(ciphertextBase64: String, privateKey: PrivateKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        
        val oaepParams = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,  // Changed to SHA1 for Android Keystore compatibility
            PSource.PSpecified.DEFAULT
        )
        
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Encrypt message content with AES-GCM
     * Returns: AESEncryptedData containing ciphertext and IV (both Base64-encoded)
     */
    fun aesEncrypt(plaintext: String, secretKey: SecretKey): AESEncryptedData {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        
        // Generate random IV
        val iv = ByteArray(GCM_IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)
        
        return AESEncryptedData(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }
    
    /**
     * Decrypt message content with AES-GCM
     * Input: ciphertext and IV (both Base64-encoded)
     */
    fun aesDecrypt(ciphertextBase64: String, ivBase64: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
    
    // ========== Key Encoding/Decoding ==========
    
    /**
     * Encode PublicKey to Base64 string (X.509 format)
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Decode PublicKey from Base64 string (X.509 format)
     */
    fun decodePublicKey(publicKeyBase64: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }
    
    /**
     * Encode SecretKey (AES) to Base64 string
     */
    fun encodeSecretKey(secretKey: SecretKey): String {
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Decode SecretKey (AES) from Base64 string
     */
    fun decodeSecretKey(secretKeyBase64: String): SecretKey {
        val keyBytes = Base64.decode(secretKeyBase64, Base64.NO_WRAP)
        return SecretKeySpec(keyBytes, "AES")
    }
}

/**
 * Data class for AES-GCM encrypted data
 */
data class AESEncryptedData(
    val ciphertext: String,  // Base64-encoded ciphertext (includes auth tag)
    val iv: String          // Base64-encoded initialization vector
)

