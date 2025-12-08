package com.example.chatapp.ui.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chatapp.ui.common.saveMediaToGallery
import com.example.chatapp.viewmodel.ChatViewModel
import java.io.File

@Composable
fun MediaViewerScreen(
    conversationId: String,
    mediaId: String,
    mimeType: String?,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var localPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaId, conversationId) {
        isLoading = true
        errorMessage = null
        val path = chatViewModel.ensureMediaCached(mediaId, conversationId)
        if (path != null) {
            localPath = path
        } else {
            errorMessage = "Không thể tải ảnh"
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorMessage ?: "", color = Color.White)
            }
        } else {
            localPath?.let { path ->
                val isVideo = mimeType?.startsWith("video/") == true
                
                if (isVideo) {
                    // For video: open with external player
                    LaunchedEffect(path) {
                        try {
                            val videoFile = File(path)
                            val videoUri = if (path.startsWith("content://")) {
                                Uri.parse(path)
                            } else {
                                // Use FileProvider for file:// URIs
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    videoFile
                                )
                            }
                            
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(videoUri, mimeType)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                                onBack() // Close viewer after opening video
                            } else {
                                Toast.makeText(context, "Không tìm thấy ứng dụng để phát video", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Không thể mở video: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Show loading while opening video
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                } else {
                    // For images: show image
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 8.dp, end = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            IconButton(
                onClick = {
                    val path = localPath
                    if (path != null) {
                        val success = saveMediaToGallery(context, path, mimeType)
                        Toast.makeText(
                            context,
                            if (success) "Đã lưu vào thư viện" else "Lưu thất bại",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
            }
        }
    }
}

