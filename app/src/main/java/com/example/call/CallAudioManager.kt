package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class CallAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun startRingtone() {
        stopRingtone()
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // Vibrate pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 1000, 1000, 1000, 1000)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error playing ringtone", e)
        }
    }

    fun stopRingtone() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error stopping ringtone", e)
        }
    }

    fun enterCallMode(speakerphone: Boolean) {
        stopRingtone()
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(speakerphone)
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error setting call audio mode", e)
        }
    }

    fun setSpeakerphone(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error setting speakerphone", e)
        }
    }

    fun setMicrophoneMute(mute: Boolean) {
        try {
            audioManager.isMicrophoneMute = mute
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error muting mic", e)
        }
    }

    fun exitCallMode() {
        stopRingtone()
        try {
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error resetting audio mode", e)
        }
    }
}
