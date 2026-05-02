package com.sheshield.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("is_priority") val isPriority: Boolean = false,
    val relation: String? = null
)

@Entity(tableName = "safety_logs")
data class SafetyLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("trigger_type") val triggerType: String,
    @SerializedName("battery_level") val batteryLevel: Int = 100,
    val isSynced: Boolean = false
)

@Entity(tableName = "community_zones")
data class CommunityZone(
    @PrimaryKey @SerializedName("zone_id") val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    @SerializedName("risk_weight") val riskWeight: Int,
    val type: String
)

data class UserProfile(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    val email: String,
    @SerializedName("home_address") val homeAddress: String,
    @SerializedName("guardian_name") val guardianName: String? = null,
    @SerializedName("guardian_phone") val guardianPhone: String? = null
)

// API Request Models
data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    @SerializedName("full_name") val full_name: String,
    @SerializedName("phone_number") val phone_number: String,
    val email: String,
    val password: String,
    @SerializedName("home_address") val home_address: String? = "",
    @SerializedName("guardian_details") val guardian_details: String? = "",
    @SerializedName("emergency_contacts") val emergency_contacts: String? = ""
)

data class LocationSyncRequest(
    @SerializedName("user_id") val userId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    @SerializedName("movement_status") val movementStatus: String = "STATIONARY",
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("battery_level") val batteryLevel: Int = 100,
    @SerializedName("is_night") val isNight: Boolean = false,
    @SerializedName("night_travel_mode") val nightTravelMode: Boolean = false,
    @SerializedName("shake_detected") val shakeDetected: Boolean = false,
    @SerializedName("manual_sos") val manualSos: Boolean = false,
    @SerializedName("voice_trigger") val voiceTrigger: Boolean = false,
    @SerializedName("sudden_stop") val suddenStop: Boolean = false,
    @SerializedName("route_deviation") val routeDeviation: Boolean = false
)

data class EmergencyAlertRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("trigger_type") val triggerType: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("risk_score") val riskScore: Int,
    val message: String? = null
)

data class RiskScoreRequest(@SerializedName("risk_score") val riskScore: Int)
data class StatusRequest(val status: String)
data class CommunityReportRequest(val latitude: Double, val longitude: Double, val description: String, val type: String)
data class IncidentRequest(@SerializedName("trigger_type") val triggerType: String, val latitude: Double, val longitude: Double, @SerializedName("risk_score") val riskScore: Int)
data class ContactRequest(val name: String, @SerializedName("phone_number") val phoneNumber: String)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class PredictRiskRequest(
    @SerializedName("user_id") val userId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("is_night") val isNight: Boolean,
    @SerializedName("road_type") val roadType: String = "Unknown",
    @SerializedName("sudden_stop") val suddenStop: Boolean = false,
    @SerializedName("shake_detected") val shakeDetected: Boolean = false,
    @SerializedName("voice_trigger") val voiceTrigger: Boolean = false,
    @SerializedName("manual_sos") val manualSos: Boolean = false,
    @SerializedName("night_travel_mode") val nightTravelMode: Boolean = false,
    @SerializedName("route_deviation") val routeDeviation: Boolean = false,
    @SerializedName("movement_status") val movementStatus: String = "STATIONARY"
)

// API Response Models
data class AuthResponse(
    @SerializedName("access_token") val access_token: String,
    @SerializedName("refresh_token") val refresh_token: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("user_name") val userName: String?,
    val message: String?
)

data class RefreshTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?
)

data class PredictRiskResponse(
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_level") val riskLevel: String,
    val message: String?,
    val action: String?
)

data class SimpleResponse(val message: String?, val status: String?)

data class EmergencyLogEntry(
    val id: Int,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("trigger_type") val triggerType: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("risk_score") val riskScore: Int,
    val message: String?,
    val timestamp: String?
)
