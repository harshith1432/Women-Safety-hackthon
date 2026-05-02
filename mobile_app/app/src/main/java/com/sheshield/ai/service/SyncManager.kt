package com.sheshield.ai.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.sheshield.ai.data.EmergencyAlertRequest
import com.sheshield.ai.data.LocationSyncRequest
import com.sheshield.ai.data.RiskScoreRequest
import com.sheshield.ai.data.local.AppDatabase
import com.sheshield.ai.data.remote.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val apiService = ApiService.create(context)
    private val syncScope = CoroutineScope(Dispatchers.IO)

    fun checkAndSync() {
        if (isInternetAvailable()) {
            syncData()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun syncData() {
        syncScope.launch {
            try {
                val unsyncedLogs = database.logDao().getUnsynced()
                if (unsyncedLogs.isNotEmpty()) {
                    Log.d("SyncManager", "Processing ${unsyncedLogs.size} unsynced items...")
                    
                    val prefs = context.getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
                    val userId = prefs.getString("user_id", "0") ?: "0"

                    for (log in unsyncedLogs) {
                        var success = false
                        
                        // 1. Always sync location using the concrete data class
                        val locationRequest = LocationSyncRequest(
                            userId = userId,
                            latitude = log.latitude,
                            longitude = log.longitude,
                            riskScore = log.riskScore,
                            batteryLevel = log.batteryLevel
                        )
                        
                        val locRes = apiService.saveLocation(locationRequest)
                        
                        if (locRes.isSuccessful) {
                            // 2. Sync risk score using concrete class
                            apiService.saveRiskScore(RiskScoreRequest(riskScore = log.riskScore))
                            
                            // 3. If it's an emergency, send alert using concrete class
                            if (log.riskLevel == "Emergency" || log.triggerType == "Manual") {
                                val alertRequest = EmergencyAlertRequest(
                                    userId = userId,
                                    latitude = log.latitude,
                                    longitude = log.longitude,
                                    riskScore = log.riskScore,
                                    triggerType = log.triggerType,
                                    message = "SOS Sync from Log (${log.triggerType})"
                                )
                                apiService.sendEmergencyAlert(alertRequest)
                            }
                            success = true
                        }

                        if (success) {
                            database.logDao().update(log.copy(isSynced = true))
                            Log.d("SyncManager", "Log ID ${log.id} synced successfully")
                        }
                    }
                    Log.d("SyncManager", "Sync complete")
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Sync loop failed: ${e.message}")
            }
        }
    }
}
