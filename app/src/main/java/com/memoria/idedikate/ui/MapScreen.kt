package com.memoria.idedikate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.memoria.idedikate.TokenViewModel
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.PinVisibility
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.launch

private fun PinVisibility.markerHue(): Float = when (this) {
    PinVisibility.PUBLIC -> BitmapDescriptorFactory.HUE_RED
    PinVisibility.SHARED -> BitmapDescriptorFactory.HUE_GREEN
    PinVisibility.PRIVATE -> BitmapDescriptorFactory.HUE_VIOLET
}

private const val MEMORIAL_TOKEN_COST = 1

private fun MemorialItem.markerSnippet(isOwn: Boolean): String = when {
    isOwn -> when (visibility) {
        PinVisibility.SHARED -> "Shared with ${sharedWith.size} · tap for options"
        else -> "${visibility.label} · tap for options"
    }
    visibility == PinVisibility.SHARED -> "Shared with you · tap to view in AR"
    else -> "${offerings.summary()} · tap to view in AR"
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    tokenViewModel: TokenViewModel,
    onViewInAr: (MemorialItem) -> Unit,
    mapViewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }

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

    val tokens by tokenViewModel.tokens.collectAsState()
    val memorials by mapViewModel.memorials.collectAsState()
    val syncError by mapViewModel.syncError.collectAsState()

    LaunchedEffect(syncError) {
        syncError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            mapViewModel.clearSyncError()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val moveTo: (Location?) -> Unit = { location ->
                location?.let {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                }
            }
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        moveTo(location)
                    } else {
                        // No cached fix (e.g. fresh boot); request a current one
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .addOnSuccessListener(moveTo)
                    }
                }
            } catch (e: SecurityException) {
                // Ignore, handled by hasLocationPermission check
            }
        }
    }

    // Reload pins for the visible area whenever the camera settles
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            cameraPositionState.projection?.visibleRegion?.latLngBounds?.let(mapViewModel::setVisibleBounds)
        }
    }

    val mapProperties = MapProperties(isMyLocationEnabled = hasLocationPermission)
    val mapUiSettings = MapUiSettings(myLocationButtonEnabled = true)

    // Location chosen for a new memorial, and the own memorial being managed
    var pendingPin by remember { mutableStateOf<LatLng?>(null) }
    var editingPin by remember { mutableStateOf<MemorialItem?>(null) }
    val offeringStock by tokenViewModel.offeringStock.collectAsState()

    val startPlacing: (LatLng) -> Unit = { latLng ->
        // Final check happens atomically when the memorial is confirmed
        if (tokens >= MEMORIAL_TOKEN_COST) {
            pendingPin = latLng
        } else {
            Toast.makeText(context, "You need $MEMORIAL_TOKEN_COST token to place a memorial. Earn more in the Wallet.", Toast.LENGTH_LONG).show()
        }
    }

    pendingPin?.let { latLng ->
        MemorialDialog(
            title = "Place a memorial",
            confirmLabel = "Place ($MEMORIAL_TOKEN_COST token)",
            ownEmail = mapViewModel.currentEmail,
            offeringStock = offeringStock,
            onDismiss = { pendingPin = null },
            onConfirm = { visibility, sharedWith, offerings ->
                pendingPin = null
                coroutineScope.launch {
                    if (tokenViewModel.spendForMemorial(MEMORIAL_TOKEN_COST, offerings)) {
                        mapViewModel.dropPin(latLng, "In Loving Memory", visibility, sharedWith, offerings) { e ->
                            // Give everything back if the server rejected the memorial
                            tokenViewModel.refundMemorial(MEMORIAL_TOKEN_COST, offerings)
                            Toast.makeText(context, "Couldn't save memorial: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                        Toast.makeText(context, "Memorial placed!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Not enough tokens or offerings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    editingPin?.let { pin ->
        MemorialDialog(
            title = "Your memorial",
            confirmLabel = "Save",
            ownEmail = mapViewModel.currentEmail,
            initialVisibility = pin.visibility,
            initialSharedWith = pin.sharedWith,
            placedOfferings = pin.offerings,
            onDismiss = { editingPin = null },
            onConfirm = { visibility, sharedWith, _ ->
                editingPin = null
                mapViewModel.updateVisibility(pin.id, visibility, sharedWith) { e ->
                    Toast.makeText(context, "Couldn't update memorial: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            },
            onViewInAr = {
                editingPin = null
                onViewInAr(pin)
            },
            onDelete = {
                editingPin = null
                mapViewModel.deletePin(pin.id) { e ->
                    Toast.makeText(context, "Couldn't delete memorial: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // Outer Scaffold (MainActivity) already handles system insets, so no nested Scaffold here
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Memorial Map (Tokens: $tokens)",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapLoaded = {
                    // Initial load, in case the camera never moves (e.g. no location permission)
                    cameraPositionState.projection?.visibleRegion?.latLngBounds?.let(mapViewModel::setVisibleBounds)
                },
                onMapLongClick = startPlacing
            ) {
                val currentUid = mapViewModel.currentUid
                memorials.forEach { memorial ->
                    key(memorial.id) {
                        val isOwn = memorial.ownerUid == currentUid
                        val markerState = rememberMarkerState(position = LatLng(memorial.latitude, memorial.longitude))
                        Marker(
                            state = markerState,
                            title = memorial.message,
                            snippet = memorial.markerSnippet(isOwn),
                            icon = BitmapDescriptorFactory.defaultMarker(memorial.visibility.markerHue()),
                            // Own memorials open the manage dialog; others go straight to AR
                            onInfoWindowClick = { if (isOwn) editingPin = memorial else onViewInAr(memorial) }
                        )
                    }
                }
            }

            // Crosshair marking where "Place memorial" will put it
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )

            ExtendedFloatingActionButton(
                onClick = { startPlacing(cameraPositionState.position.target) },
                icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                text = { Text("Place memorial") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
