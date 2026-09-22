package com.example.ui.screens.main

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FirebaseRepository
import com.example.data.model.CallHistoryItem
import com.example.data.model.ChatWithPeer
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenChat: (chatId: String, peerUid: String) -> Unit,
    onStartCall: (peerUid: String, type: String) -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onLogout: () -> Unit
) {
    val myProfile by FirebaseRepository.currentUserProfile.collectAsState()
    val chatList by FirebaseRepository.observeChatList().collectAsState(initial = emptyList())
    val callHistory by FirebaseRepository.observeCallHistory().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Obrolan, 1 = Panggilan

    var showSearchDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ChatLeryyy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                actions = {
                    // Dark Mode Toggle
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Ganti Tema"
                        )
                    }
                    // Search User
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari Pengguna"
                        )
                    }
                    // Profile Avatar
                    IconButton(onClick = { showProfileDialog = true }) {
                        UserAvatar(
                            photoUrl = myProfile?.photoURL,
                            name = myProfile?.username ?: "Saya",
                            size = 32.dp
                        )
                    }
                    // Logout
                    IconButton(onClick = { showLogoutConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Keluar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        val totalUnread = chatList.sumOf { it.unreadCount }
                        if (totalUnread > 0) {
                            androidx.compose.material3.BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (totalUnread > 99) "99+" else totalUnread.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "Obrolan")
                            }
                        } else {
                            Icon(Icons.Default.Chat, contentDescription = "Obrolan")
                        }
                    },
                    label = { Text("Obrolan") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Phone, contentDescription = "Panggilan") },
                    label = { Text("Panggilan") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showSearchDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Mulai Chat")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (selectedTab == 0) {
                // Chat List Tab
                if (chatList.isEmpty()) {
                    EmptyChatsView(onSearchClick = { showSearchDialog = true })
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chatList, key = { it.chat.chatId }) { item ->
                            ChatItemRow(
                                item = item,
                                onClick = { onOpenChat(item.chat.chatId, item.peer.uid) }
                            )
                        }
                    }
                }
            } else {
                // Call History Tab
                if (callHistory.isEmpty()) {
                    EmptyCallHistoryView()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(callHistory, key = { it.callId }) { callItem ->
                            CallHistoryRow(
                                item = callItem,
                                onCallClick = {
                                    onStartCall(callItem.peerId, callItem.type)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Search User Dialog
    if (showSearchDialog) {
        SearchUserDialog(
            onDismiss = { showSearchDialog = false },
            onSelectUser = { user ->
                showSearchDialog = false
                val myUid = FirebaseRepository.currentUid ?: ""
                val chatId = FirebaseRepository.getChatId(myUid, user.uid)
                onOpenChat(chatId, user.uid)
            }
        )
    }

    // Profile Dialog
    if (showProfileDialog && myProfile != null) {
        ProfileDialog(
            userProfile = myProfile!!,
            onDismiss = { showProfileDialog = false }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Konfirmasi Keluar") },
            text = { Text("Apakah Anda yakin ingin keluar dari akun ChatLeryyy?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        FirebaseRepository.logout()
                        onLogout()
                    }
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun ChatItemRow(
    item: ChatWithPeer,
    onClick: () -> Unit
) {
    val peer = item.peer
    val lastMsg = item.chat.lastMessage
    val myUid = FirebaseRepository.currentUid

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                photoUrl = peer.photoURL,
                name = peer.username,
                size = 50.dp,
                showOnlineDot = true,
                isOnline = peer.online
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = peer.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val timeFormatted = remember(item.chat.updatedAt) {
                        if (item.chat.updatedAt > 0) {
                            if (DateUtils.isToday(item.chat.updatedAt)) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.chat.updatedAt))
                            } else {
                                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(item.chat.updatedAt))
                            }
                        } else ""
                    }
                    Text(
                        text = timeFormatted,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isTyping) {
                        Text(
                            text = "sedang mengetik...",
                            color = OnlineGreen,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    } else {
                        val prefix = if (lastMsg?.senderId == myUid) "Anda: " else ""
                        val preview = when {
                            lastMsg == null -> "Mulai obrolan"
                            lastMsg.deleted -> "🚫 Pesan telah dihapus"
                            lastMsg.type == "image" -> "${prefix}📷 Foto"
                            lastMsg.type == "file" -> "${prefix}📎 File"
                            else -> "$prefix${lastMsg.text}"
                        }
                        Text(
                            text = preview,
                            fontSize = 13.sp,
                            color = if (item.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (item.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryRow(
    item: CallHistoryItem,
    onCallClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                photoUrl = item.peerPhoto,
                name = item.peerName,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.peerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, iconTint, label) = when {
                        item.status == "rejected" -> Triple(Icons.Default.CallMissed, DangerRed, "Ditolak")
                        item.status == "missed" -> Triple(Icons.Default.CallMissed, DangerRed, "Tidak terjawab")
                        item.isOutgoing -> Triple(Icons.Default.CallMade, OnlineGreen, "Panggilan keluar")
                        else -> Triple(Icons.Default.CallReceived, MaterialTheme.colorScheme.primary, "Panggilan masuk")
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    val timeStr = if (item.timestamp > 0) {
                        SimpleDateFormat("d MMM, HH:mm", Locale("id", "ID")).format(Date(item.timestamp))
                    } else ""

                    val durationStr = if (item.durationSeconds > 0) " (${item.durationSeconds} dtk)" else ""

                    Text(
                        text = "$label · $timeStr$durationStr",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = if (item.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Telepon Kembali",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyChatsView(onSearchClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Belum Ada Obrolan",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cari teman dengan username mereka untuk memulai percakapan realtime.",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSearchClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cari Pengguna")
            }
        }
    }
}

@Composable
fun EmptyCallHistoryView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Belum Ada Riwayat Panggilan",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Panggilan suara dan video Anda akan muncul di sini.",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SearchUserDialog(
    onDismiss: () -> Unit,
    onSelectUser: (UserProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cari Pengguna") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.trim().isNotBlank()) {
                            isSearching = true
                            scope.launch {
                                results = FirebaseRepository.searchUsers(it)
                                isSearching = false
                            }
                        } else {
                            results = emptyList()
                        }
                    },
                    placeholder = { Text("Ketik username...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (results.isEmpty() && query.trim().isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada pengguna ditemukan", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(results, key = { it.uid }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectUser(user) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    photoUrl = user.photoURL,
                                    name = user.username,
                                    size = 40.dp,
                                    showOnlineDot = true,
                                    isOnline = user.online
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = if (user.online) "🟢 Online" else "Offline",
                                        fontSize = 11.5.sp,
                                        color = if (user.online) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun ProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(userProfile.username) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil Saya") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with Click to Change
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Foto Profil Baru",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        UserAvatar(
                            photoUrl = userProfile.photoURL,
                            name = userProfile.username,
                            size = 90.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Ubah Foto",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = userProfile.email,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = null },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.trim().length < 3) {
                        errorMsg = "Username minimal 3 karakter"
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        FirebaseRepository.updateProfile(username, selectedPhotoUri, context)
                            .onSuccess {
                                isSaving = false
                                onDismiss()
                            }
                            .onFailure {
                                isSaving = false
                                errorMsg = it.message ?: "Gagal memperbarui profil"
                            }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Simpan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
