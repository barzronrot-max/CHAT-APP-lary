package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.FirebaseRepository
import com.example.data.model.CallSession
import com.example.ui.components.NotificationPermissionCard
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.call.CallScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.main.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

sealed class Screen {
    object Main : Screen()
    data class Chat(val chatId: String, val peerUid: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private var pendingIntentData: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntentData = intent

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                MainAppHost(
                    activityIntent = pendingIntentData,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode },
                    onConsumeIntent = { pendingIntentData = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentData = intent
    }
}

@Composable
fun MainAppHost(
    activityIntent: Intent?,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onConsumeIntent: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var currentUser by remember { mutableStateOf(FirebaseRepository.auth.currentUser) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    // Listen to Auth State
    LaunchedEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser == null) {
                currentUser = null
                currentScreen = Screen.Main
            } else if (currentUser == null && !FirebaseRepository.isAuthenticating) {
                // Cold-start restore from disk when not actively submitting auth form
                currentUser = auth.currentUser
            }
        }
        FirebaseRepository.auth.addAuthStateListener(listener)
    }

    // Calls state
    val incomingCall by FirebaseRepository.incomingCall.collectAsState()
    val activeCall by FirebaseRepository.activeCall.collectAsState()

    // Notification Permission Check (Android 13+)
    var showNotifCard by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        )
    }
    var notifPermanentlyDenied by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showNotifCard = !isGranted
        if (!isGranted) notifPermanentlyDenied = true
    }

    // Call Audio & Video Permissions
    var pendingCallPeerUid by remember { mutableStateOf<String?>(null) }
    var pendingCallType by remember { mutableStateOf<String?>(null) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = perms[Manifest.permission.CAMERA] == true

        val peer = pendingCallPeerUid
        val type = pendingCallType
        pendingCallPeerUid = null
        pendingCallType = null

        if (audioGranted && peer != null && type != null) {
            if (type == "video" && !cameraGranted) {
                Toast.makeText(context, "Izin kamera diperlukan untuk Video Call", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                FirebaseRepository.startCall(peer, type)
            }
        } else {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk melakukan panggilan", Toast.LENGTH_SHORT).show()
        }
    }

    fun initiateCallWithPermissions(peerUid: String, type: String) {
        val permissions = if (type == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            scope.launch {
                FirebaseRepository.startCall(peerUid, type)
            }
        } else {
            pendingCallPeerUid = peerUid
            pendingCallType = type
            callPermissionLauncher.launch(permissions)
        }
    }

    // Handle deep links and notification intents
    LaunchedEffect(activityIntent, currentUser) {
        val intent = activityIntent ?: return@LaunchedEffect
        val user = currentUser ?: return@LaunchedEffect

        // Check Deep Link
        val data: Uri? = intent.data
        if (data != null && data.scheme == "chatleryyy") {
            if (data.host == "chat") {
                val chatId = data.lastPathSegment ?: ""
                if (chatId.isNotEmpty()) {
                    val participants = chatId.split("_")
                    val peer = participants.firstOrNull { it != user.uid } ?: ""
                    currentScreen = Screen.Chat(chatId, peer)
                }
            } else if (data.host == "call") {
                // Call deep link
            }
            onConsumeIntent()
            return@LaunchedEffect
        }

        // Check Notification Extras
        val action = intent.getStringExtra("action")
        val callId = intent.getStringExtra("callId")
        val chatId = intent.getStringExtra("chatId")
        val senderId = intent.getStringExtra("senderId")

        if (callId != null) {
            if (action == "accept") {
                FirebaseRepository.answerCall(callId)
            } else if (action == "reject") {
                FirebaseRepository.rejectCall(callId)
            }
            onConsumeIntent()
            return@LaunchedEffect
        }

        if (chatId != null && senderId != null) {
            currentScreen = Screen.Chat(chatId, senderId)
            onConsumeIntent()
        }
    }

    // 1. Unauthenticated -> Auth Screen
    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = FirebaseRepository.auth.currentUser
                currentScreen = Screen.Main
            }
        )
        return
    }

    // 2. Active Call or Incoming Call Overlay
    val callToShow: Pair<CallSession, Boolean>? = when {
        incomingCall != null -> Pair(incomingCall!!, true)
        activeCall != null -> Pair(activeCall!!, false)
        else -> null
    }

    if (callToShow != null) {
        CallScreen(
            callSession = callToShow.first,
            isIncoming = callToShow.second,
            onEndCall = {
                // Cleaned up
            }
        )
        return
    }

    // 3. Screen Routing
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val screen = currentScreen) {
                is Screen.Main -> {
                    MainScreen(
                        onOpenChat = { chatId, peerUid ->
                            currentScreen = Screen.Chat(chatId, peerUid)
                        },
                        onStartCall = { peerUid, type ->
                            initiateCallWithPermissions(peerUid, type)
                        },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode,
                        onLogout = {
                            currentUser = null
                            currentScreen = Screen.Main
                        }
                    )
                }
                is Screen.Chat -> {
                    BackHandler {
                        currentScreen = Screen.Main
                    }
                    ChatScreen(
                        chatId = screen.chatId,
                        peerUid = screen.peerUid,
                        onBack = { currentScreen = Screen.Main },
                        onStartVoiceCall = { receiverId ->
                            initiateCallWithPermissions(receiverId, "voice")
                        },
                        onStartVideoCall = { receiverId ->
                            initiateCallWithPermissions(receiverId, "video")
                        }
                    )
                }
            }

            // Notification Permission Educational Card (Android 13+)
            if (showNotifCard && currentScreen is Screen.Main) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    NotificationPermissionCard(
                        isPermanentlyDenied = notifPermanentlyDenied,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onDismiss = { showNotifCard = false }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
