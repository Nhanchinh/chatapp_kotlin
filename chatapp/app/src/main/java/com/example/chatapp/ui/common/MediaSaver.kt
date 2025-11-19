package com.example.chatapp.ui.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
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

