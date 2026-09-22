package com.example.ui.screens.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.CallAudioManager
import com.example.data.FirebaseRepository
import com.example.data.model.CallSession
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    callSession: CallSession,
    isIncoming: Boolean,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { CallAudioManager(context) }

    var isMicMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(callSession.type == "video") }
    var isCameraOn by remember { mutableStateOf(callSession.type == "video") }
    var isFrontCamera by remember { mutableStateOf(true) }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    // Start ringtone if incoming and ringing
    LaunchedEffect(isIncoming, callSession.status) {
        if (isIncoming && callSession.status == "ringing") {
            audioManager.startRingtone()
        } else if (callSession.status == "connected" || callSession.status == "accepted") {
            audioManager.enterCallMode(isSpeakerOn)
        }
    }

    // Call duration timer when connected
    LaunchedEffect(callSession.status) {
        if (callSession.status == "connected") {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Clean up audio manager when leaving call screen
    DisposableEffect(Unit) {
        onDispose {
            audioManager.exitCallMode()
        }
    }

    val isConnected = callSession.status == "connected"

    // Background gradient
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFF0B0F19)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(24.dp)
    ) {
        // Upper Content: Caller/Peer info & Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val peerName = if (isIncoming) callSession.callerName else "Menghubungkan..."
            val peerPhoto = if (isIncoming) callSession.callerPhoto else ""

            // Ring Animation for Incoming Call
            if (callSession.status == "ringing") {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .border(3.dp, if (callSession.type == "video") MaterialTheme.colorScheme.primary else OnlineGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(
                        photoUrl = peerPhoto,
                        name = peerName,
                        size = 110.dp
                    )
                }
            } else {
                UserAvatar(
                    photoUrl = peerPhoto,
                    name = peerName,
                    size = 120.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = peerName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            val statusLabel = when (callSession.status) {
                "ringing" -> if (isIncoming) {
                    if (callSession.type == "video") "Panggilan video masuk..." else "Panggilan suara masuk..."
                } else {
                    "Memanggil..."
                }
                "connected" -> formatCallTimer(elapsedSeconds)
                "accepted" -> "Menghubungkan suara..."
                "rejected" -> "Panggilan ditolak"
                "ended" -> "Panggilan berakhir"
                else -> callSession.status
            }

            Text(
                text = statusLabel,
                fontSize = 15.sp,
                color = if (isConnected) OnlineGreen else Color.White.copy(alpha = 0.75f)
            )
        }

        // Lower Content: Control Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isIncoming && callSession.status == "ringing") {
                // Incoming Call: [TOLAK] and [TERIMA]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reject Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                audioManager.stopRingtone()
                                FirebaseRepository.rejectCall(callSession.callId)
                                onEndCall()
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Tolak Panggilan",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Tolak", color = Color.White, fontSize = 13.sp)
                    }

                    // Accept Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                audioManager.stopRingtone()
                                FirebaseRepository.answerCall(callSession.callId)
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(OnlineGreen)
                        ) {
                            Icon(
                                imageVector = if (callSession.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Terima Panggilan",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Terima", color = Color.White, fontSize = 13.sp)
                    }
                }
            } else {
                // Active Call Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Microphone
                    IconButton(
                        onClick = {
                            isMicMuted = !isMicMuted
                            audioManager.setMicrophoneMute(isMicMuted)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isMicMuted) Color.White else Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Nyalakan Mikrofon" else "Matikan Mikrofon",
                            tint = if (isMicMuted) Color.Black else Color.White
                        )
                    }

                    // Speakerphone
                    IconButton(
                        onClick = {
                            isSpeakerOn = !isSpeakerOn
                            audioManager.setSpeakerphone(isSpeakerOn)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn) Color.Black else Color.White
                        )
                    }

                    // Camera On/Off (Only for video calls)
                    if (callSession.type == "video") {
                        IconButton(
                            onClick = { isCameraOn = !isCameraOn },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isCameraOn) Color.White.copy(alpha = 0.2f) else Color.White)
                        ) {
                            Icon(
                                imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Kamera",
                                tint = if (isCameraOn) Color.White else Color.Black
                            )
                        }

                        // Switch Camera
                        IconButton(
                            onClick = { isFrontCamera = !isFrontCamera },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Ganti Kamera",
                                tint = Color.White
                            )
                        }
                    }

                    // End Call (Red Button)
                    IconButton(
                        onClick = {
                            FirebaseRepository.endCall(callSession.callId)
                            audioManager.exitCallMode()
                            onEndCall()
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Akhiri Panggilan",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatCallTimer(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
