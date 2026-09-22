package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ChatLeryyyApp
import com.example.MainActivity
import com.example.R
import com.example.data.FirebaseRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatLeryyyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")
        FirebaseRepository.currentUid?.let { uid ->
            FirebaseRepository.registerFcmToken(uid)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        val notifType = data["type"] ?: "message"
        val title = remoteMessage.notification?.title ?: data["title"] ?: "ChatLeryyy"
        val body = remoteMessage.notification?.body ?: data["body"] ?: "Pesan baru"

        if (notifType == "call") {
            showIncomingCallNotification(data, title, body)
        } else {
            showMessageNotification(data, title, body)
        }
    }

    private fun showMessageNotification(data: Map<String, String>, title: String, body: String) {
        val chatId = data["chatId"]
        val senderId = data["senderId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", chatId)
            putExtra("senderId", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (chatId?.hashCode() ?: System.currentTimeMillis().toInt()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ChatLeryyyApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((chatId?.hashCode() ?: 1001), notification)
    }

    private fun showIncomingCallNotification(data: Map<String, String>, title: String, body: String) {
        val callId = data["callId"] ?: return
        val callerName = data["callerName"] ?: "Pengguna"
        val callType = data["callType"] ?: "voice"

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("incomingCall", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Accept
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("action", "accept")
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode() + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Reject
        val rejectIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("action", "reject")
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode() + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notification = NotificationCompat.Builder(this, ChatLeryyyApp.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (callType == "video") "📹 Video Call Masuk" else "📞 Panggilan Masuk")
            .setContentText("$callerName sedang menelepon Anda")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setSound(soundUri)
            .setAutoCancel(true)
            .addAction(0, "Tolak", rejectPendingIntent)
            .addAction(0, if (callType == "video") "Terima" else "Angkat", acceptPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(callId.hashCode(), notification)
    }

    companion object {
        private const val TAG = "ChatLeryyyFCM"
    }
}
