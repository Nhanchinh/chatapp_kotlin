package com.example.chatapp.ui.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.chatapp.data.remote.model.GroupInfoResponse
import com.example.chatapp.data.remote.model.MemberDto
import com.example.chatapp.ui.navigation.NavRoutes
import com.example.chatapp.viewmodel.ChatViewModel

@Composable
fun MemberRow(
    member: MemberDto,
    isGroupOwner: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (member.avatarUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(member.avatarUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF90CAF9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (member.fullName ?: member.email ?: member.userId).take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = member.fullName ?: member.email ?: member.userId,
                    fontWeight = FontWeight.Medium
                )
                if (isGroupOwner) {
                    Text(
                        text = "Chủ nhóm",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Remove",
                    tint = Color(0xFFE91E63)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversationId: String,
    groupName: String?,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    navController: NavController? = null
) {
    var groupInfo by remember { mutableStateOf<GroupInfoResponse?>(null) }
    var showConfirmLeave by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showAddMembersSheet by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    
    val friendsList by chatViewModel.friendsList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(conversationId) {
        chatViewModel.fetchGroupInfo(conversationId) {
            groupInfo = it
        }
    }

    val info = groupInfo
    val myId by chatViewModel.myUserId.collectAsStateWithLifecycle()
    val isOwner = info?.let { myId == it.ownerId } ?: false
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thông tin nhóm",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {}
            )
        }
    ) { paddingValues ->
        if (info == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Profile section - Group header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF90CAF9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (groupName ?: info.name ?: "Nhóm").firstOrNull()?.uppercase() ?: "G",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = groupName ?: info.name ?: "Nhóm",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Encrypted badge
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Được mã hóa đầu cuối",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Actions section
            item {
                Divider()
                
                // Media files
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController?.navigate(
                                NavRoutes.MediaGallery.createRoute(
                                    conversationId,
                                    groupName ?: info.name ?: "Nhóm"
                                )
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Xem file phương tiện, file và liên kết",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Tất cả",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                Divider()
            }
            
            // Search in conversation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tìm kiếm trong cuộc trò chuyện",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Divider()
            }
            
            // View members (replacing "Personal page")
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showMembersSheet = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Xem thành viên",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${info.participants.size} thành viên",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                Divider()
            }
            
            // Add members (owner only)
            if (isOwner) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddMembersSheet = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Thêm thành viên",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    Divider()
                }
            }
            
            // Leave or Delete group
            item {
                if (isOwner) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showConfirmDelete = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Xóa nhóm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFE91E63)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showConfirmLeave = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Rời nhóm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFE91E63)
                        )
                    }
                }
                
                Divider()
            }
            
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Members bottom sheet
    if (showMembersSheet && info != null) {
        ModalBottomSheet(
            onDismissRequest = { showMembersSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Thành viên (${info.participants.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(info.participants) { member ->
                        MemberRow(
                            member = member,
                            isGroupOwner = member.userId == info.ownerId,
                            canRemove = isOwner && member.userId != info.ownerId && member.userId != myId,
                            onRemove = { memberToRemove = member.userId }
                        )
                    }
                }
            }
        }
    }

    // Confirm leave dialog
    if (showConfirmLeave) {
        AlertDialog(
            onDismissRequest = { showConfirmLeave = false },
            title = { Text("Rời nhóm") },
            text = { Text("Bạn chắc chắn muốn rời khỏi nhóm?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmLeave = false
                        chatViewModel.leaveGroup(conversationId) {
                            onBack()
                        }
                    }
                ) {
                    Text("Rời", color = Color(0xFFE91E63))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmLeave = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Confirm delete dialog
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showConfirmDelete = false
                    deleteError = null
                }
            },
            title = { Text("Xóa nhóm") },
            text = {
                Column {
                    Text("Chỉ chủ nhóm có thể xóa nhóm. Bạn chắc chắn muốn xóa?")
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = deleteError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isDeleting) {
                            isDeleting = true
                            deleteError = null
                            chatViewModel.deleteConversation(
                                conversationId = conversationId,
                                onSuccess = {
                                    showConfirmDelete = false
                                    isDeleting = false
                                    onBack()
                                },
                                onError = { error ->
                                    deleteError = error
                                    isDeleting = false
                                }
                            )
                        }
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Xóa", color = Color(0xFFE91E63))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        deleteError = null
                    },
                    enabled = !isDeleting
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    // Remove member confirmation
    memberToRemove?.let { memberId ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Xóa thành viên") },
            text = { Text("Bạn muốn xóa thành viên này khỏi nhóm?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.removeGroupMember(conversationId, memberId) { updatedInfo ->
                            groupInfo = updatedInfo
                        }
                        memberToRemove = null
                    }
                ) {
                    Text("Xóa", color = Color(0xFFE91E63))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Add members bottom sheet
    if (showAddMembersSheet && info != null) {
        val existingMemberIds = info.participants.map { it.userId }.toSet()
        val availableFriends = friendsList.filter { friend ->
            friend.id != null && friend.id !in existingMemberIds
        }
        
        val selectedMembers = remember { mutableStateListOf<String>() }
        var isAddingMembers by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        
        val filteredFriends = remember(availableFriends, searchQuery) {
            if (searchQuery.isBlank()) {
                availableFriends
            } else {
                availableFriends.filter { friend ->
                    val name = friend.fullName ?: friend.email ?: ""
                    name.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        
        ModalBottomSheet(
            onDismissRequest = { 
                showAddMembersSheet = false
                selectedMembers.clear()
                searchQuery = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Thêm thành viên", style = MaterialTheme.typography.headlineSmall)
                
                if (availableFriends.isEmpty()) {
                    Text(
                        "Không còn bạn bè nào để thêm vào nhóm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tìm kiếm bạn bè...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667EEA),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    
                    if (filteredFriends.isEmpty()) {
                        Text(
                            "Không tìm thấy bạn bè nào",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(filteredFriends) { friend ->
                            val friendId = friend.id ?: return@items
                            val friendName = friend.fullName ?: friend.email ?: "Unknown"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedMembers.contains(friendId)) {
                                            selectedMembers.remove(friendId)
                                        } else {
                                            selectedMembers.add(friendId)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedMembers.contains(friendId),
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            selectedMembers.add(friendId)
                                        } else {
                                            selectedMembers.remove(friendId)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(friendName)
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            if (selectedMembers.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Vui lòng chọn ít nhất một thành viên",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            isAddingMembers = true
                            val addCount = selectedMembers.size // Save count before clearing
                            chatViewModel.addGroupMembers(
                                conversationId = conversationId,
                                memberIds = selectedMembers.toList(),
                                onSuccess = { updatedInfo ->
                                    isAddingMembers = false
                                    groupInfo = updatedInfo
                                    showAddMembersSheet = false
                                    selectedMembers.clear()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Đã thêm $addCount thành viên",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { errorMsg ->
                                    isAddingMembers = false
                                    android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = selectedMembers.isNotEmpty() && !isAddingMembers,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
                    ) {
                        if (isAddingMembers) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Thêm vào nhóm")
                        }
                    }
                }
            }
        }
    }

}}
