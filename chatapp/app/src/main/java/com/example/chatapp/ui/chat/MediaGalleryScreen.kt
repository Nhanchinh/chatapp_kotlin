package com.example.chatapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.chatapp.data.model.MediaItem
import com.example.chatapp.viewmodel.ChatViewModel
import com.example.chatapp.utils.getVideoThumbnail
import com.example.chatapp.utils.formatDuration
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaGalleryScreen(
    conversationId: String,
    contactName: String,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    val mediaItems by chatViewModel.mediaGallery.collectAsStateWithLifecycle()
    val isLoading by chatViewModel.mediaGalleryLoading.collectAsStateWithLifecycle()
    val error by chatViewModel.mediaGalleryError.collectAsStateWithLifecycle()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ảnh", "File", "Video")

    LaunchedEffect(conversationId) {
        chatViewModel.loadMediaGallery(conversationId)
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.clearMediaGallery()
        }
    }
    
    // Filter media items by type
    val filteredItems = when (selectedTabIndex) {
        0 -> mediaItems.filter { it.mimeType?.startsWith("image/") == true }
        1 -> mediaItems.filter { 
            val mimeType = it.mimeType ?: ""
            !mimeType.startsWith("image/") && !mimeType.startsWith("video/") && !mimeType.startsWith("audio/")
        }
        2 -> mediaItems.filter { it.mimeType?.startsWith("video/") == true }
        else -> emptyList()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Phương tiện của $contactName") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2196F3),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            when {
                isLoading && mediaItems.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                filteredItems.isEmpty() -> {
                    val emptyMessage = when (selectedTabIndex) {
                        0 -> "Chưa có ảnh nào trong cuộc trò chuyện này"
                        1 -> "Chưa có file nào trong cuộc trò chuyện này"
                        2 -> "Chưa có video nào trong cuộc trò chuyện này"
                        else -> "Chưa có phương tiện nào"
                    }
                    Text(
                        text = emptyMessage,
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.mediaId }) { item ->
                            MediaThumbnail(
                                item = item,
                                onClick = {
                                    when (selectedTabIndex) {
                                        0 -> onMediaClick(item) // Ảnh: mở MediaViewerScreen
                                        1 -> chatViewModel.downloadAndOpenFile(item.mediaId, conversationId, item.mimeType) // File: tải xuống
                                        2 -> onMediaClick(item) // Video: mở MediaViewerScreen
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: MediaItem,
    onClick: () -> Unit
) {
    val file = item.localPath?.let { File(it) }
    val mimeType = item.mimeType ?: ""
    val isImage = mimeType.startsWith("image/")
    val isVideo = mimeType.startsWith("video/")
    val isFile = !isImage && !isVideo && !mimeType.startsWith("audio/")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.LightGray, shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isImage && file != null && file.exists() -> {
                // Hiển thị ảnh thumbnail
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isVideo && file != null && file.exists() -> {
                // Hiển thị video thumbnail thực sự từ video với play icon
                val context = LocalContext.current
                var videoThumbnail by remember(item.mediaId) { mutableStateOf<Bitmap?>(null) }
                var videoDuration by remember(item.mediaId) { mutableStateOf(0L) }
                
                LaunchedEffect(item.mediaId, file.absolutePath) {
                    withContext(Dispatchers.IO) {
                        try {
                            // Resolve URI - support both content:// and file://
                            val videoUri = if (item.localPath?.startsWith("content://") == true || item.localPath?.startsWith("file://") == true) {
                                Uri.parse(item.localPath)
                            } else {
                                Uri.fromFile(file)
                            }
                            val videoInfo = getVideoThumbnail(context, videoUri)
                            videoThumbnail = videoInfo?.thumbnail
                            videoDuration = videoInfo?.duration ?: 0L
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // Hiển thị thumbnail từ video
                if (videoThumbnail != null) {
                    Image(
                        bitmap = videoThumbnail!!.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Loading state - hiển thị background đen với loading indicator
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Play icon overlay
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                )
                
                // Duration overlay (bottom right) - chỉ hiển thị nếu có thumbnail
                if (videoThumbnail != null && videoDuration > 0) {
                    Text(
                        text = formatDuration(videoDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            isFile -> {
                // Hiển thị file icon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "File",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    if (file != null && file.exists()) {
                        Text(
                            text = file.name.take(15) + if (file.name.length > 15) "..." else "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "Đang tải...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            else -> {
                // Loading state
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Đang tải...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

