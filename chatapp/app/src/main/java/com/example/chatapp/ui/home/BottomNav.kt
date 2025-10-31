package com.example.chatapp.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BottomNav(selected: HomeTab, onSelected: (HomeTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == HomeTab.CHATS,
            onClick = { onSelected(HomeTab.CHATS) },
            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
            label = { Text("Đoạn chat") }
        )
        NavigationBarItem(
            selected = selected == HomeTab.NEWS,
            onClick = { onSelected(HomeTab.NEWS) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Tin") }
        )
        NavigationBarItem(
            selected = selected == HomeTab.MENU,
            onClick = { onSelected(HomeTab.MENU) },
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("Menu") }
        )
    }
}


