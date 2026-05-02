package com.sheshield.ai.service

import android.content.Context
import android.location.Location
import com.sheshield.ai.data.CommunityZone
import java.util.*
import kotlin.math.*

class RiskEngine(private val context: Context) {

    fun calculateRiskScore(
        location: Location?,
        batteryPct: Int,
        isNight: Boolean,
        isShaking: Boolean,
        isVoiceTriggered: Boolean,
        nearbyZones: List<CommunityZone> = emptyList()
    ): Int {
        var score = 0

        // 1. Time based risk (Night is more dangerous)
        if (isNight) score += 15

        // 2. Battery risk (Low battery increases vulnerability)
        if (batteryPct != -1) {
            if (batteryPct < 15) score += 25
            else if (batteryPct < 40) score += 10
        }

        // 3. Sensor Triggers (Immediate High Risk)
        if (isShaking) score += 60
        if (isVoiceTriggered) score += 70

        // 4. Proximity to Zones
        location?.let { loc ->
            nearbyZones.forEach { zone ->
                val distance = calculateDistance(loc.latitude, loc.longitude, zone.latitude, zone.longitude)
                if (distance <= zone.radius) {
                    // RiskWeight is positive for unsafe, negative for safe
                    score += zone.riskWeight
                }
            }
        }
        
        return score.coerceIn(0, 100)
    }

    fun getRiskLevel(score: Int): String {
        return when {
            score >= 81 -> "Emergency"
            score >= 61 -> "High Risk"
            score >= 31 -> "Warning"
            else -> "Safe"
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
