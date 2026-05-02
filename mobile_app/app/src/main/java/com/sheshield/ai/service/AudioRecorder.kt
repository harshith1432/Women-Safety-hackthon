package com.sheshield.ai.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording() {
        val fileName = "${context.externalCacheDir?.absolutePath}/emergency_rec_${System.currentTimeMillis()}.mp3"
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileName)
            try {
                prepare()
                start()
                Log.d("AudioRecorder", "Recording started: $fileName")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Recording failed", e)
            }
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Stop recording failed", e)
        }
        mediaRecorder = null
    }
}
