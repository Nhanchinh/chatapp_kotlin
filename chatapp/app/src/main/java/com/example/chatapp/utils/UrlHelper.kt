package com.example.chatapp.utils

import com.example.chatapp.BuildConfig

/**
 * Helper object to construct full URLs from relative paths.
 * 
 * GOLDEN PRINCIPLE:
 * - Backend stores ONLY relative paths (e.g., "/static/avatars/abc.jpg")
 * - Client ALWAYS combines BASE_URL with relative path
 * 
 * This helper ensures consistent URL construction across the app.
 */
object UrlHelper {
    
    private val baseUrl: String
        get() = BuildConfig.BASE_URL.trimEnd('/')
    
    /**
     * Construct full avatar URL from relative path.
     * 
     * @param path Relative path from server (e.g., "/static/avatars/abc.jpg")
     * @return Full URL or null if path is blank
     * 
     * Usage:
     * ```kotlin
     * Image(
     *     painter = rememberAsyncImagePainter(UrlHelper.avatar(user.avatar)),
     *     contentDescription = null
     * )
     * ```
     */
    fun avatar(path: String?): String? {
        if (path.isNullOrBlank()) return null
        // Already a full URL (e.g., from external source)
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        // Combine base URL with relative path
        return baseUrl + path
    }
    
    /**
     * Construct full media URL from relative path.
     * Same logic as avatar but for general media files.
     */
    fun media(path: String?): String? {
        return avatar(path)  // Same logic
    }
}
