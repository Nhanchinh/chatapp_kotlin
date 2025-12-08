package com.example.chatapp.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.Context
import java.io.File

data class VideoInfo(
    val thumbnail: Bitmap?,
    val duration: Long // in milliseconds
)

fun getVideoThumbnail(context: Context, uri: Uri): VideoInfo? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val thumbnail = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        
        retriever.release()
        
        VideoInfo(thumbnail, duration)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

