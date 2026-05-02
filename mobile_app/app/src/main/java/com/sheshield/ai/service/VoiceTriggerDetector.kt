package com.sheshield.ai.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class VoiceTriggerDetector(
    private val context: Context,
    private val onTrigger: (String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val keywords = listOf("help me", "save me", "call mom", "emergency", "i am in danger", "please help")
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var lastTriggerTime: Long = 0
    private val TRIGGER_COOLDOWN = 10000L 
    private val CONFIDENCE_THRESHOLD = 0.75f
    
    private var triggerCount = 0
    private var lastTriggerResetTime: Long = 0
    private val TRIGGER_RESET_INTERVAL = 60000L // 1 minute to reset trigger count

    fun startListening() {
        handler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(this)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Ensure it doesn't time out too quickly
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("VoiceTrigger", "Started listening with confidence monitoring...")
            } catch (e: Exception) {
                Log.e("VoiceTrigger", "Start listening failed: ${e.message}")
                restartListeningWithDelay(2000)
            }
        }
    }

    fun stopListening() {
        handler.post {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        
        matches?.forEachIndexed { index, text ->
            val confidence = confidences?.getOrNull(index) ?: 0f
            val lowerText = text.lowercase()
            
            Log.d("VoiceTrigger", "Result: \"$lowerText\" | Confidence: $confidence")
            
            if (confidence >= CONFIDENCE_THRESHOLD) {
                val detectedKeywords = keywords.filter { lowerText.contains(it) }
                if (detectedKeywords.isNotEmpty()) {
                    val isMultiple = detectedKeywords.size > 1
                    handleDetection(text, isMultiple)
                    return@forEachIndexed // Trigger once per result set
                }
            }
        }
        restartListeningWithDelay(500)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.forEach { text ->
            Log.v("VoiceTrigger", "Partial: ${text.lowercase()}")
        }
    }

    private fun handleDetection(text: String, isMultiple: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime > TRIGGER_COOLDOWN) {
            lastTriggerTime = now
            
            if (now - lastTriggerResetTime > TRIGGER_RESET_INTERVAL) {
                triggerCount = 0
            }
            lastTriggerResetTime = now
            triggerCount++

            val triggerInfo = when {
                isMultiple -> "CRITICAL_VOICE_TRIGGER"
                triggerCount >= 2 -> "REPEATED_VOICE_TRIGGER"
                else -> "SINGLE_VOICE_TRIGGER"
            }
            
            Log.i("VoiceTrigger", "TRIGGER CONFIRMED: $triggerInfo (Text: $text)")
            onTrigger(triggerInfo)
        }
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        Log.e("VoiceTrigger", "Error: $error ($message)")
        
        // Restart on most errors to keep protection active
        if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            restartListeningWithDelay(1500)
        } else {
            // If busy, reset and restart
            stopListening()
            restartListeningWithDelay(2000)
        }
    }

    private fun restartListeningWithDelay(delayMillis: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (isListening) startListening() }, delayMillis)
    }

    override fun onReadyForSpeech(params: Bundle?) { Log.d("VoiceTrigger", "Ready for speech") }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
