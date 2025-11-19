package com.example.chatapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.chatapp.data.model.MediaItem
import com.example.chatapp.viewmodel.ChatViewModel
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

    LaunchedEffect(conversationId) {
        chatViewModel.loadMediaGallery(conversationId)
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.clearMediaGallery()
        }
    }

    Scaffold(
        topBar = {
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
                mediaItems.isEmpty() -> {
                    Text(
                        text = "Chưa có ảnh nào trong cuộc trò chuyện này",
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
                        items(mediaItems, key = { it.mediaId }) { item ->
                            MediaThumbnail(item = item, onClick = { onMediaClick(item) })
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.LightGray, shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ảnh", color = Color.White)
                Text("Đang tải...", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

