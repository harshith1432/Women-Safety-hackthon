package com.sheshield.ai.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.sheshield.ai.R
import com.sheshield.ai.data.remote.ApiService
import com.sheshield.ai.data.remote.DirectionsApiService
import com.sheshield.ai.data.remote.PlacesApiService
import com.sheshield.ai.databinding.ActivityMainBinding
import com.sheshield.ai.service.SafetyService
import com.sheshield.ai.utils.MapUtils
import com.sheshield.ai.utils.PermissionUtils
import com.sheshield.ai.utils.SafeRouteEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var googleMap: GoogleMap? = null
    private val prefs by lazy { getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE) }
    private val placesApi = PlacesApiService.create()
    private val directionsApi = DirectionsApiService.create()
    private val apiService by lazy { ApiService.create(this) }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var alternativePolylines: MutableList<Polyline> = mutableListOf()
    private var isFollowingSafeRoute = false
    private val DEVIATION_THRESHOLD_METERS = 75.0

    private val riskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SafetyService.ACTION_RISK_UPDATE) {
                val score = intent.getIntExtra(SafetyService.EXTRA_RISK_SCORE, 0)
                val level = intent.getStringExtra(SafetyService.EXTRA_RISK_LEVEL) ?: "Unknown"
                updateRiskUI(score, level)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBackgroundLocationPermission()
        } else {
            Toast.makeText(this, "Core permissions denied. Some safety features may not work.", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSafetyService()
            enableMyLocation()
        } else {
            Toast.makeText(this, "Background location denied. SOS may not trigger when app is closed.", Toast.LENGTH_LONG).show()
            startSafetyService()
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        checkPermissions()
        checkBackendConnection()
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SafetyService.ACTION_RISK_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(riskReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(riskReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(riskReceiver)
    }

    private fun checkBackendConnection() {
        val userId = prefs.getString("user_id", null) ?: return
        lifecycleScope.launch {
            try {
                val response = apiService.getUserProfile(userId)
                if (response.isSuccessful) {
                    Log.d("MainActivity", "AI Backend connection verified for user: $userId")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Connection check failed", e)
            }
        }
    }

    private fun setupUI() {
        binding.sosButton.setOnClickListener { triggerManualSOS() }
        binding.contactsButton.setOnClickListener { startActivity(Intent(this, ContactsActivity::class.java)) }
        binding.recordButton.setOnClickListener { Toast.makeText(this, "AI: Recording & Analysis Active", Toast.LENGTH_SHORT).show() }
        
        binding.searchButton.setOnClickListener { performSearch() }
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        binding.chipPolice.setOnClickListener { findNearby("police") }
        binding.chipHospital.setOnClickListener { findNearby("hospital") }
        binding.chipPharmacy.setOnClickListener { findNearby("pharmacy") }

        binding.safetyPathButton.setOnClickListener { calculateSafePath() }

        binding.nightModeSwitch.isChecked = prefs.getBoolean("night_travel_mode", false)
        binding.nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("night_travel_mode", isChecked).apply()
            Toast.makeText(this, if (isChecked) "Night Mode: AI Sensitivity Increased" else "Night Mode Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRiskUI(score: Int, level: String) {
        binding.riskLevelValue.text = "$level ($score%)"
        
        val color = when {
            score >= 81 -> Color.parseColor("#ef4444") // Red
            score >= 61 -> Color.parseColor("#f97316") // Orange
            score >= 31 -> Color.parseColor("#eab308") // Yellow
            else -> Color.parseColor("#10b981") // Green
        }
        binding.riskLevelValue.setTextColor(color)
    }

    private fun triggerManualSOS() {
        val intent = Intent(this, SafetyService::class.java).apply {
            action = SafetyService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "SOS TRIGGERED: Alerting Contacts & Police", Toast.LENGTH_LONG).show()
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString()
        if (query.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    
                    withContext(Dispatchers.Main) {
                        destinationMarker?.remove()
                        destinationMarker = googleMap?.addMarker(MarkerOptions()
                            .position(latLng)
                            .title(query)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                        
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        fetchAndDrawRoute(latLng)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Search failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun findNearby(type: String) {
        getCurrentLocation { location ->
            fetchNearbySafePlaces(location.latitude, location.longitude, type)
        }
    }

    private fun calculateSafePath() {
        destinationMarker?.let { dest ->
            Toast.makeText(this, "AI: Calculating safest route using live risk data...", Toast.LENGTH_LONG).show()
            fetchAndDrawRoute(dest.position, isSafe = true)
        } ?: Toast.makeText(this, "Please search for a destination first", Toast.LENGTH_SHORT).show()
    }

    private fun fetchAndDrawRoute(dest: LatLng, isSafe: Boolean = false) {
        getCurrentLocation { loc ->
            val origin = "${loc.latitude},${loc.longitude}"
            val destination = "${dest.latitude},${dest.longitude}"
            val apiKey = getString(R.string.google_maps_key)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = directionsApi.getDirections(origin, destination, alternatives = true, apiKey = apiKey)
                    if (response.isSuccessful && response.body()?.routes?.isNotEmpty() == true) {
                        val routes = response.body()!!.routes
                        val safestRoute = if (isSafe) SafeRouteEngine.findSafestRoute(routes) else routes[0]
                        
                        withContext(Dispatchers.Main) {
                            // Clear existing polylines
                            routePolyline?.remove()
                            alternativePolylines.forEach { it.remove() }
                            alternativePolylines.clear()

                            val builder = LatLngBounds.Builder()

                            routes.forEach { route ->
                                val points = MapUtils.decodePolyline(route.overview_polyline.points)
                                points.forEach { builder.include(it) }

                                val isTheSafest = (route == safestRoute)
                                val options = PolylineOptions()
                                    .addAll(points)
                                    .width(if (isTheSafest) 18f else 10f)
                                    .color(if (isTheSafest) Color.parseColor("#10b981") else Color.parseColor("#475569"))
                                    .zIndex(if (isTheSafest) 10f else 5f)
                                    .jointType(JointType.ROUND)
                                
                                val poly = googleMap?.addPolyline(options)
                                if (isTheSafest) {
                                    routePolyline = poly
                                    isFollowingSafeRoute = isSafe
                                    // Show Distance and ETA
                                    val leg = route.legs.firstOrNull()
                                    leg?.let {
                                        Toast.makeText(this@MainActivity, "Safest Route: ${it.distance.text}, ${it.duration.text}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    poly?.let { alternativePolylines.add(it) }
                                }
                            }
                            
                            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            drawStraightLine(LatLng(loc.latitude, loc.longitude), dest, isSafe)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Route fetch failed", e)
                    withContext(Dispatchers.Main) {
                        drawStraightLine(LatLng(loc.latitude, loc.longitude), dest, isSafe)
                    }
                }
            }
        }
    }

    private fun drawStraightLine(start: LatLng, dest: LatLng, isSafe: Boolean) {
        routePolyline?.remove()
        val options = PolylineOptions()
            .add(start)
            .add(dest)
            .width(12f)
            .color(if (isSafe) Color.parseColor("#10b981") else Color.parseColor("#38bdf8"))
            .jointType(JointType.ROUND)
        routePolyline = googleMap?.addPolyline(options)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(callback: (android.location.Location) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    if (isFollowingSafeRoute) {
                        checkRouteDeviation(location)
                    }
                    callback(location)
                } else {
                    Toast.makeText(this, "Waiting for GPS fix...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkRouteDeviation(location: android.location.Location) {
        routePolyline?.let { poly ->
            val userPos = LatLng(location.latitude, location.longitude)
            val points = poly.points
            
            var minDistance = Double.MAX_VALUE
            for (point in points) {
                val distance = MapUtils.calculateDistance(userPos, point)
                if (distance < minDistance) minDistance = distance
            }

            if (minDistance > DEVIATION_THRESHOLD_METERS) {
                Log.w("MainActivity", "ROUTE DEVIATION DETECTED: ${minDistance}m")
                Toast.makeText(this, "⚠️ DEVIATION DETECTED! Returning to safe path is recommended.", Toast.LENGTH_LONG).show()
                
                // Broadcast to SafetyService to increase risk
                val intent = Intent(SafetyService.ACTION_ROUTE_DEVIATION)
                intent.putExtra("is_deviation", true)
                sendBroadcast(intent)
                
                isFollowingSafeRoute = false // Reset to avoid constant spamming
            }
        }
    }

    private fun fetchNearbySafePlaces(lat: Double, lng: Double, type: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiKey = getString(R.string.google_maps_key)
                val response = placesApi.getNearbyPlaces("$lat,$lng", 5000, type, apiKey)
                if (response.isSuccessful) {
                    val places = response.body()?.results ?: emptyList()
                    withContext(Dispatchers.Main) {
                        googleMap?.clear()
                        places.forEach { place ->
                            googleMap?.addMarker(MarkerOptions()
                                .position(LatLng(place.geometry.location.lat, place.geometry.location.lng))
                                .title(place.name)
                                .snippet(place.vicinity)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Nearby search failed", e)
            }
        }
    }

    private fun checkPermissions() {
        val missingBasic = PermissionUtils.REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingBasic.isEmpty()) {
            checkBackgroundLocationPermission()
        } else {
            showPermissionRationale()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationRationale()
            } else {
                startSafetyService()
                enableMyLocation()
            }
        } else {
            startSafetyService()
            enableMyLocation()
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this, R.style.Theme_SheShieldAI_Dialog)
            .setTitle("Safety Permissions")
            .setMessage("Location and sensors are vital for SheShield AI's live prediction engine.")
            .setPositiveButton("Grant") { _, _ -> requestPermissionLauncher.launch(PermissionUtils.REQUIRED_PERMISSIONS) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this, R.style.Theme_SheShieldAI_Dialog)
            .setTitle("Background Location")
            .setMessage("To keep the AI protection active when the app is closed, please select 'Allow all the time' in the location settings.")
            .setPositiveButton("Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("No thanks") { _, _ ->
                startSafetyService()
                enableMyLocation()
            }
            .show()
    }

    private fun startSafetyService() {
        val intent = Intent(this, SafetyService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        googleMap?.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        }
    }
}
