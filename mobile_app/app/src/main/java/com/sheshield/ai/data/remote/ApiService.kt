package com.sheshield.ai.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sheshield.ai.Config
import com.sheshield.ai.data.*
import com.sheshield.ai.ui.LoginActivity
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    @POST("/register")
    suspend fun register(@Body request: RegisterRequest): retrofit2.Response<AuthResponse>

    @POST("/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<AuthResponse>
    
    @POST("/save-location")
    suspend fun saveLocation(@Body locationData: LocationSyncRequest): retrofit2.Response<SimpleResponse>

    @POST("/device-online")
    suspend fun deviceOnline(@Body statusData: StatusRequest): retrofit2.Response<SimpleResponse>

    @POST("/device-offline")
    suspend fun deviceOffline(): retrofit2.Response<SimpleResponse>

    @POST("/update-risk")
    suspend fun saveRiskScore(@Body riskData: RiskScoreRequest): retrofit2.Response<SimpleResponse>

    @POST("/send-alert")
    suspend fun sendEmergencyAlert(@Body alertData: EmergencyAlertRequest): retrofit2.Response<SimpleResponse>

    @POST("/community-report")
    suspend fun reportUnsafeZone(@Body zone: CommunityReportRequest): retrofit2.Response<SimpleResponse>

    @POST("/save-incident")
    suspend fun saveIncident(@Body incident: IncidentRequest): retrofit2.Response<SimpleResponse>

    @GET("/user-profile/{id}")
    suspend fun getUserProfile(@Path("id") userId: String): retrofit2.Response<UserProfile>

    @POST("/trusted-contacts")
    suspend fun addTrustedContact(@Body contact: ContactRequest): retrofit2.Response<SimpleResponse>

    @GET("/emergency-logs")
    suspend fun getEmergencyLogs(): retrofit2.Response<List<EmergencyLogEntry>>

    @POST("/refresh-token")
    suspend fun refreshAccessToken(@Body request: RefreshTokenRequest): retrofit2.Response<RefreshTokenResponse>

    @POST("/predict-risk")
    suspend fun predictRisk(@Body request: PredictRiskRequest): retrofit2.Response<PredictRiskResponse>

    companion object {
        @Volatile
        var authToken: String? = null

        fun create(context: Context? = null): ApiService {
            context?.let {
                val prefs = it.getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
                authToken = prefs.getString("access_token", null)
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val refreshTokenRetrofit = Retrofit.Builder()
                .baseUrl(Config.BACKEND_URL)
                .client(OkHttpClient.Builder().addInterceptor(logging).build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val refreshTokenApiService = refreshTokenRetrofit.create(ApiService::class.java)


            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                    authToken?.let {
                        requestBuilder.addHeader("Authorization", "Bearer $it")
                    }
                    chain.proceed(requestBuilder.build())
                }
                .apply {
                    if (context != null) {
                        authenticator(TokenAuthenticator(context, refreshTokenApiService))
                    }
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(Config.BACKEND_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

class TokenAuthenticator(private val context: Context, private val refreshTokenApiService: ApiService) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code == 401) {
            if (response.request.header("Authorization") != null && response.request.url.encodedPath.contains("/refresh-token")) {
                Log.e("TokenAuthenticator", "Already tried to refresh token, or refresh token request failed.")
                forceLogout()
                return null
            }

            synchronized(this) {
                val prefs = context.getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
                val currentAccessToken = prefs.getString("access_token", null)
                val currentRefreshToken = prefs.getString("refresh_token", null)

                if (currentAccessToken != null && currentAccessToken != ApiService.authToken) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${ApiService.authToken}")
                        .build()
                }

                if (currentRefreshToken == null) {
                    forceLogout()
                    return null
                }

                val refreshResponse = runBlocking {
                    refreshTokenApiService.refreshAccessToken(RefreshTokenRequest(currentRefreshToken))
                }

                if (refreshResponse.isSuccessful) {
                    val newTokens = refreshResponse.body()
                    if (newTokens != null && newTokens.accessToken != null) {
                        val newAccessToken = newTokens.accessToken
                        val newRefreshToken = newTokens.refreshToken ?: currentRefreshToken

                        prefs.edit().apply {
                            putString("access_token", newAccessToken)
                            putString("refresh_token", newRefreshToken)
                        }.apply()

                        ApiService.authToken = newAccessToken
                        return response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                    } else {
                        forceLogout()
                        return null
                    }
                } else {
                    forceLogout()
                    return null
                }
            }
        }
        return null
    }

    private fun forceLogout() {
        val prefs = context.getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("access_token", null) == null && ApiService.authToken == null) {
            return // Already logged out
        }
        
        Log.w("TokenAuthenticator", "Force logout triggered - clearing credentials")
        prefs.edit().clear().apply()
        ApiService.authToken = null
        
        // Broadcast to stop SafetyService
        context.sendBroadcast(Intent("com.sheshield.ai.ACTION_STOP_SERVICE"))
        
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
