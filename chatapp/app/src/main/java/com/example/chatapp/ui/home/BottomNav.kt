package com.example.chatapp.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomNav(selected: HomeTab, onSelected: (HomeTab) -> Unit, friendRequestsCount: Int = 0, unreadNotificationsCount: Int = 0) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == HomeTab.CHATS,
            onClick = { onSelected(HomeTab.CHATS) },
            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
            label = { Text("Đoạn chat") }
        )
        NavigationBarItem(
            selected = selected == HomeTab.CONTACTS,
            onClick = { onSelected(HomeTab.CONTACTS) },
            icon = {
                Box {
                    Icon(Icons.Default.Contacts, contentDescription = null)
                    if (friendRequestsCount > 0) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 12.dp, y = (-4).dp),
                            containerColor = Color(0xFFFF6B6B)
                        ) {
                            Text(
                                text = if (friendRequestsCount > 99) "99+" else friendRequestsCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            },
            label = { Text("Danh bạ") }
        )
        NavigationBarItem(
            selected = selected == HomeTab.NOTIFICATIONS,
            onClick = { onSelected(HomeTab.NOTIFICATIONS) },
            icon = {
                Box {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    if (unreadNotificationsCount > 0) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 12.dp, y = (-4).dp),
                            containerColor = Color(0xFFFF6B6B)
                        ) {
                            Text(
                                text = if (unreadNotificationsCount > 99) "99+" else unreadNotificationsCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            },
            label = { Text("Thông báo") }
        )
        NavigationBarItem(
            selected = selected == HomeTab.MENU,
            onClick = { onSelected(HomeTab.MENU) },
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("Menu") }
        )
    }
}


