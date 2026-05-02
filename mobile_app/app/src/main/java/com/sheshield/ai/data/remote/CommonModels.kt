package com.sheshield.ai.data.remote

data class LocationData(
    val lat: Double,
    val lng: Double
)

data class Distance(val text: String, val value: Int)
data class Duration(val text: String, val value: Int)

data class OverviewPolyline(
    val points: String
)
