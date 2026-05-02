package com.sheshield.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.sheshield.ai.Config
import com.sheshield.ai.R
import com.sheshield.ai.data.*
import com.sheshield.ai.data.local.AppDatabase
import com.sheshield.ai.data.remote.ApiService
import com.sheshield.ai.ui.MainActivity
import com.sheshield.ai.utils.AppLifecycleTracker
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.math.sqrt

class SafetyService : Service(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private lateinit var database: AppDatabase
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var voiceTriggerDetector: VoiceTriggerDetector
    private lateinit var syncManager: SyncManager
    private val apiService by lazy { ApiService.create(this) }

    private var lastLocation: Location? = null
    private var lastSpeed: Float = 0f
    
    // Shake Detection Settings (Tuned for 5-7 seconds requirement)
    private var shakeThreshold = 25.0f // Reduced sensitivity (Higher threshold = Less sensitive)
    private var shakeStartTime: Long = 0
    private var lastShakePeakTime: Long = 0
    private val MIN_SHAKE_DURATION = 6000L // Must shake for 6 seconds (range 5-7s)
    private val SHAKE_TIMEOUT = 1200L // Reset if shaking stops for more than 1.2 seconds
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    private var lastRiskScore: Int = 0
    private var lastRiskLevel: String = "Safe"
    private var lastNotificationUpdateTime: Long = 0
    private var lastEmergencyTriggerTime: Long = 0
    private val EMERGENCY_COOLDOWN = 60000L // 1 minute cooldown between automated SOS actions

    // AI Prediction Flags
    private var suddenStopDetected = false
    private var shakeDetected = false
    private var voiceTriggered = false
    private var manualSosTriggered = false
    private var lastBatteryLevel = -1
    
    private var isDeviceOnline = false
    private var routeDeviationDetected = false
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isDeviceOnline = true
            notifyStatusToBackend("online")
        }
        override fun onLost(network: Network) {
            isDeviceOnline = false
            notifyStatusToBackend("offline")
        }
    }

    companion object {
        const val CHANNEL_ID = "SafetyServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_TRIGGER_SOS = "com.sheshield.ai.TRIGGER_SOS"
        const val ACTION_RISK_UPDATE = "com.sheshield.ai.RISK_UPDATE"
        const val ACTION_ROUTE_DEVIATION = "com.sheshield.ai.ROUTE_DEVIATION"
        const val EXTRA_RISK_SCORE = "risk_score"
        const val EXTRA_RISK_LEVEL = "risk_level"
        const val BACKGROUND_NOTIFICATION_INTERVAL = 30 * 60 * 1000L // 30 minutes
        const val EMERGENCY_CHANNEL_ID = "EmergencyChannel"
        const val EMERGENCY_NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SafetyService", "Safety Service Starting...")
        createNotificationChannel()
        val notification = createNotification("SheShield AI Protection Active")
        startForeground(NOTIFICATION_ID, notification)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        database = AppDatabase.getDatabase(this)
        audioRecorder = audioRecorderInstance()
        syncManager = SyncManager(this)
        
        val prefs = getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
        ApiService.authToken = prefs.getString("access_token", null)
        
        voiceTriggerDetector = VoiceTriggerDetector(this) { triggerInfo ->
            Log.d("SafetyService", "VOICE TRIGGER DETECTED: $triggerInfo")
            voiceTriggered = true
            
            // Increment risk based on trigger type
            when (triggerInfo) {
                "CRITICAL_VOICE_TRIGGER" -> {
                    lastRiskScore = 100
                    triggerEmergency("Voice SOS: Critical Keywords Detected")
                }
                "REPEATED_VOICE_TRIGGER" -> {
                    lastRiskScore = (lastRiskScore + 40).coerceAtMost(95)
                }
                "SINGLE_VOICE_TRIGGER" -> {
                    lastRiskScore = (lastRiskScore + 20).coerceAtMost(80)
                }
            }
            
            // Immediate UI feedback
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "VOICE TRIGGER: Risk Increased!", Toast.LENGTH_SHORT).show()
                broadcastRiskUpdate(lastRiskScore, lastRiskLevel)
            }
            
            requestImmediateRiskPrediction()
        }

        setupLocationUpdates()
        setupSensors()
        voiceTriggerDetector.startListening()
        startPeriodicSync()
        setupOnlineDetection()
        
        // Register receivers
        registerReceiver(stopServiceReceiver, IntentFilter("com.sheshield.ai.ACTION_STOP_SERVICE"))
        registerReceiver(dataUpdateReceiver, IntentFilter(ACTION_ROUTE_DEVIATION))
        
        Toast.makeText(this, "Safety AI Engine Activated", Toast.LENGTH_SHORT).show()
    }

    private val stopServiceReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.w("SafetyService", "Stop service broadcast received - shutting down")
            stopSelf()
        }
    }

    private val dataUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ROUTE_DEVIATION) {
                val isDeviation = intent.getBooleanExtra("is_deviation", false)
                if (isDeviation) {
                    routeDeviationDetected = true
                    lastRiskScore = (lastRiskScore + 30).coerceAtMost(90) // Deviation adds +30 risk
                    Log.w("SafetyService", "Route Deviation Signal Received. Updated Risk: $lastRiskScore")
                    broadcastRiskUpdate(lastRiskScore, lastRiskLevel)
                    requestImmediateRiskPrediction()
                }
            }
        }
    }

    private fun audioRecorderInstance(): AudioRecorder = AudioRecorder(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_SOS) {
            Log.d("SafetyService", "Manual SOS Intent Received")
            manualSosTriggered = true
            // Bulletproof: Trigger emergency actions immediately locally
            triggerEmergency("Manual SOS")
            requestImmediateRiskPrediction()
        }
        return START_STICKY
    }

    private fun setupOnlineDetection() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun notifyStatusToBackend(status: String) {
        serviceScope.launch {
            try {
                if (status == "online") {
                    apiService.deviceOnline(StatusRequest(status = "ONLINE"))
                } else {
                    apiService.deviceOffline()
                }
            } catch (e: Exception) {
                Log.e("SafetyService", "Failed to update status: ${e.message}")
            }
        }
    }

    private fun startPeriodicSync() {
        syncJob = serviceScope.launch {
            while (isActive) {
                if (ApiService.authToken != null) {
                    syncManager.checkAndSync()
                }
                delay(Config.SYNC_INTERVAL_MS)
            }
        }
    }

    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Update battery level immediately on every location update
                    val battery = getBatteryLevel()
                    lastBatteryLevel = battery
                    Log.d("SafetyService", "Location Update: Battery=$battery%, Speed=${location.speed}")

                    detectSuddenStop(location)
                    lastLocation = location
                    processSafetyCheckAI(location)
                    
                    if (isDeviceOnline && ApiService.authToken != null) {
                        syncLiveLocation(location)
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (unlikely: SecurityException) {
            Log.e("SafetyService", "Location permission missing in Service")
        }
    }

    private fun syncLiveLocation(location: Location) {
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "0") ?: "0"
                val nightTravelMode = prefs.getBoolean("night_travel_mode", false)
                val locData = LocationSyncRequest(
                    userId = userId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = location.speed,
                    movementStatus = if (location.speed > 0.5) "MOVING" else "STATIONARY",
                    riskScore = lastRiskScore,
                    batteryLevel = getBatteryLevel(),
                    isNight = isSystemNightTime() || nightTravelMode,
                    nightTravelMode = nightTravelMode,
                    shakeDetected = shakeDetected,
                    manualSos = manualSosTriggered,
                    voiceTrigger = voiceTriggered,
                    suddenStop = suddenStopDetected
                )
                apiService.saveLocation(locData)
            } catch (e: Exception) {
                Log.e("SafetyService", "Live location sync failed: ${e.message}")
            }
        }
    }

    private fun detectSuddenStop(newLocation: Location) {
        if (lastLocation != null && newLocation.hasSpeed()) {
            val speedKmh = newLocation.speed * 3.6f
            if (lastSpeed > 30f && speedKmh < 5f) {
                suddenStopDetected = true
                Log.w("SafetyService", "Sudden stop detected!")
            }
            lastSpeed = speedKmh
        }
    }

    private fun setupSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            Log.d("SafetyService", "Accelerometer Registered")
        }
    }

    private fun requestImmediateRiskPrediction() {
        if (manualSosTriggered || shakeDetected || voiceTriggered) {
            triggerEmergency(when {
                manualSosTriggered -> "Manual SOS"
                shakeDetected -> "Shake Detected"
                voiceTriggered -> "Voice Trigger"
                else -> "Emergency"
            })
        }

        lastLocation?.let { processSafetyCheckAI(it) } ?: run {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { 
                        lastLocation = it
                        processSafetyCheckAI(it)
                    }
                }
            } catch (e: SecurityException) {}
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val current = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                null
            }
        } ?: if (lastBatteryLevel != -1) lastBatteryLevel else 100 // Fallback to 100 only if never measured
        
        Log.d("SafetyService", "getBatteryLevel: $current%")
        return current
    }

    private fun processSafetyCheckAI(location: Location) {
        if (ApiService.authToken == null) return

        val currentBattery = getBatteryLevel()
        lastBatteryLevel = currentBattery
        
        val prefs = getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
        val nightTravelMode = prefs.getBoolean("night_travel_mode", false)
        val userId = prefs.getString("user_id", "0") ?: "0"
        
        serviceScope.launch {
            try {
                val request = PredictRiskRequest(
                    userId = userId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = location.speed,
                    batteryLevel = currentBattery,
                    isNight = isSystemNightTime() || nightTravelMode,
                    suddenStop = suddenStopDetected,
                    shakeDetected = shakeDetected,
                    voiceTrigger = voiceTriggered,
                    manualSos = manualSosTriggered,
                    nightTravelMode = nightTravelMode,
                    routeDeviation = routeDeviationDetected,
                    movementStatus = if (location.speed > 0.5) "MOVING" else "STATIONARY"
                )

                val response = apiService.predictRisk(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        lastRiskScore = body.riskScore
                        lastRiskLevel = body.riskLevel
                        
                        withContext(Dispatchers.Main) {
                            broadcastRiskUpdate(lastRiskScore, lastRiskLevel)
                            updateNotificationIfNecessary("Status: $lastRiskLevel | Risk: $lastRiskScore%")
                        }

                        database.logDao().insert(SafetyLog(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            riskScore = lastRiskScore,
                            riskLevel = lastRiskLevel,
                            batteryLevel = lastBatteryLevel,
                            triggerType = if (manualSosTriggered) "Manual SOS" else if (shakeDetected) "Shake" else "Periodic"
                        ))

                        if (lastRiskScore >= 85) {
                            triggerEmergency(body.message ?: "AI Detected Danger")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SafetyService", "AI Prediction failed: ${e.message}")
            } finally {
                // Only reset transient sensor flags
                suddenStopDetected = false
                shakeDetected = false
                voiceTriggered = false
                routeDeviationDetected = false
            }
        }
    }

    private fun isSystemNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour > 19
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            val now = System.currentTimeMillis()
            if (acceleration > shakeThreshold) {
                lastShakePeakTime = now
                
                if (shakeStartTime == 0L) {
                    shakeStartTime = now
                    Log.d("SafetyService", "Potential shake started...")
                } else {
                    val duration = now - shakeStartTime
                    if (duration >= MIN_SHAKE_DURATION) {
                        Log.d("SafetyService", "SHAKE CONFIRMED! Duration: ${duration}ms")
                        shakeDetected = true
                        
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this, "SHAKE SOS TRIGGERED!", Toast.LENGTH_LONG).show()
                        }
                        
                        requestImmediateRiskPrediction()
                        shakeStartTime = 0L // Reset
                    }
                }
            } else {
                if (now - lastShakePeakTime > SHAKE_TIMEOUT) {
                    shakeStartTime = 0L
                }
            }
        }
    }

    private fun broadcastRiskUpdate(score: Int, level: String) {
        val intent = Intent(ACTION_RISK_UPDATE)
        intent.putExtra(EXTRA_RISK_SCORE, score)
        intent.putExtra(EXTRA_RISK_LEVEL, level)
        sendBroadcast(intent)
    }

    private fun triggerEmergency(triggerType: String) {
        val now = System.currentTimeMillis()
        // Prevent double triggers within 5s regardless of trigger type (Fast without suppression)
        if (now - lastEmergencyTriggerTime < 5000L) {
            Log.d("SafetyService", "Emergency trigger suppressed (cooldown): $triggerType")
            // Ensure manual SOS is reset even if suppressed to prevent loop
            serviceScope.launch {
                delay(5000)
                manualSosTriggered = false 
            }
            return
        }
        
        lastEmergencyTriggerTime = now
        Log.d("SafetyService", "EMERGENCY ACTIONS TRIGGERED: $triggerType")
        
        // INSTANTLY RAISE RISK SCORE TO EMERGENCY LEVEL & BROADCAST TO UI (RED ALERT)
        lastRiskScore = 100
        lastRiskLevel = "CRITICAL EMERGENCY"
        Handler(Looper.getMainLooper()).post {
            broadcastRiskUpdate(lastRiskScore, lastRiskLevel)
        }
        
        // Delay reset of manual SOS to ensure at least one backend sync cycle sees it
        serviceScope.launch {
            delay(15000) // 15 seconds should cover one or two sync cycles
            manualSosTriggered = false
            Log.d("SafetyService", "Manual SOS flag reset after delay")
        }
        
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))

        audioRecorder.startRecording()
        CommunicationManager.triggerEmergencyActions(this, lastLocation, triggerType)
        updateNotification("EMERGENCY ACTIVE: $triggerType")
        showEmergencyNotification(triggerType)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "SheShield AI Safety Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(serviceChannel)

            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID, "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when SOS is triggered"
                enableVibration(true)
            }
            manager?.createNotificationChannel(emergencyChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SheShield AI")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationIfNecessary(content: String) {
        val now = System.currentTimeMillis()
        if (AppLifecycleTracker.isAppInForeground() || (now - lastNotificationUpdateTime >= BACKGROUND_NOTIFICATION_INTERVAL)) {
            updateNotification(content)
            lastNotificationUpdateTime = now
        }
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun showEmergencyNotification(triggerType: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
            .setContentTitle("🚨 EMERGENCY TRIGGERED 🚨")
            .setContentText("SOS Activated: $triggerType. Police & Contacts are being notified!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(EMERGENCY_NOTIFICATION_ID, notification)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::connectivityManager.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        try { unregisterReceiver(stopServiceReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(dataUpdateReceiver) } catch (e: Exception) {}
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        voiceTriggerDetector.stopListening()
        audioRecorder.stopRecording()
        syncJob?.cancel()
        serviceScope.cancel()
    }
}
