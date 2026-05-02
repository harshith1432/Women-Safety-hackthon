package com.sheshield.ai.service

import android.util.Log
import com.sheshield.ai.data.SafetyLog
import kotlin.math.abs

/**
 * Heuristic-based engine simulating a Random Forest Classifier
 * Predicts danger escalation based on historical logs and current trends.
 */
object PredictiveEngine {

    fun predictDangerEscalation(recentLogs: List<SafetyLog>): Float {
        if (recentLogs.size < 5) return 0.0f

        var trendScore = 0.0f
        
        // 1. Trend Analysis: Is the risk score increasing?
        val scoreDiffs = mutableListOf<Int>()
        for (i in 0 until recentLogs.size - 1) {
            scoreDiffs.add(recentLogs[i].riskScore - recentLogs[i + 1].riskScore)
        }
        
        val averageIncrease = scoreDiffs.average().toFloat()
        if (averageIncrease > 5) trendScore += 0.4f // Significant upward trend
        
        // 2. Velocity Analysis: Rapid changes in location
        val lastLog = recentLogs.first()
        val prevLog = recentLogs[1]
        val latDiff = abs(lastLog.latitude - prevLog.latitude)
        val lonDiff = abs(lastLog.longitude - prevLog.longitude)
        
        if (latDiff > 0.001 || lonDiff > 0.001) {
            // User is moving fast - could be a vehicle or running
            trendScore += 0.2f
        }

        // 3. Historical Persistence: Has the user been in high risk for a while?
        val highRiskCount = recentLogs.count { it.riskScore > 60 }
        if (highRiskCount > 3) trendScore += 0.3f

        Log.d("PredictiveEngine", "Danger Escalation Probability: $trendScore")
        return trendScore.coerceIn(0.0f, 1.0f)
    }
}
