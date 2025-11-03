package com.example.chatapp.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.navigation.NavController
import com.example.chatapp.ui.common.KeyboardDismissWrapper
import com.example.chatapp.ui.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController? = null, onLogout: () -> Unit) {
    val chats = listOf(
        Triple("Quang Nguyễn", "Bạn: v chịu", "16:34"),
        Triple("Nguyễn Đăng Nam", "😄😄😄", "11:09"),
        Triple("Tối ưu phần mềm di động", "Nghĩa đã bày tỏ cảm xúc", "20:48"),
        Triple("Trần T.Anh", "oke oke", "17:38"),
        Triple("NHÓM NÀY HỌC", "Trọng: Giải gì đâu...", "16:52")
    )
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        bottomBar = { BottomNav(selected = selectedTab, onSelected = { selectedTab = it }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (selectedTab) {
                HomeTab.CHATS -> {
                    KeyboardDismissWrapper(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search TextField (only in CHATS)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                placeholder = { Text("Tìm kiếm") },
                                singleLine = true,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.LightGray,
                                    focusedContainerColor = Color.LightGray,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent
                                )
                            )

                            // Stories row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val names = listOf("Bạn", "Trung", "Quyền", "Gia Bảo", "Minh")
                                names.forEachIndexed { index, n ->
                                    StoryAvatar(
                                        name = n, 
                                        online = index % 2 == 0,
                                        onClick = {
                                            navController?.navigate("chat/$n")
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(
                                    chats.filter { it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true) }
                                ) { (name, message, time) ->
                                    ConversationItem(
                                        name = name,
                                        lastMessage = message,
                                        time = time,
                                        isOnline = name.hashCode() % 2 == 0,
                                        onClick = {
                                            navController?.navigate("chat/$name")
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(72.dp)) }
                            }
                        }
                    }
                }
                HomeTab.NEWS -> {
                    NewsScreen()
                }
                HomeTab.MENU -> {
                    MenuScreen(navController = navController, onLogout = onLogout)
                }
            }
        }
    }
}


