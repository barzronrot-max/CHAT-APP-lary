package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val email: String = "",
    val photoURL: String = "",
    val createdAt: Long = 0L,
    val online: Boolean = false,
    val lastSeen: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val type: String = "text", // "text", "image", "file"
    val timestamp: Long = 0L,
    val seen: Boolean = false,
    val seenAt: Long = 0L,
    val delivered: Boolean = false,
    val edited: Boolean = false,
    val editedAt: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L,
    val replyTo: String? = null,
    val replyText: String? = null,
    val replyToName: String? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val deletedFor: Map<String, Boolean>? = null
)

@Keep
@IgnoreExtraProperties
data class LastMessageInfo(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val type: String = "text",
    val timestamp: Long = 0L,
    val deleted: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class ChatSummary(
    val chatId: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val lastMessage: LastMessageInfo? = null,
    val updatedAt: Long = 0L
)

data class ChatWithPeer(
    val chat: ChatSummary,
    val peer: UserProfile,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhoto: String = "",
    val receiverId: String = "",
    val type: String = "voice", // "voice" or "video"
    val status: String = "ringing", // "ringing", "accepted", "connected", "rejected", "cancelled", "ended", "missed"
    val channelId: String = "",
    val createdAt: Long = 0L,
    val answeredAt: Long = 0L,
    val endedAt: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class CallHistoryItem(
    val id: String = "",
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerPhoto: String = "",
    val isOutgoing: Boolean = false,
    val type: String = "voice", // "voice", "video"
    val status: String = "ended", // "ended", "missed", "rejected"
    val timestamp: Long = 0L,
    val durationSeconds: Long = 0L
)
