package com.example.chatapp.ui.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.io.IOException

fun saveMediaToGallery(
    context: Context,
    sourcePath: String,
    mimeType: String?
): Boolean {
    val sourceFile = File(sourcePath)
    if (!sourceFile.exists()) return false

    val type = mimeType ?: MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(sourceFile.extension) ?: "image/jpeg"

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val fileName = "chatapp_${System.currentTimeMillis()}.${sourceFile.extension.ifEmpty { "jpg" }}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChatApp")
        }
    }

    val resolver = context.contentResolver
    val uri: Uri = resolver.insert(collection, values) ?: return false

    return try {
        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: IOException) {
        resolver.delete(uri, null, null)
        false
    }
}

/**
 * Save file to Downloads folder and open it with appropriate app
 * Similar to Messenger behavior
 */
fun saveFileToDownloads(
    context: Context,
    sourcePath: String,
    mimeType: String?,
    originalFileName: String? = null
): Boolean {
    val sourceFile = File(sourcePath)
    if (!sourceFile.exists()) return false

    val fileName = originalFileName ?: sourceFile.name
    val extension = sourceFile.extension.ifEmpty { 
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    }
    val finalFileName = if (fileName.contains(".")) fileName else "$fileName.$extension"

    return try {
        var savedFilePath: String? = null
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+): Use MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/ChatApp")
            }
            val resolver = context.contentResolver
            val downloadUri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: return false

            resolver.openOutputStream(downloadUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            // Mark as not pending
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(downloadUri, values, null, null)

            // For Android 10+, construct path from MediaStore
            savedFilePath = "/storage/emulated/0/Download/ChatApp/$finalFileName"
            downloadUri
        } else {
            // Android 9 and below: Try MediaStore first, fallback to direct file copy
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
                }
                val resolver = context.contentResolver
                val downloadUri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    savedFilePath = "/storage/emulated/0/Download/ChatApp/$finalFileName"
                    downloadUri
                } else {
                    // Fallback: copy to Downloads folder
                    val downloadsDir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "ChatApp"
                    )
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    val destFile = File(downloadsDir, finalFileName)
                    sourceFile.copyTo(destFile, overwrite = true)
                    savedFilePath = destFile.absolutePath
                    Uri.fromFile(destFile)
                }
            } catch (e: Exception) {
                // Fallback: copy to Downloads folder
                val downloadsDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "ChatApp"
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val destFile = File(downloadsDir, finalFileName)
                sourceFile.copyTo(destFile, overwrite = true)
                savedFilePath = destFile.absolutePath
                Uri.fromFile(destFile)
            }
        }

        // Open file with appropriate app
        openFileWithIntent(context, uri, mimeType, finalFileName)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun openFileWithIntent(context: Context, uri: Uri, mimeType: String?, fileName: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || uri.scheme != "file") {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Toast.makeText(context, "Đã lưu và mở file", Toast.LENGTH_SHORT).show()
        } else {
            // Show simple file path message
            Toast.makeText(
                context,
                "File đã lưu vào Downloads/ChatApp/$fileName",
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(
            context,
            "File đã lưu vào Downloads/ChatApp/$fileName",
            Toast.LENGTH_LONG
        ).show()
    }
}

