package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

class ChatLeryyyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        createNotificationChannels()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.fromResource(this) ?: FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAFIhd5Fbyjuw9QJeTbEm8vRXaXSlNm74E")
                    .setApplicationId("1:279744464896:android:b26ecbfb72a4d9520e03a1")
                    .setProjectId("chat-leryyy")
                    .setDatabaseUrl("https://chat-leryyy-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .setStorageBucket("chat-leryyy.firebasestorage.app")
                    .setGcmSenderId("279744464896")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("ChatLeryyyApp", "Firebase initialized with Android configuration")
            }
            // Explicitly ensure FCM auto-init and background sync are disabled on startup
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false
            } catch (e: Throwable) {
                Log.d("ChatLeryyyApp", "FCM auto init configuration skipped: ${e.message}")
            }

            // Enable offline persistence for RTDB if desired
            val db = FirebaseDatabase.getInstance("https://chat-leryyy-default-rtdb.asia-southeast1.firebasedatabase.app")
            try {
                db.setPersistenceEnabled(true)
            } catch (e: Exception) {
                // Ignore if already set
            }
        } catch (e: Exception) {
            Log.e("ChatLeryyyApp", "Failed to init Firebase", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Channel Pesan (Messages)
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Pesan Masuk",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan baru ChatLeryyy"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
            }

            // 2. Channel Panggilan Masuk (Incoming Calls) - High Importance with Ringtone
            val callSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val callAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_INCOMING_CALLS,
                "Panggilan Masuk",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi panggilan suara dan video masuk"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 800, 1000, 800)
                setSound(callSoundUri, callAudioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // 3. Channel Panggilan Tak Terjawab (Missed Calls)
            val missedCallChannel = NotificationChannel(
                CHANNEL_MISSED_CALLS,
                "Panggilan Tak Terjawab",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi panggilan yang terlewat"
            }

            // 4. Channel Panggilan Berlangsung (Ongoing Calls)
            val ongoingCallChannel = NotificationChannel(
                CHANNEL_ONGOING_CALLS,
                "Panggilan Aktif",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status panggilan yang sedang berjalan"
            }

            notificationManager.createNotificationChannels(
                listOf(msgChannel, callChannel, missedCallChannel, ongoingCallChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "chatleryyy_messages"
        const val CHANNEL_INCOMING_CALLS = "chatleryyy_incoming_calls"
        const val CHANNEL_MISSED_CALLS = "chatleryyy_missed_calls"
        const val CHANNEL_ONGOING_CALLS = "chatleryyy_ongoing_calls"
    }
}
