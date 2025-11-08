package com.example.chatapp.ui.qrcode

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapp.data.local.AuthManager
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import android.content.Intent
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRCodeScreen(
    navController: androidx.navigation.NavController? = null
) {
    val context = LocalContext.current
    val auth = remember { AuthManager(context) }
    val userIdFlow = auth.userId
    val userIdState by userIdFlow.collectAsStateWithLifecycle(initialValue = null)
    var userFullName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Get user info if needed
        try {
            val token = auth.getAccessTokenOnce()
            if (token != null) {
                val bearer = "Bearer $token"
                val profile = com.example.chatapp.data.remote.ApiClient.apiService.getProfile(bearer)
                userFullName = profile.fullName
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(userIdState) {
        userIdState?.let { userId ->
            qrBitmap = withContext(Dispatchers.Default) {
                generateQRCode(userId, 512)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mã QR của tôi") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val currentBitmapForShare = qrBitmap
                    val currentUserIdForShare = userIdState
                    if (currentBitmapForShare != null && currentUserIdForShare != null) {
                        IconButton(
                            onClick = {
                                // Share QR code or user ID
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Mã ID của tôi: $currentUserIdForShare")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ mã QR"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Quét mã QR này để kết bạn với tôi",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                val currentBitmap = qrBitmap
                if (currentBitmap != null) {
                    Card(
                        modifier = Modifier
                            .size(300.dp)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }

                val currentUserFullName = userFullName
                if (currentUserFullName != null) {
                    Text(
                        text = currentUserFullName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                val currentUserIdState = userIdState
                if (currentUserIdState != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Mã ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentUserIdState,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = "Người khác có thể quét mã QR này để gửi lời mời kết bạn",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

fun generateQRCode(text: String, size: Int): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.MARGIN, 1)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

