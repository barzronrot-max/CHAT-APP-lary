package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.FirebaseRepository
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DarkBubbleMe
import com.example.ui.theme.DarkBubbleOther
import com.example.ui.theme.DeliveredTickGrey
import com.example.ui.theme.LightBubbleMe
import com.example.ui.theme.LightBubbleOther
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.SeenTickBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    peerUid: String,
    onBack: () -> Unit,
    onStartVoiceCall: (receiverId: String) -> Unit,
    onStartVideoCall: (receiverId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = FirebaseRepository.currentUid ?: ""

    // Observe peer profile (online, lastSeen, photo)
    val peerProfile by FirebaseRepository.observeUserProfile(peerUid).collectAsState(initial = null)
    val messages by FirebaseRepository.observeMessages(chatId).collectAsState(initial = emptyList())
    val typingMap by FirebaseRepository.observeTyping(chatId).collectAsState(initial = emptyMap())
    val isPeerTyping = typingMap[peerUid] == true

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var isSending by remember { mutableStateOf(false) }

    // Dialogs & context menu
    var activeMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var editedText by remember { mutableStateOf("") }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Mark messages as seen when entering chat or receiving new messages
    LaunchedEffect(chatId, messages.size) {
        FirebaseRepository.markSeen(chatId)
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Cleanup typing when leaving chat
    DisposableEffect(chatId) {
        onDispose {
            FirebaseRepository.setTyping(chatId, false)
        }
    }

    // Attachment pickers
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            selectedFileUri = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedImageUri = null
            val pair = FirebaseRepository.getFileNameAndSize(context, uri)
            selectedFileName = pair.first
            selectedFileSize = pair.second
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Could show profile */ }
                    ) {
                        UserAvatar(
                            photoUrl = peerProfile?.photoURL,
                            name = peerProfile?.username ?: "Pengguna",
                            size = 38.dp,
                            showOnlineDot = true,
                            isOnline = peerProfile?.online == true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = peerProfile?.username ?: "Memuat...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statusText = when {
                                isPeerTyping -> "sedang mengetik..."
                                peerProfile?.online == true -> "Online"
                                (peerProfile?.lastSeen ?: 0L) > 0L -> {
                                    val timeSpan = DateUtils.getRelativeTimeSpanString(
                                        peerProfile!!.lastSeen,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    )
                                    "Terakhir dilihat $timeSpan"
                                }
                                else -> "Offline"
                            }
                            Text(
                                text = statusText,
                                fontSize = 11.5.sp,
                                color = if (isPeerTyping || peerProfile?.online == true) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    // Voice Call Button
                    IconButton(onClick = { onStartVoiceCall(peerUid) }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Panggilan Suara",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Video Call Button
                    IconButton(onClick = { onStartVideoCall(peerUid) }) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Panggilan Video",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages List
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada pesan. Kirim sapaan pertama!",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        var previousTimestamp = 0L

                        items(messages, key = { it.id }) { msg ->
                            // Date header if day changes
                            val showDateHeader = !isSameDay(previousTimestamp, msg.timestamp)
                            previousTimestamp = msg.timestamp

                            if (showDateHeader && msg.timestamp > 0) {
                                DateHeader(timestamp = msg.timestamp)
                            }

                            MessageBubble(
                                message = msg,
                                isMe = msg.senderId == myUid,
                                onLongClick = { activeMenuMessage = msg },
                                onImageClick = { url -> fullScreenImageUrl = url },
                                onFileClick = { fileUrl ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Tidak dapat membuka file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Peer typing banner at bottom of messages
            AnimatedVisibility(visible = isPeerTyping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${peerProfile?.username ?: "Pengguna"} sedang mengetik...",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Reply Preview Bar
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (reply.senderId == myUid) "Membalas diri sendiri" else "Membalas ${peerProfile?.username ?: "Pengguna"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (reply.type == "image") "📷 Foto" else if (reply.type == "file") "📎 ${reply.fileName}" else reply.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Attachment Preview Bar (Image or Document)
            AnimatedVisibility(visible = selectedImageUri != null || selectedFileUri != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Preview Gambar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Foto siap dikirim",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (selectedFileUri != null) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFileName ?: "Dokumen",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(selectedFileSize),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedImageUri = null
                                selectedFileUri = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus Lampiran")
                        }
                    }
                }
            }

            // Input Composer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachments launcher
                var showAttachMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Lampiran",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kirim Foto") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                imagePicker.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kirim Dokumen") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                filePicker.launch("*/*")
                            }
                        )
                    }
                }

                // Text Input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        FirebaseRepository.setTyping(chatId, it.isNotBlank())
                    },
                    placeholder = { Text("Ketik pesan...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Send Button
                val canSend = (inputText.isNotBlank() || selectedImageUri != null || selectedFileUri != null) && !isSending

                IconButton(
                    onClick = {
                        if (!canSend) return@IconButton
                        isSending = true
                        val textToSend = inputText
                        val reply = replyingTo
                        val img = selectedImageUri
                        val file = selectedFileUri
                        val fName = selectedFileName
                        val fSize = selectedFileSize

                        // Reset input immediately
                        inputText = ""
                        replyingTo = null
                        selectedImageUri = null
                        selectedFileUri = null
                        selectedFileName = null

                        scope.launch {
                            val msgType = when {
                                img != null -> "image"
                                file != null -> "file"
                                else -> "text"
                            }
                            FirebaseRepository.sendMessage(
                                chatId = chatId,
                                receiverId = peerUid,
                                text = textToSend,
                                type = msgType,
                                replyToMsg = reply,
                                imageUri = img,
                                fileUri = file,
                                fileName = fName,
                                fileSize = fSize,
                                context = context
                            )
                            isSending = false
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Message Long-press Context Menu Dialog
    activeMenuMessage?.let { msg ->
        val isMyMessage = msg.senderId == myUid
        val isDeleted = msg.deleted

        AlertDialog(
            onDismissRequest = { activeMenuMessage = null },
            title = { Text("Opsi Pesan") },
            text = {
                Column {
                    if (!isDeleted) {
                        // Reply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    replyingTo = msg
                                    activeMenuMessage = null
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("Balas pesan", fontSize = 15.sp)
                        }

                        // Copy Text
                        if (msg.type == "text" && msg.text.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Pesan", msg.text))
                                        Toast.makeText(context, "Pesan disalin", Toast.LENGTH_SHORT).show()
                                        activeMenuMessage = null
                                    }
                                    .padding(vertical = 12.dp)
                        ) {
                            Text("Salin teks", fontSize = 15.sp)
                        }
                        }

                        // Edit (Only for own text message)
                        if (isMyMessage && msg.type == "text") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageToEdit = msg
                                        editedText = msg.text
                                        activeMenuMessage = null
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text("Edit pesan", fontSize = 15.sp)
                            }
                        }
                    }

                    // Delete for Me
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    FirebaseRepository.deleteMessage(chatId, msg.id, forEveryone = false)
                                }
                                activeMenuMessage = null
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text("Hapus untuk saya", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    }

                    // Delete for Everyone (Only for own message)
                    if (isMyMessage && !isDeleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        FirebaseRepository.deleteMessage(chatId, msg.id, forEveryone = true)
                                    }
                                    activeMenuMessage = null
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("Hapus untuk semua orang", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeMenuMessage = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Edit Message Dialog
    messageToEdit?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToEdit = null },
            title = { Text("Edit Pesan") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    label = { Text("Pesan") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedText.isNotBlank()) {
                            scope.launch {
                                FirebaseRepository.editMessage(chatId, msg.id, editedText)
                                messageToEdit = null
                            }
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToEdit = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Full Screen Image Viewer
    fullScreenImageUrl?.let { url ->
        Dialog(onDismissRequest = { fullScreenImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Gambar Penuh",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onFileClick: (String) -> Unit
) {
    val bubbleColor = if (isMe) {
        if (androidx.compose.foundation.isSystemInDarkTheme()) DarkBubbleMe else LightBubbleMe
    } else {
        if (androidx.compose.foundation.isSystemInDarkTheme()) DarkBubbleOther else LightBubbleOther
    }
    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Deleted State
                if (message.deleted) {
                    Text(
                        text = "🚫 Pesan telah dihapus",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 13.5.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    // Reply Quote preview
                    if (!message.replyTo.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMe) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp)
                        ) {
                            Column {
                                Text(
                                    text = message.replyToName ?: "Pesan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isMe) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyText ?: "",
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Image Content
                    if (message.type == "image" && !message.imageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Gambar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(message.imageUrl) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // File Content
                    if (message.type == "file" && !message.fileUrl.isNullOrEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMe) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onFileClick(message.fileUrl) }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Dokumen",
                                tint = if (isMe) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: "File",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor
                                )
                                Text(
                                    text = formatFileSize(message.fileSize),
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Text Content
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Timestamp & Ticks Footer
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.edited && !message.deleted) {
                        Text(
                            text = "(diedit) ",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    }

                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = if (message.timestamp > 0) timeFormat.format(Date(message.timestamp)) else "",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    // Read receipt ticks for my message
                    if (isMe && !message.deleted) {
                        Spacer(modifier = Modifier.width(3.dp))
                        when {
                            message.seen -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Dibaca",
                                    tint = SeenTickBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            message.delivered -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Terkirim",
                                    tint = DeliveredTickGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terkirim ke server",
                                    tint = DeliveredTickGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(timestamp: Long) {
    val dateText = remember(timestamp) {
        val now = System.currentTimeMillis()
        when {
            DateUtils.isToday(timestamp) -> "Hari ini"
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Kemarin"
            else -> SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(timestamp))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = dateText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    if (t1 == 0L || t2 == 0L) return false
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return fmt.format(Date(t1)) == fmt.format(Date(t2))
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    }
}
