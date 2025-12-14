package com.example.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chatapp.utils.UrlHelper

/**
 * Avatar component that displays user's avatar image or initials fallback.
 * 
 * @param name User's display name (used to generate initials)
 * @param imageUrl Relative path to avatar (e.g., "/static/avatars/xxx.jpg") or null
 * @param modifier Compose modifier
 * @param sizeDp Avatar size in dp
 */
@Composable
fun Avatar(
    name: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48
) {
    val fullUrl = UrlHelper.avatar(imageUrl)
    val initials = name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (fullUrl != null) {
            // Show avatar image from URL
            AsyncImage(
                model = fullUrl,
                contentDescription = "Avatar of $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .clip(CircleShape)
            )
        } else {
            // Fallback to initials
            Text(
                text = initials.ifEmpty { "?" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}


