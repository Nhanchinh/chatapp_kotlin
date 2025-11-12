package com.example.chatapp.ui.qrcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import com.example.chatapp.data.local.AuthManager
import com.example.chatapp.data.remote.ApiClient
import com.example.chatapp.data.remote.model.UserDto
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(
    navController: NavController? = null,
    onUserScanned: (UserDto) -> Unit = {}
) {
    var scannedUserId by remember { mutableStateOf<String?>(null) }
    var scannedUser by remember { mutableStateOf<UserDto?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    var invited by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // QR Scanner launcher
    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            scannedUserId = result.contents
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Quét mã QR để kết bạn")
                setCameraId(0)
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setCaptureActivity(PortraitCaptureActivity::class.java)
            }
            barcodeLauncher.launch(options)
        } else {
            error = "Cần quyền camera để quét mã QR"
        }
    }

    val launchScanner: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Quét mã QR để kết bạn")
                setCameraId(0)
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setCaptureActivity(PortraitCaptureActivity::class.java)
            }
            barcodeLauncher.launch(options)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Load user info when scannedUserId changes
    LaunchedEffect(scannedUserId) {
        val userIdToLoad = scannedUserId ?: return@LaunchedEffect
        isLoading = true
        error = null
        try {
            val auth = AuthManager(context)
            val token = auth.getValidAccessToken() ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn")
            val user = ApiClient.apiService.getUserById("Bearer $token", userIdToLoad)
            scannedUser = user
            isLoading = false
        } catch (e: Exception) {
            error = "Không tìm thấy người dùng: ${e.message}"
            isLoading = false
        }
    }

    // Handle add friend
    val handleAddFriend: () -> Unit = {
        val userId = scannedUser?.id
        if (userId != null) {
            isAdding = true
            scope.launch {
                try {
                    val auth = AuthManager(context)
                    val token = auth.getValidAccessToken() ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn")
                    ApiClient.apiService.sendFriendRequest("Bearer $token", userId)
                    invited = true
                    isAdding = false
                } catch (e: Exception) {
                    error = "Không thể gửi lời mời: ${e.message}"
                    isAdding = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quét mã QR") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val currentScannedUser = scannedUser
            val currentError = error
            if (currentScannedUser == null && currentError == null && !isLoading) {
                // Show scan button
                Text(
                    text = "Nhấn nút bên dưới để quét mã QR",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
                
                Button(
                    onClick = launchScanner,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Quét mã QR", modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            if (isLoading) {
                CircularProgressIndicator()
                Text("Đang tải thông tin...")
            }

            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Button(
                    onClick = {
                        error = null
                        scannedUser = null
                        scannedUserId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Thử lại")
                }
            }

            val currentScannedUserForDisplay = scannedUser
            if (currentScannedUserForDisplay != null) {
                // Show user info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color(0xFF90CAF9)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (currentScannedUserForDisplay.fullName ?: currentScannedUserForDisplay.email ?: "?")
                                            .firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Text(
                            text = currentScannedUserForDisplay.fullName ?: currentScannedUserForDisplay.email ?: "Unknown",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        currentScannedUserForDisplay.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }

                        currentScannedUserForDisplay.friendCount?.let { friendCount ->
                            Text(
                                text = "$friendCount bạn bè",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add friend button
                        Button(
                            onClick = handleAddFriend,
                            enabled = !isAdding && !invited,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isAdding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                            } else if (invited) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Đã gửi lời mời")
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Thêm bạn bè")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        scannedUser = null
                        scannedUserId = null
                        error = null
                        invited = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Quét lại")
                }
            }
        }
    }
}

