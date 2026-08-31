package com.example.memory.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.memory.MainActivity

class VoiceCaptureService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListening = false

    companion object {
        private const val TAG = "VoiceCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_capture_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        initializeSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Activation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listening for Memory Capture wake word"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Memory Voice Activation")
            .setContentText("Listening for 'Memory Capture'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Built-in icon for now
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
            
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Prefer offline recognition if available (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
                }
            }
        } else {
            Log.e(TAG, "Speech recognition not available")
        }
    }

    private fun startListening() {
        if (!isListening && speechRecognizer != null) {
            isListening = true
            speechRecognizer?.startListening(recognizerIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        // Speech ended, we need to restart listening after a brief delay
        isListening = false
        startListening()
    }

    override fun onError(error: Int) {
        isListening = false
        // Ignore expected errors like no speech or network issues, just restart
        startListening()
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null) {
            for (match in matches) {
                if (match.contains("memory capture", ignoreCase = true) || match.contains("capture memory", ignoreCase = true)) {
                    triggerCapture()
                    break
                }
            }
        }
        startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // Can optionally trigger on partial results for faster response
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun triggerCapture() {
        Log.i(TAG, "Wake word detected! Triggering capture.")
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trigger_capture", true)
        }
        startActivity(launchIntent)
    }
}
