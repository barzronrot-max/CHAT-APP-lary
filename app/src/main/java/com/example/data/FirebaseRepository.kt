package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.example.data.model.CallHistoryItem
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSummary
import com.example.data.model.ChatWithPeer
import com.example.data.model.LastMessageInfo
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

object FirebaseRepository {

    private const val TAG = "FirebaseRepository"
    private const val RTDB_URL = "https://chat-leryyy-default-rtdb.asia-southeast1.firebasedatabase.app"

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val rtdb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(RTDB_URL) }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallSession?>(null)
    val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private var connectedListener: ValueEventListener? = null
    private var incomingCallListener: ValueEventListener? = null
    private var activeCallListener: ValueEventListener? = null

    val currentUid: String?
        get() = auth.currentUser?.uid

    @Volatile
    var isAuthenticating: Boolean = false
        private set

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                setupPresence(user.uid)
                loadUserProfile(user.uid)
                registerFcmToken(user.uid)
                listenForIncomingCalls(user.uid)
            } else {
                cleanupOnSignOut()
            }
        }
    }

    fun getChatId(uid1: String, uid2: String): String {
        return listOf(uid1, uid2).sorted().joinToString("_")
    }

    // ==========================================
    // AUTHENTICATION
    // ==========================================

    suspend fun login(email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO + NonCancellable) {
        isAuthenticating = true
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("Gagal masuk: Pengguna tidak ditemukan")
            setupPresence(user.uid)
            loadUserProfile(user.uid)
            registerFcmToken(user.uid)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        } finally {
            isAuthenticating = false
        }
    }

    suspend fun register(
        username: String,
        email: String,
        pass: String,
        photoUri: Uri?,
        context: Context
    ): Result<FirebaseUser> = withContext(Dispatchers.IO + NonCancellable) {
        isAuthenticating = true
        var createdUser: FirebaseUser? = null
        try {
            val cleanUsername = username.trim()
            val lower = cleanUsername.lowercase()

            // 1. Check if username is taken
            val usernameSnapshot = rtdb.getReference("usernames").child(lower).get().await()
            if (usernameSnapshot.exists()) {
                throw Exception("Username '$cleanUsername' sudah digunakan oleh pengguna lain.")
            }

            // 2. Create Auth user
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("Gagal membuat akun Firebase")
            createdUser = user
            val uid = user.uid

            // 3. Upload photo if provided
            var photoUrl = ""
            if (photoUri != null) {
                try {
                    photoUrl = uploadImageFile(photoUri, "avatars/$uid/${System.currentTimeMillis()}.jpg", context)
                } catch (imgError: Exception) {
                    Log.w(TAG, "Avatar upload failed, continuing registration: ${imgError.message}")
                }
            }

            // 4. Save user profile to RTDB
            val now = System.currentTimeMillis()
            val profile = UserProfile(
                uid = uid,
                username = cleanUsername,
                usernameLower = lower,
                email = email.trim(),
                photoURL = photoUrl,
                createdAt = now,
                online = true,
                lastSeen = now
            )

            rtdb.getReference("users").child(uid).setValue(profile).await()
            rtdb.getReference("usernames").child(lower).setValue(uid).await()

            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanUsername)
                    .apply { if (photoUrl.isNotEmpty()) setPhotoUri(Uri.parse(photoUrl)) }
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (ignored: Exception) {}

            _currentUserProfile.value = profile
            setupPresence(uid)
            registerFcmToken(uid)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            if (createdUser != null) {
                try {
                    createdUser.delete().await()
                } catch (delEx: Exception) {
                    Log.w(TAG, "Failed to cleanup created user: ${delEx.message}")
                }
            }
            Result.failure(e)
        } finally {
            isAuthenticating = false
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        val uid = currentUid
        if (uid != null) {
            // Set offline before signing out
            rtdb.getReference("presence").child(uid).setValue(
                mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
            )
            rtdb.getReference("users").child(uid).updateChildren(
                mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
            )
        }
        cleanupOnSignOut()
        auth.signOut()
    }

    private fun cleanupOnSignOut() {
        connectedListener?.let { rtdb.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
        incomingCallListener?.let { rtdb.getReference("calls").removeEventListener(it) }
        incomingCallListener = null
        activeCallListener?.let { rtdb.getReference("calls").removeEventListener(it) }
        activeCallListener = null
        _currentUserProfile.value = null
        _incomingCall.value = null
        _activeCall.value = null
    }

    // ==========================================
    // PRESENCE & PROFILE
    // ==========================================

    private fun setupPresence(uid: String) {
        val presenceRef = rtdb.getReference("presence").child(uid)
        val userRef = rtdb.getReference("users").child(uid)
        val connectedRef = rtdb.getReference(".info/connected")

        connectedListener?.let { connectedRef.removeEventListener(it) }

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    presenceRef.onDisconnect().setValue(
                        mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
                    )
                    userRef.child("online").onDisconnect().setValue(false)
                    userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                    presenceRef.setValue(
                        mapOf("state" to "online", "lastChanged" to ServerValue.TIMESTAMP)
                    )
                    userRef.updateChildren(
                        mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    fun loadUserProfile(uid: String) {
        rtdb.getReference("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(UserProfile::class.java)
                if (profile != null) {
                    _currentUserProfile.value = profile
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun getUserProfileOnce(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val snapshot = rtdb.getReference("users").child(uid).get().await()
            snapshot.getValue(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val ref = rtdb.getReference("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(UserProfile::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun registerFcmToken(uid: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        val deviceId = Build.MODEL.replace(Regex("[^a-zA-Z0-9_]"), "_")
                        val tokenData = mapOf(
                            "token" to token,
                            "device" to deviceId,
                            "platform" to "android",
                            "updatedAt" to ServerValue.TIMESTAMP
                        )
                        rtdb.getReference("fcmTokens").child(uid).child(deviceId).setValue(tokenData)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "FCM token registration skipped or failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseMessaging not available: ${e.message}")
        }
    }

    // ==========================================
    // USER SEARCH
    // ==========================================

    suspend fun searchUsers(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        val myUid = currentUid ?: ""
        try {
            val snapshot = rtdb.getReference("users")
                .orderByChild("usernameLower")
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limitToFirst(20)
                .get()
                .await()

            val list = mutableListOf<UserProfile>()
            for (child in snapshot.children) {
                val user = child.getValue(UserProfile::class.java)
                if (user != null && user.uid != myUid) {
                    list.add(user)
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            emptyList()
        }
    }

    // ==========================================
    // CHAT LIST
    // ==========================================

    fun observeChatList(): Flow<List<ChatWithPeer>> = callbackFlow {
        val myUid = currentUid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val userChatsRef = rtdb.getReference("userChats").child(myUid)
        val chatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatIds = snapshot.children.mapNotNull { it.key }
                if (chatIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                // Fetch details for each chat
                CoroutineScope(Dispatchers.IO).launch {
                    val list = mutableListOf<ChatWithPeer>()
                    for (chatId in chatIds) {
                        try {
                            val chatSnap = rtdb.getReference("chats").child(chatId).get().await()
                            val chat = chatSnap.getValue(ChatSummary::class.java)?.copy(chatId = chatId)
                            if (chat != null) {
                                val peerUid = chat.participants.keys.firstOrNull { it != myUid }
                                if (peerUid != null) {
                                    val peer = getUserProfileOnce(peerUid) ?: UserProfile(uid = peerUid, username = "User")
                                    
                                    // Count unread
                                    val msgsSnap = rtdb.getReference("chats").child(chatId).child("messages")
                                        .orderByChild("receiverId").equalTo(myUid)
                                        .limitToLast(50)
                                        .get().await()
                                    var unread = 0
                                    for (mChild in msgsSnap.children) {
                                        val seen = mChild.child("seen").getValue(Boolean::class.java) ?: false
                                        val deleted = mChild.child("deleted").getValue(Boolean::class.java) ?: false
                                        val deletedForMe = mChild.child("deletedFor").child(myUid).getValue(Boolean::class.java) ?: false
                                        if (!seen && !deleted && !deletedForMe) {
                                            unread++
                                        }
                                    }

                                    // Check if peer is typing
                                    val typingSnap = rtdb.getReference("typing").child(chatId).child(peerUid).get().await()
                                    val isTyping = typingSnap.getValue(Boolean::class.java) ?: false

                                    list.add(ChatWithPeer(chat, peer, unread, isTyping))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing chat $chatId", e)
                        }
                    }
                    // Sort by newest activity
                    list.sortByDescending { it.chat.updatedAt }
                    trySend(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        userChatsRef.addValueEventListener(chatListener)
        awaitClose { userChatsRef.removeEventListener(chatListener) }
    }

    // ==========================================
    // MESSAGING
    // ==========================================

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val ref = rtdb.getReference("chats").child(chatId).child("messages").limitToLast(200)
        val myUid = currentUid ?: ""
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val msg = child.getValue(ChatMessage::class.java)
                    if (msg != null) {
                        val deletedForMe = msg.deletedFor?.get(myUid) == true
                        if (!deletedForMe) {
                            list.add(msg)
                        }
                    }
                }
                list.sortBy { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(
        chatId: String,
        receiverId: String,
        text: String,
        type: String = "text",
        replyToMsg: ChatMessage? = null,
        imageUri: Uri? = null,
        fileUri: Uri? = null,
        fileName: String? = null,
        fileSize: Long = 0L,
        context: Context? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val myUid = currentUid ?: throw Exception("Belum login")
            val msgRef = rtdb.getReference("chats").child(chatId).child("messages").push()
            val msgId = msgRef.key ?: UUID.randomUUID().toString()

            var finalImageUrl: String? = null
            var finalFileUrl: String? = null

            if (imageUri != null && context != null) {
                finalImageUrl = uploadImageFile(imageUri, "chatImages/$chatId/$msgId.jpg", context)
            }
            if (fileUri != null && context != null) {
                finalFileUrl = uploadRawFile(fileUri, "chatFiles/$chatId/$msgId/${fileName ?: "file"}", context)
            }

            val now = System.currentTimeMillis()
            val message = ChatMessage(
                id = msgId,
                senderId = myUid,
                receiverId = receiverId,
                text = text.trim(),
                type = type,
                timestamp = now,
                seen = false,
                delivered = false,
                replyTo = replyToMsg?.id,
                replyText = replyToMsg?.let {
                    if (it.type == "image") "📷 Foto" else if (it.type == "file") "📎 ${it.fileName ?: "File"}" else it.text
                },
                replyToName = replyToMsg?.let {
                    if (it.senderId == myUid) "Anda" else (_currentUserProfile.value?.username ?: "Pengguna")
                },
                imageUrl = finalImageUrl,
                fileUrl = finalFileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            // Save message
            msgRef.setValue(message).await()

            // Update chat summary
            val lastPreview = when (type) {
                "image" -> "📷 Foto"
                "file" -> "📎 ${fileName ?: "File"}"
                else -> text.trim()
            }
            val lastMessage = LastMessageInfo(
                id = msgId,
                senderId = myUid,
                receiverId = receiverId,
                text = lastPreview,
                type = type,
                timestamp = now,
                deleted = false
            )

            val chatUpdate = mapOf(
                "participants" to mapOf(myUid to true, receiverId to true),
                "lastMessage" to lastMessage,
                "updatedAt" to now
            )
            rtdb.getReference("chats").child(chatId).updateChildren(chatUpdate).await()

            // Connect userChats
            rtdb.getReference("userChats").child(myUid).child(chatId).setValue(true)
            rtdb.getReference("userChats").child(receiverId).child(chatId).setValue(true)

            // Clear typing
            setTyping(chatId, false)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Send message error", e)
            Result.failure(e)
        }
    }

    suspend fun editMessage(chatId: String, msgId: String, newText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf<String, Any>(
                "text" to newText.trim(),
                "edited" to true,
                "editedAt" to ServerValue.TIMESTAMP
            )
            rtdb.getReference("chats").child(chatId).child("messages").child(msgId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chatId: String, msgId: String, forEveryone: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val myUid = currentUid ?: return@withContext Result.failure(Exception("Not logged in"))
            val msgRef = rtdb.getReference("chats").child(chatId).child("messages").child(msgId)

            if (forEveryone) {
                val updates = mapOf<String, Any?>(
                    "deleted" to true,
                    "text" to "",
                    "imageUrl" to null,
                    "fileUrl" to null,
                    "deletedAt" to ServerValue.TIMESTAMP
                )
                msgRef.updateChildren(updates).await()
            } else {
                msgRef.child("deletedFor").child(myUid).setValue(true).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun markSeen(chatId: String) {
        val myUid = currentUid ?: return
        val ref = rtdb.getReference("chats").child(chatId).child("messages")
        ref.orderByChild("receiverId").equalTo(myUid).limitToLast(50).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updates = mutableMapOf<String, Any>()
                for (child in snapshot.children) {
                    val seen = child.child("seen").getValue(Boolean::class.java) ?: false
                    if (!seen) {
                        val key = child.key ?: continue
                        updates["$key/seen"] = true
                        updates["$key/seenAt"] = ServerValue.TIMESTAMP
                    }
                }
                if (updates.isNotEmpty()) {
                    ref.updateChildren(updates)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        val myUid = currentUid ?: return
        val typingRef = rtdb.getReference("typing").child(chatId).child(myUid)
        if (isTyping) {
            typingRef.setValue(true)
            typingRef.onDisconnect().removeValue()
        } else {
            typingRef.removeValue()
        }
    }

    fun observeTyping(chatId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = rtdb.getReference("typing").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Boolean>()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    map[uid] = child.getValue(Boolean::class.java) ?: false
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ==========================================
    // CALLS (VOICE & VIDEO)
    // ==========================================

    private fun listenForIncomingCalls(myUid: String) {
        val callsRef = rtdb.getReference("calls")
        incomingCallListener?.let { callsRef.removeEventListener(it) }

        incomingCallListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var found: CallSession? = null
                for (child in snapshot.children) {
                    val call = child.getValue(CallSession::class.java)
                    if (call != null && call.receiverId == myUid && call.status == "ringing") {
                        found = call
                        break
                    }
                }
                _incomingCall.value = found
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        callsRef.addValueEventListener(incomingCallListener!!)
    }

    suspend fun startCall(receiverId: String, type: String): Result<CallSession> = withContext(Dispatchers.IO) {
        try {
            val myUid = currentUid ?: throw Exception("Belum login")
            val myProfile = _currentUserProfile.value ?: getUserProfileOnce(myUid)
            val callRef = rtdb.getReference("calls").push()
            val callId = callRef.key ?: UUID.randomUUID().toString()

            val call = CallSession(
                callId = callId,
                callerId = myUid,
                callerName = myProfile?.username ?: "Pengguna",
                callerPhoto = myProfile?.photoURL ?: "",
                receiverId = receiverId,
                type = type,
                status = "ringing",
                channelId = "call_${callId}_${System.currentTimeMillis()}",
                createdAt = System.currentTimeMillis()
            )

            callRef.setValue(call).await()
            _activeCall.value = call
            listenActiveCall(callId)

            Result.success(call)
        } catch (e: Exception) {
            Log.e(TAG, "Start call error", e)
            Result.failure(e)
        }
    }

    fun answerCall(callId: String) {
        val callRef = rtdb.getReference("calls").child(callId)
        val now = System.currentTimeMillis()
        callRef.updateChildren(
            mapOf("status" to "connected", "answeredAt" to now)
        )
        _incomingCall.value = null
        rtdb.getReference("calls").child(callId).get().addOnSuccessListener { snap ->
            snap.getValue(CallSession::class.java)?.let {
                _activeCall.value = it.copy(status = "connected")
                listenActiveCall(callId)
            }
        }
    }

    fun rejectCall(callId: String) {
        val call = _incomingCall.value
        rtdb.getReference("calls").child(callId).updateChildren(
            mapOf("status" to "rejected", "endedAt" to System.currentTimeMillis())
        )
        _incomingCall.value = null

        // Save rejected to call history
        if (call != null) {
            saveCallHistory(call.copy(status = "rejected", endedAt = System.currentTimeMillis()))
        }
    }

    fun endCall(callId: String) {
        val call = _activeCall.value
        val now = System.currentTimeMillis()
        rtdb.getReference("calls").child(callId).updateChildren(
            mapOf("status" to "ended", "endedAt" to now)
        )
        _activeCall.value = null
        activeCallListener?.let { rtdb.getReference("calls").child(callId).removeEventListener(it) }
        activeCallListener = null

        if (call != null) {
            saveCallHistory(call.copy(status = "ended", endedAt = now))
        }
    }

    private fun listenActiveCall(callId: String) {
        val callRef = rtdb.getReference("calls").child(callId)
        activeCallListener?.let { callRef.removeEventListener(it) }
        activeCallListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val call = snapshot.getValue(CallSession::class.java)
                if (call == null || call.status in listOf("ended", "rejected", "cancelled")) {
                    _activeCall.value = null
                    callRef.removeEventListener(this)
                    activeCallListener = null
                } else {
                    _activeCall.value = call
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        callRef.addValueEventListener(activeCallListener!!)
    }

    private fun saveCallHistory(call: CallSession) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val duration = if (call.answeredAt > 0L && call.endedAt > call.answeredAt) {
                    (call.endedAt - call.answeredAt) / 1000
                } else 0L

                val callerPeer = getUserProfileOnce(call.receiverId)
                val receiverPeer = getUserProfileOnce(call.callerId)

                // For Caller
                val callerHistory = CallHistoryItem(
                    id = UUID.randomUUID().toString(),
                    callId = call.callId,
                    peerId = call.receiverId,
                    peerName = callerPeer?.username ?: "Pengguna",
                    peerPhoto = callerPeer?.photoURL ?: "",
                    isOutgoing = true,
                    type = call.type,
                    status = call.status,
                    timestamp = call.createdAt,
                    durationSeconds = duration
                )
                rtdb.getReference("callHistory").child(call.callerId).child(call.callId).setValue(callerHistory)

                // For Receiver
                val receiverHistory = CallHistoryItem(
                    id = UUID.randomUUID().toString(),
                    callId = call.callId,
                    peerId = call.callerId,
                    peerName = call.callerName,
                    peerPhoto = call.callerPhoto,
                    isOutgoing = false,
                    type = call.type,
                    status = call.status,
                    timestamp = call.createdAt,
                    durationSeconds = duration
                )
                rtdb.getReference("callHistory").child(call.receiverId).child(call.callId).setValue(receiverHistory)
            } catch (e: Exception) {
                Log.e(TAG, "Save call history error", e)
            }
        }
    }

    fun observeCallHistory(): Flow<List<CallHistoryItem>> = callbackFlow {
        val myUid = currentUid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = rtdb.getReference("callHistory").child(myUid).limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CallHistoryItem>()
                for (child in snapshot.children) {
                    val item = child.getValue(CallHistoryItem::class.java)
                    if (item != null) list.add(item)
                }
                list.sortByDescending { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ==========================================
    // PROFILE UPDATE
    // ==========================================

    suspend fun updateProfile(newUsername: String, newPhotoUri: Uri?, context: Context): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val myUid = currentUid ?: throw Exception("Belum login")
            val cleanName = newUsername.trim()
            val lower = cleanName.lowercase()
            val current = _currentUserProfile.value ?: getUserProfileOnce(myUid) ?: throw Exception("Profil tidak ditemukan")

            // Check username if changed
            if (lower != current.usernameLower) {
                val taken = rtdb.getReference("usernames").child(lower).get().await()
                if (taken.exists() && taken.getValue(String::class.java) != myUid) {
                    throw Exception("Username '$cleanName' sudah dipakai.")
                }
                rtdb.getReference("usernames").child(current.usernameLower).removeValue()
                rtdb.getReference("usernames").child(lower).setValue(myUid)
            }

            var photoUrl = current.photoURL
            if (newPhotoUri != null) {
                photoUrl = uploadImageFile(newPhotoUri, "avatars/$myUid/${System.currentTimeMillis()}.jpg", context)
            }

            val updated = current.copy(
                username = cleanName,
                usernameLower = lower,
                photoURL = photoUrl
            )

            rtdb.getReference("users").child(myUid).setValue(updated).await()
            _currentUserProfile.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // FILE & IMAGE UPLOAD HELPERS
    // ==========================================

    private suspend fun uploadImageFile(uri: Uri, storagePath: String, context: Context): String = withContext(Dispatchers.IO) {
        // Try Firebase Storage first
        try {
            val ref = storage.reference.child(storagePath)
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            return@withContext downloadUrl
        } catch (e: Exception) {
            Log.w(TAG, "Storage upload failed, falling back to base64: ${e.message}")
        }

        // Fallback to compressed Base64 Data URL so user is never blocked
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap != null) {
            val maxDimension = 640
            var width = originalBitmap.width
            var height = originalBitmap.height
            if (width > height && width > maxDimension) {
                height = (height * maxDimension) / width
                width = maxDimension
            } else if (height > maxDimension) {
                width = (width * maxDimension) / height
                height = maxDimension
            }
            val scaled = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val bytes = outputStream.toByteArray()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return@withContext "data:image/jpeg;base64,$b64"
        }
        throw Exception("Gagal membaca gambar")
    }

    private suspend fun uploadRawFile(uri: Uri, storagePath: String, context: Context): String = withContext(Dispatchers.IO) {
        try {
            val ref = storage.reference.child(storagePath)
            ref.putFile(uri).await()
            return@withContext ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Raw file upload failed: ${e.message}")
            throw Exception("Gagal mengunggah file: ${e.message}")
        }
    }

    fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }
}
