package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Background Foreground Service that keeps NECXA running in the background,
 * maintaining an active wake-word listening loop for "Nexa" / "NECXA" (like Siri).
 */
class NecxaVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRecognitionRunning = false

    companion object {
        const val CHANNEL_ID = "necxa_voice_channel"
        const val NOTIFICATION_ID = 101

        private val _wakeWordEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val wakeWordEvents: SharedFlow<String> = _wakeWordEvents

        fun startService(context: Context) {
            val intent = Intent(context, NecxaVoiceService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NecxaVoiceService::class.java)
            context.stopService(intent)
        }

        fun notifyWakeWordDetected(command: String = "Nexa") {
            _wakeWordEvents.tryEmit(command)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWakeWordListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWakeWordListening()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWakeWordListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) return@post
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            isRecognitionRunning = false
                            scheduleRestartListening(1000)
                        }

                        override fun onResults(results: Bundle?) {
                            processHeardText(results)
                            isRecognitionRunning = false
                            scheduleRestartListening(500)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            processHeardText(partialResults)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                launchRecognition()
            } catch (e: Exception) {
                scheduleRestartListening(2000)
            }
        }
    }

    private fun launchRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speechRecognizer?.startListening(intent)
            isRecognitionRunning = true
        } catch (e: Exception) {
            scheduleRestartListening(1500)
        }
    }

    private fun processHeardText(bundle: Bundle?) {
        if (bundle == null) return
        val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in matches) {
            val lower = text.lowercase()
            if (lower.contains("nexa") || lower.contains("necxa") || lower.contains("hey nexa") || lower.contains("nexa suno")) {
                notifyWakeWordDetected(text)
                // Bring app / assistant to front if in background
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(openIntent)
                break
            }
        }
    }

    private fun scheduleRestartListening(delayMillis: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isRecognitionRunning) {
                launchRecognition()
            }
        }, delayMillis)
    }

    private fun stopWakeWordListening() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRecognitionRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NECXA Voice Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NECXA background voice and wake-word service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NECXA")
            .setContentText("Active & listening for wake-word \"Nexa\"...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
