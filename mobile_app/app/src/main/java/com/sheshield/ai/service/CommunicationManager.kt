package com.sheshield.ai.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.sheshield.ai.Config
import com.sheshield.ai.data.EmergencyAlertRequest
import com.sheshield.ai.data.local.AppDatabase
import com.sheshield.ai.data.remote.ApiService
import com.sheshield.ai.data.remote.TwilioApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages all outgoing emergency communications including SMS, Calls,
 * and Backend/Police Dashboard notifications.
 */
object CommunicationManager {

    private fun getApiService(context: Context) = ApiService.create(context)
    private val twilioService = TwilioApiService.create()

    fun triggerEmergencyActions(context: Context, location: Location?, triggerType: String) {
        val db = AppDatabase.getDatabase(context)
        val apiService = getApiService(context)
        val scope = CoroutineScope(Dispatchers.IO)

        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryPct = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        } ?: -1f

        val prefs = context.getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "0") ?: "0"

        // 1. Immediate UI Action (Toast)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "🚨 SOS ACTIVATED: Alerting Contacts & Police!", Toast.LENGTH_LONG).show()
        }

        // 2. Notify Police Dashboard (Parallel Background)
        scope.launch {
            try {
                val emergencyPayload = EmergencyAlertRequest(
                    userId = userId,
                    triggerType = triggerType,
                    latitude = (location?.latitude ?: 0.0),
                    longitude = (location?.longitude ?: 0.0),
                    riskScore = 100 // SOS is always high risk
                )
                Log.d("CommManager", "Sending SOS to backend: $emergencyPayload")
                val response = apiService.sendEmergencyAlert(emergencyPayload)
                if (response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Police Dashboard Notified!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("CommManager", "Backend alert failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CommManager", "Backend notification error: ${e.message}")
            }
        }

        // 3. Local Emergency Actions (SMS & Call)
        scope.launch {
            val contacts = db.contactDao().getAll()
            val callNumber = if (contacts.isNotEmpty()) {
                val priorityContact = contacts.find { it.isPriority } ?: contacts.first()
                priorityContact.phoneNumber
            } else {
                Log.w("CommManager", "No emergency contacts configured. Defaulting to 112.")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "No contacts found! Dialing 112 automatically.", Toast.LENGTH_LONG).show()
                }
                "112" // Fallback to standard emergency number
            }

            val locationUrl = location?.let { "https://www.google.com/maps?q=${it.latitude},${it.longitude}" } ?: "Location unknown"
            val message = "🚨 EMERGENCY! SheShield AI detected danger ($triggerType). My live location: $locationUrl"
            
            // PRIORITY: Start the call immediately on the main thread
            Handler(Looper.getMainLooper()).post {
                makeCall(context, callNumber)
            }

            // Secondary: Send SMS based on battery levels
            contacts.forEach { contact ->
                launch {
                    when {
                        batteryPct > 80 -> {
                            sendSMS(context, contact.phoneNumber, message)
                            sendTwilioSMS(contact.phoneNumber, message)
                        }
                        batteryPct in 40f..80f -> {
                            sendTwilioSMS(contact.phoneNumber, message)
                        }
                        else -> {
                            sendTwilioSMS(contact.phoneNumber, "SOS! Critical Battery. Location: $locationUrl")
                        }
                    }
                }
            }
        }
    }

    private fun sendSMS(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("CommManager", "Native SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e("CommManager", "Native SMS failed: ${e.message}")
        }
    }

    private suspend fun sendTwilioSMS(to: String, message: String) {
        try {
            val response = twilioService.sendSms(
                Config.TWILIO_ACCOUNT_SID,
                to,
                Config.TWILIO_FROM_NUMBER,
                message
            )
            if (response.isSuccessful) {
                Log.d("CommManager", "Twilio SMS sent to $to")
            } else {
                Log.e("CommManager", "Twilio SMS failed: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("CommManager", "Twilio SMS exception: ${e.message}")
        }
    }

    private fun makeCall(context: Context, phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            Log.d("CommManager", "Attempting direct call to $phoneNumber")
            context.startActivity(callIntent)
        } catch (e: SecurityException) {
            Log.e("CommManager", "Call permission missing, falling back to dialer")
            try {
                context.startActivity(dialIntent)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Permission missing for direct call. Opening dialer...", Toast.LENGTH_SHORT).show()
                }
            } catch (e2: Exception) {
                Log.e("CommManager", "Dialer fallback failed: ${e2.message}")
            }
        } catch (e: Exception) {
            Log.e("CommManager", "Call failed, falling back to dialer: ${e.message}")
            try {
                context.startActivity(dialIntent)
            } catch (e2: Exception) {
                Log.e("CommManager", "Final call fallback failed")
            }
        }
    }
}
