package com.sheshield.ai.data.remote

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("alternatives") alternatives: Boolean = true,
        @Query("key") apiKey: String
    ): Response<DirectionsResponse>

    companion object {
        fun create(): DirectionsApiService {
            return Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DirectionsApiService::class.java)
        }
    }
}

data class DirectionsResponse(
    val routes: List<Route>,
    val status: String
)

data class Route(
    val overview_polyline: OverviewPolyline,
    val legs: List<Leg>,
    val summary: String? = null
)

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val steps: List<Step>,
    val start_location: LocationData,
    val end_location: LocationData
)

data class Step(
    val distance: Distance,
    val duration: Duration,
    val end_location: LocationData,
    val start_location: LocationData,
    val polyline: OverviewPolyline,
    val travel_mode: String,
    val html_instructions: String? = null,
    val maneuver: String? = null
)



