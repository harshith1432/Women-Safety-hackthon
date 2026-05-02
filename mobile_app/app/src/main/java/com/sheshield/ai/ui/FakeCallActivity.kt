package com.sheshield.ai.ui

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sheshield.ai.databinding.ActivityFakeCallBinding

class FakeCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeCallBinding
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start playing default ringtone
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, notification)
        ringtone?.play()

        binding.acceptButton.setOnClickListener {
            stopCall()
        }

        binding.declineButton.setOnClickListener {
            stopCall()
        }
    }

    private fun stopCall() {
        ringtone?.stop()
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        super.onDestroy()
    }
}
