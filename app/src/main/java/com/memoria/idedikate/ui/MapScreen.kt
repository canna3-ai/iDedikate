package com.memoria.idedikate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.memoria.idedikate.network.MemorialItem
import com.memoria.idedikate.network.RetrofitClient
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var hasLocationPermission by remember { 
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var tokens by remember { mutableStateOf(5) }
    val memorials = remember { mutableStateListOf<MemorialItem>() }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        userLocation = latLng
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    }
                }
            } catch (e: SecurityException) {
                // Ignore, handled by hasLocationPermission check
            }
        }
    }

    val mapProperties = MapProperties(isMyLocationEnabled = hasLocationPermission)
    val mapUiSettings = MapUiSettings(myLocationButtonEnabled = true)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues())
            ) {
                Text(
                    text = "Memorial Map (Tokens: $tokens)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapLongClick = { latLng ->
                    if (tokens > 0) {
                        tokens -= 1
                        val newItem = MemorialItem(
                            id = UUID.randomUUID().toString(),
                            latitude = latLng.latitude,
                            longitude = latLng.longitude,
                            message = "In Loving Memory"
                        )
                        memorials.add(newItem)
                        
                        // Mock sync using Retrofit
                        coroutineScope.launch {
                            try {
                                RetrofitClient.apiService.dropPin(newItem)
                                Toast.makeText(context, "Memorial pin dropped & synced!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                // Error handling
                                Toast.makeText(context, "Memorial pin dropped (sync failed: ${e.message})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Not enough tokens!", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                memorials.forEach { memorial ->
                    val markerState = rememberMarkerState(position = LatLng(memorial.latitude, memorial.longitude))
                    Marker(
                        state = markerState,
                        title = "Memorial",
                        snippet = memorial.message
                    )
                }
            }
        }
    }
}
