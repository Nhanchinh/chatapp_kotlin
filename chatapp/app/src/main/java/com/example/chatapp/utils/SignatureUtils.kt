package com.example.chatapp.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

object SignatureUtils {

    // SHA-256 của debug.keystore hiện tại (đã khóa để chống đóng gói lại)
    private const val VALID_SIGNATURE_HASH = "22:9D:FA:84:FA:0C:C9:F9:35:8E:29:5A:96:A9:08:3D:03:90:1B:CB:41:38:9A:46:C8:79:B8:96:DD:C6:93:77"

    /**
     * Lấy mã SHA-256 của chữ ký ứng dụng hiện tại.
     * Trả về chuỗi Hex (VD: AA:BB:CC...) hoặc null lỗi.
     */
    fun getAppSignature(context: Context): String? {
        try {
            val packageName = context.packageName
            val packageManager = context.packageManager
            
            // Lấy thông tin package cùng với chữ ký
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            
            val packageInfo = packageManager.getPackageInfo(packageName, flags)

            // Lấy mảng signatures (hỗ trợ cả version cũ và mới)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners ?: packageInfo.signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return null

            // Chỉ lấy chữ ký đầu tiên (thường app chỉ dùng 1 key)
            val signature = signatures[0]

            // Băm chữ ký bằng thuật toán SHA-256
            val md = MessageDigest.getInstance("SHA-256")
            val updatedSignature = md.digest(signature.toByteArray())

            // Chuyển byte array sang chuỗi Hex format AA:BB:CC...
            return bytesToHex(updatedSignature)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Kiểm tra xem chữ ký hiện tại có khớp với chữ ký hợp lệ (VALID_SIGNATURE_HASH) hay không.
     */
    fun isValidSignature(context: Context): Boolean {
        val currentSignature = getAppSignature(context)
        // Nếu không lấy được signature hoặc signature khác với bản gốc -> FALSE
        return currentSignature != null && currentSignature.equals(VALID_SIGNATURE_HASH, ignoreCase = true)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val hexChars = CharArray(bytes.size * 3 - 1)
        var v: Int
        for (j in bytes.indices) {
            v = bytes[j].toInt() and 0xFF
            hexChars[j * 3] = hexArray[v ushr 4]
            hexChars[j * 3 + 1] = hexArray[v and 0x0F]
            if (j < bytes.size - 1) {
                hexChars[j * 3 + 2] = ':'
            }
        }
        return String(hexChars)
    }
}
