package com.memoria.idedikate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.memoria.idedikate.TokenViewModel
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.MemorialOfferings
import com.memoria.idedikate.model.OfferingType
import com.memoria.idedikate.model.Wallet
import com.memoria.idedikate.model.PinVisibility
import kotlin.math.roundToInt

/**
 * Map marker for a memorial: a pill showing an icon for each offering placed there (with a count
 * when there's more than one), outlined in the visibility colour (red public, green shared,
 * violet private), with a pointer marking the exact spot.
 */
@Composable
private fun MemorialMarker(visibility: PinVisibility, offerings: MemorialOfferings, scale: Float) {
    val ring = visibility.color()
    val placed = OfferingType.entries.filter { offerings[it] > 0 }
    val shape = RoundedCornerShape(22.dp * scale)
    val iconSize = 30.dp * scale
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp * scale),
            modifier = Modifier
                .shadow(3.dp * scale, shape)
                .background(Color.White, shape)
                .border((3.dp * scale).coerceAtLeast(1.5.dp), ring, shape)
                .padding(horizontal = 7.dp * scale, vertical = 5.dp * scale)
        ) {
            if (placed.isEmpty()) {
                // Nothing offered yet: a faded plaque so the marker still reads as a memorial
                Icon(
                    OfferingIcons.MemorialPlaque,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(iconSize).alpha(0.45f)
                )
            }
            placed.forEach { type ->
                Box {
                    Icon(
                        OfferingIcons.forType(type),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(iconSize)
                    )
                    val count = offerings[type]
                    if (count > 1) {
                        Text(
                            text = if (count > 99) "99+" else "$count",
                            color = Color.White,
                            fontSize = 9.sp * scale,
                            lineHeight = 11.sp * scale,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(ring, RoundedCornerShape(6.dp * scale))
                                .padding(horizontal = 3.dp * scale)
                        )
                    }
                }
            }
        }
        Canvas(modifier = Modifier.size(width = 14.dp * scale, height = 9.dp * scale)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                color = ring
            )
        }
    }
}

/**
 * Marker size for a map zoom level: 1.0 at neighbourhood level (zoom 15), growing to 1.6 at street
 * level (zoom 17 and closer) and shrinking to 0.3 at whole-island level (zoom 12 and further out).
 * Rounded to steps of 0.1 because every size change re-renders each marker's bitmap; continuous
 * scaling would redraw them on every frame of a pinch.
 */
private fun markerScaleFor(zoom: Float): Float {
    val scale = if (zoom >= NEIGHBOURHOOD_ZOOM) {
        1f + (zoom - NEIGHBOURHOOD_ZOOM) * (STREET_SCALE - 1f) / (STREET_ZOOM - NEIGHBOURHOOD_ZOOM)
    } else {
        1f - (NEIGHBOURHOOD_ZOOM - zoom) * (1f - ISLAND_SCALE) / (NEIGHBOURHOOD_ZOOM - ISLAND_ZOOM)
    }
    return (scale.coerceIn(ISLAND_SCALE, STREET_SCALE) * 10).roundToInt() / 10f
}

private const val ISLAND_ZOOM = 12f
private const val NEIGHBOURHOOD_ZOOM = 15f
private const val STREET_ZOOM = 17f
private const val ISLAND_SCALE = 0.3f
private const val STREET_SCALE = 1.6f

private const val MEMORIAL_TOKEN_COST = Wallet.MEMORIAL_TOKEN_COST

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
    /** Centre on this spot (e.g. a memorial picked in the List tab) rather than the user's location. */
    focus: LatLng? = null,
    mapViewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current

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
        position = focus?.let { CameraPosition.fromLatLngZoom(it, 17f) }
            ?: CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    // Only changes when the zoom crosses a size step, so panning/zooming doesn't recompose markers
    val markerScale by remember { derivedStateOf { markerScaleFor(cameraPositionState.position.zoom) } }

    LaunchedEffect(hasLocationPermission) {
        // When opened to show a specific memorial, stay on it instead of jumping to the user
        if (hasLocationPermission && focus == null) {
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
                // Friendly early check; the rules make the real, atomic one
                val affordable = tokens >= MEMORIAL_TOKEN_COST &&
                    OfferingType.entries.all { offerings[it] <= offeringStock[it] }
                if (affordable) {
                    mapViewModel.placeMemorial(latLng, "In Loving Memory", visibility, sharedWith, offerings) { e ->
                        // Firestore has already rolled back the memorial and the payment locally
                        Toast.makeText(context, "Couldn't place memorial: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                    Toast.makeText(context, "Memorial placed!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Not enough tokens or offerings", Toast.LENGTH_SHORT).show()
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
                        // Re-rendered to a bitmap only when the visibility, offerings or size step change
                        MarkerComposable(
                            memorial.visibility,
                            memorial.offerings,
                            markerScale,
                            state = markerState,
                            title = memorial.message,
                            snippet = memorial.markerSnippet(isOwn),
                            // Own memorials open the manage dialog; others go straight to AR
                            onInfoWindowClick = { if (isOwn) editingPin = memorial else onViewInAr(memorial) }
                        ) {
                            MemorialMarker(memorial.visibility, memorial.offerings, markerScale)
                        }
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
