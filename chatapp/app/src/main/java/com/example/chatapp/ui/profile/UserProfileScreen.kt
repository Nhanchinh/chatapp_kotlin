package com.example.chatapp.ui.profile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.utils.UrlHelper
import com.example.chatapp.utils.rememberImagePickerLauncher
import com.example.chatapp.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsState()
    val userName = authState.userFullName ?: authState.userEmail ?: "Người dùng"
    val subEmail = authState.userEmail ?: ""
    var coverImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var showAvatarOptionsSheet by remember { mutableStateOf(false) }  // Show avatar options bottom sheet
    
    val coverImagePicker = rememberImagePickerLauncher { uri ->
        coverImageUri = uri
    }
    
    // Avatar picker with upload logic
    val profileImagePicker = rememberImagePickerLauncher { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingAvatar = true
                uploadError = null
                try {
                    val result = uploadAvatarToServer(context, selectedUri)
                    if (result != null) {
                        authViewModel.updateAvatarInState(result)
                    } else {
                        uploadError = "Upload thất bại"
                    }
                } catch (e: Exception) {
                    uploadError = e.message ?: "Lỗi không xác định"
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }
    
    // Blue gradient color for avatar border (matches app theme)
    val blueGradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(Color(0xFF64B5F6), Color(0xFF2196F3))
    )
    
    var showEditDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                title = {
                    Text(
                        text = "Hồ sơ",
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                },
                actions = {
                    IconButton(onClick = { /* Menu options */ }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        var showEditNameDialog by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Profile header: Avatar left, Info right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with orange gradient border
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(blueGradient)
                        .clickable(enabled = !isUploadingAvatar) { showAvatarOptionsSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUrl = UrlHelper.avatar(authState.userAvatar)
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFB39DDB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // User info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Name with edit icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = userName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Chỉnh sửa tên",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showEditNameDialog = true }
                        )
                    }
                    
                    // Email
                    if (subEmail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subEmail,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    
                    // Friend count badge
                    authState.userFriendCount?.let { count ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$count người bạn",
                                    fontSize = 13.sp,
                                    color = Color(0xFF2196F3),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // Upload error
            uploadError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Chi tiết section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chi tiết",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Chỉnh sửa",
                        fontSize = 14.sp,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.clickable { showEditDialog = true }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                DetailItem(
                    icon = Icons.Default.Home,
                    label = "Sống tại",
                    value = authState.userLocation ?: "Chưa cập nhật"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                DetailItem(
                    icon = Icons.Default.LocationCity,
                    label = "Đến từ",
                    value = authState.userHometown ?: "Chưa cập nhật"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                DetailItem(
                    icon = Icons.Default.Cake,
                    label = "Năm sinh",
                    value = authState.userBirthYear?.toString() ?: "Chưa cập nhật"
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Edit Name Dialog
        if (showEditNameDialog) {
            EditNameDialog(
                currentName = userName,
                onDismiss = { showEditNameDialog = false },
                onSave = { newName ->
                    scope.launch {
                        authViewModel.updateFullName(newName)
                        showEditNameDialog = false
                    }
                }
            )
        }
    }
    
    // Edit Profile Dialog
    if (showEditDialog) {
        EditProfileDialog(
            authViewModel = authViewModel,
            onDismiss = { showEditDialog = false }
        )
    }
    
    // Avatar Options Bottom Sheet
    if (showAvatarOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarOptionsSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Ảnh đại diện",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAvatarOptionsSheet = false
                            profileImagePicker.launch("image/*")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Tải lên ảnh",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var isLoading by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Chỉnh sửa tên",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        focusedLabelColor = Color(0xFF2196F3)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", color = Color.Gray)
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                isLoading = true
                                onSave(name.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        ),
                        enabled = name.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text("Lưu")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var location by remember { mutableStateOf(authState.userLocation ?: "") }
    var hometown by remember { mutableStateOf(authState.userHometown ?: "") }
    var birthYear by remember { mutableStateOf(authState.userBirthYear?.toString() ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Chỉnh sửa thông tin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Sống tại") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = hometown,
                    onValueChange = { hometown = it },
                    label = { Text("Đến từ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it },
                    label = { Text("Năm sinh") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val year = birthYear.toIntOrNull()
                                if (birthYear.isNotEmpty() && year == null) {
                                    errorMessage = "Năm sinh không hợp lệ"
                                    isLoading = false
                                    return@launch
                                }
                                val result = authViewModel.updateProfile(
                                    location = location.ifEmpty { null },
                                    hometown = hometown.ifEmpty { null },
                                    birthYear = year
                                )
                                isLoading = false
                                if (result.isSuccess) {
                                    onDismiss()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Cập nhật thất bại"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Lưu")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Upload avatar image to server.
 * @return Relative path if successful, null if failed
 */
private suspend fun uploadAvatarToServer(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            // Get input stream from URI
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot read file")
            
            // Get content type
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            
            // Read bytes
            val bytes = inputStream.use { it.readBytes() }
            
            // Create multipart body
            val requestBody = bytes.toRequestBody(contentType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData(
                "file",
                "avatar.${contentType.substringAfter("/")}",
                requestBody
            )
            
            // Get auth token
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            // Use DataStore approach if available, fallback to direct API call
            val token = com.example.chatapp.data.local.AuthManager(context).getAccessTokenOnce()
                ?: throw Exception("Not authenticated")
            
            // Upload
            val response = ApiClient.apiService.uploadAvatar(
                token = "Bearer $token",
                file = part
            )
            
            response.avatar  // Return relative path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
