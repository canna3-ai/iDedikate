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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.memoria.idedikate.TokenViewModel
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.MemorialOfferings
import com.memoria.idedikate.model.OfferingType
import com.memoria.idedikate.model.Wallet
import com.memoria.idedikate.model.PinVisibility
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

/**
 * Map marker for a memorial: a pill showing an icon for each offering placed there (with a count
 * when there's more than one), outlined in the visibility color (red public, green shared,
 * violet private), with a pointer marking the exact spot.
 *
 * When [stackCount] memorials overlap here, the marker shows the newest one with a second card
 * peeking out behind it, outlined in the next memorial's visibility color ([behindVisibility]),
 * and a badge with the count.
 */
@Composable
private fun MemorialMarker(
    visibility: PinVisibility,
    offerings: MemorialOfferings,
    scale: Float,
    stackCount: Int = 1,
    behindVisibility: PinVisibility? = null
) {
    val ring = visibility.color()
    val placed = OfferingType.entries.filter { offerings[it] > 0 }
    val shape = RoundedCornerShape(22.dp * scale)
    val iconSize = 30.dp * scale
    val isStack = stackCount > 1
    // Room for the card behind and the badge; equal on both sides so the pointer stays centered
    val stackInset = if (isStack) 8.dp * scale else 0.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.padding(start = stackInset, top = stackInset, end = stackInset)) {
            if (isStack) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 5.dp * scale, y = (-5).dp * scale)
                        .background(Color.White, shape)
                        .border((2.dp * scale).coerceAtLeast(1.dp), (behindVisibility ?: visibility).color(), shape)
                )
            }
            MarkerPill(placed, offerings, ring, shape, iconSize, scale)
            if (isStack) {
                Text(
                    text = if (stackCount > 99) "99+" else "$stackCount",
                    color = Color.White,
                    fontSize = 11.sp * scale,
                    lineHeight = 13.sp * scale,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = stackInset, y = -stackInset)
                        .background(STACK_BADGE_COLOR, CircleShape)
                        .border((1.5.dp * scale).coerceAtLeast(1.dp), Color.White, CircleShape)
                        .widthIn(min = 18.dp * scale)
                        .padding(horizontal = 4.dp * scale, vertical = 2.dp * scale)
                )
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

private val STACK_BADGE_COLOR = Color(0xFF37474F)

/** The white pill of offering icons at the top of a memorial marker. */
@Composable
private fun MarkerPill(
    placed: List<OfferingType>,
    offerings: MemorialOfferings,
    ring: Color,
    shape: RoundedCornerShape,
    iconSize: Dp,
    scale: Float
) {
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
}

/**
 * Marker size for a map zoom level: 1.0 at neighborhood level (zoom 15), growing to 1.6 at street
 * level (zoom 17 and closer) and shrinking to 0.3 at whole-island level (zoom 12 and further out).
 * Rounded to steps of 0.1 because every size change recomposes each marker; continuous scaling
 * would recompose them on every frame of a pinch.
 */
private fun markerScaleFor(zoom: Float): Float {
    val scale = if (zoom >= NEIGHBORHOOD_ZOOM) {
        1f + (zoom - NEIGHBORHOOD_ZOOM) * (STREET_SCALE - 1f) / (STREET_ZOOM - NEIGHBORHOOD_ZOOM)
    } else {
        1f - (NEIGHBORHOOD_ZOOM - zoom) * (1f - ISLAND_SCALE) / (NEIGHBORHOOD_ZOOM - ISLAND_ZOOM)
    }
    return (scale.coerceIn(ISLAND_SCALE, STREET_SCALE) * 10).roundToInt() / 10f
}

private const val ISLAND_ZOOM = 12f
private const val NEIGHBORHOOD_ZOOM = 15f
private const val STREET_ZOOM = 17f
private const val ISLAND_SCALE = 0.3f
private const val STREET_SCALE = 1.6f

private const val MEMORIAL_TOKEN_COST = Wallet.MEMORIAL_TOKEN_COST

/** Room around a group's memorials when zooming in to separate them: about half a street-level marker. */
private val ZOOM_TO_GROUP_PADDING = 120.dp

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

/** Map zoom limits; OpenStreetMap tiles go to 19, closer zoom levels enlarge them. */
private const val MIN_ZOOM = 2.0
private const val MAX_ZOOM = 21.0

/** How long the map must stay still before the pins for the visible area are reloaded. */
private const val CAMERA_SETTLE_MS = 300L

private val MY_LOCATION_BLUE = Color(0xFF1A73E8)

/** The OpenStreetMap map view, configured once per screen. */
private fun createMapView(context: Context, center: GeoPoint, zoom: Double): MapView {
    // Must be configured before the first MapView exists. The tile servers require an identifying user agent
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        // Tiles sized in dp rather than pixels, so zoom levels match what the marker sizing expects
        isTilesScaledToDpi = true
        minZoomLevel = MIN_ZOOM
        maxZoomLevel = MAX_ZOOM
        isVerticalMapRepetitionEnabled = false
        val tileSystem = MapView.getTileSystem()
        setScrollableAreaLimitLatitude(tileSystem.maxLatitude, tileSystem.minLatitude, 0)
        controller.setZoom(zoom)
        controller.setCenter(center)
    }
}

/** Loads the pins for the visible area, once the map has been laid out. */
private fun MapView.loadVisiblePins(mapViewModel: MapViewModel) {
    if (width == 0 || height == 0) return
    val box = boundingBox
    // Zoomed out far enough that the world repeats across the screen: every longitude is visible
    val wholeWorld = width >= TileSystem.MapSize(zoomLevelDouble)
    mapViewModel.setVisibleBounds(
        south = box.latSouth,
        west = if (wholeWorld) -180.0 else box.lonWest,
        north = box.latNorth,
        east = if (wholeWorld) 180.0 else box.lonEast
    )
}

/**
 * Places the content on [point] of [mapView], its bottom center there (or its center, with
 * [centered]). Reading [cameraTick] here re-places the content as the map moves without
 * recomposing it.
 */
private fun Modifier.atMapPoint(
    mapView: MapView,
    point: GeoPoint,
    cameraTick: () -> Int,
    centered: Boolean = false
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        cameraTick()
        val pixel = mapView.projection.toPixels(point, null)
        val y = if (centered) pixel.y - placeable.height / 2 else pixel.y - placeable.height
        placeable.place(pixel.x - placeable.width / 2, y)
    }
}

/** The bubble shown above a tapped marker, like a map info window; tapping it opens the memorial. */
@Composable
private fun MarkerInfoBubble(title: String, snippet: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .shadow(4.dp, shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .widthIn(max = 260.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = Color.Black, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(snippet, color = Color.DarkGray, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    tokenViewModel: TokenViewModel,
    onViewInAr: (MemorialItem) -> Unit,
    /** Center on this spot (e.g. a memorial picked in the Memorials tab) rather than the user's location. */
    focus: GeoPoint? = null,
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
        if (!hasLocationPermission && !mapViewModel.locationPermissionRequested) {
            mapViewModel.locationPermissionRequested = true
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

    val initialZoom = if (focus != null) 17.0 else MIN_ZOOM
    val mapView = remember { createMapView(context, focus ?: GeoPoint(0.0, 0.0), initialZoom) }
    // The map's camera mirrored into Compose: its zoom, and a tick that changes whenever it moves
    var zoom by remember { mutableDoubleStateOf(initialZoom) }
    var cameraTick by remember { mutableIntStateOf(0) }

    // Only changes when the zoom crosses a size step, so panning/zooming doesn't recompose markers
    val markerScale by remember { derivedStateOf { markerScaleFor(zoom.toFloat()) } }
    // Memorials whose markers would cover each other share one stacked marker. Regrouped in half
    // zoom steps, which is as often as the grouping can noticeably change
    val groupingZoom by remember { derivedStateOf { (zoom * 2).roundToInt() / 2f } }
    val markerGroups = remember(memorials, groupingZoom, markerScale) {
        groupOverlapping(memorials, groupingZoom, markerScale)
    }
    val zoomToGroupPaddingPx = with(LocalDensity.current) { ZOOM_TO_GROUP_PADDING.roundToPx() }

    // The user's location, kept current while the map is shown, for the blue dot and the location button
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose { }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { userLocation = GeoPoint(it.latitude, it.longitude) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000).build()
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, context.mainLooper)
        } catch (e: SecurityException) {
            // Ignore, handled by hasLocationPermission check
        }
        onDispose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    LaunchedEffect(hasLocationPermission) {
        // When opened to show a specific memorial, stay on it instead of jumping to the user
        if (hasLocationPermission && focus == null) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val moveTo: (Location?) -> Unit = { location ->
                location?.let {
                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
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

    // Reload pins for the visible area whenever the camera settles (and once the map is first laid out)
    LaunchedEffect(cameraTick) {
        delay(CAMERA_SETTLE_MS)
        mapView.loadVisiblePins(mapViewModel)
    }

    // Location chosen for a new memorial, the own memorial being managed, and the stacked marker being chosen from
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null) }
    var editingPin by remember { mutableStateOf<MemorialItem?>(null) }
    var choosingGroup by remember { mutableStateOf<MemorialGroup?>(null) }
    // The marker whose info bubble is showing, by its representative memorial's id
    var selectedMarkerId by remember { mutableStateOf<String?>(null) }
    val offeringStock by tokenViewModel.offeringStock.collectAsState()
    val currentUid = mapViewModel.currentUid

    // Own memorials open the manage dialog; others go straight to AR
    val openMemorial: (MemorialItem) -> Unit = { memorial ->
        if (memorial.ownerUid == currentUid) editingPin = memorial else onViewInAr(memorial)
    }

    choosingGroup?.let { group ->
        MemorialChooserDialog(
            group = group,
            currentUid = currentUid,
            onChoose = { memorial ->
                choosingGroup = null
                openMemorial(memorial)
            },
            // Memorials at exactly the same spot can't be separated by zooming
            onZoomIn = if (group.isSameSpot) null else {
                {
                    choosingGroup = null
                    val bounds = BoundingBox.fromGeoPointsSafe(group.memorials.map { GeoPoint(it.latitude, it.longitude) })
                    mapView.zoomToBoundingBox(bounds, true, zoomToGroupPaddingPx)
                }
            },
            onDismiss = { choosingGroup = null }
        )
    }

    val startPlacing: (GeoPoint) -> Unit = { location ->
        // Final check happens atomically when the memorial is confirmed
        if (tokens >= MEMORIAL_TOKEN_COST) {
            pendingPin = location
        } else {
            Toast.makeText(context, "You need $MEMORIAL_TOKEN_COST token to place a memorial. Earn more in the Wallet.", Toast.LENGTH_LONG).show()
        }
    }
    val currentStartPlacing by rememberUpdatedState(startPlacing)

    // Mirror camera moves into Compose, place memorials on long press, close the info bubble on tap,
    // and pause tile loading along with the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mapView, lifecycleOwner) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                cameraTick++
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                zoom = mapView.zoomLevelDouble
                cameraTick++
                return false
            }
        }
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                selectedMarkerId = null
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { currentStartPlacing(it) }
                return true
            }
        })
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        mapView.addMapListener(mapListener)
        mapView.overlays.add(0, eventsOverlay)
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> cameraTick++ }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mapView.removeMapListener(mapListener)
            mapView.overlays.remove(eventsOverlay)
            mapView.onDetach()
        }
    }

    pendingPin?.let { location ->
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
                    mapViewModel.placeMemorial(
                        location, "In Loving Memory", visibility, sharedWith, offerings,
                        onSuccess = { Toast.makeText(context, "Memorial placed!", Toast.LENGTH_SHORT).show() },
                        onFailure = { e ->
                            // Firestore has already rolled back the memorial and the payment locally
                            Toast.makeText(context, "Couldn't place memorial: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    )
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
                .clipToBounds()
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // Markers are regular Compose UI laid over the map, re-placed as it moves
            userLocation?.let { location ->
                Box(
                    modifier = Modifier
                        .atMapPoint(mapView, location, { cameraTick }, centered = true)
                        .size(18.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                        .background(MY_LOCATION_BLUE, CircleShape)
                )
            }
            markerGroups.forEach { group ->
                // The newest memorial stands for the group, at its exact spot
                val memorial = group.representative
                val behindVisibility = group.memorials.getOrNull(1)?.visibility
                key(memorial.id) {
                    val isOwn = memorial.ownerUid == currentUid
                    val isSelected = memorial.id == selectedMarkerId
                    val position = remember(memorial.latitude, memorial.longitude) { GeoPoint(memorial.latitude, memorial.longitude) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .zIndex(if (isSelected) 1f else 0f)
                            .atMapPoint(mapView, position, { cameraTick })
                    ) {
                        if (isSelected) {
                            MarkerInfoBubble(
                                title = if (group.size > 1) "${group.size} memorials here" else memorial.message,
                                snippet = when {
                                    group.size == 1 -> memorial.markerSnippet(isOwn)
                                    group.isSameSpot -> "Tap to choose one"
                                    else -> "Tap to choose one or zoom in"
                                },
                                onClick = {
                                    selectedMarkerId = null
                                    if (group.size > 1) choosingGroup = group else openMemorial(memorial)
                                }
                            )
                        }
                        Box(
                            modifier = Modifier.clickable(interactionSource = null, indication = null) {
                                selectedMarkerId = if (isSelected) null else memorial.id
                            }
                        ) {
                            MemorialMarker(
                                memorial.visibility,
                                memorial.offerings,
                                markerScale,
                                stackCount = group.size,
                                behindVisibility = behindVisibility
                            )
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

            if (hasLocationPermission) {
                SmallFloatingActionButton(
                    onClick = {
                        val location = userLocation
                        if (location != null) {
                            mapView.controller.animateTo(location, maxOf(mapView.zoomLevelDouble, 15.0), null)
                        } else {
                            Toast.makeText(context, "Finding your location…", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color.DarkGray,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My location")
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    val center = mapView.mapCenter
                    startPlacing(GeoPoint(center.latitude, center.longitude))
                },
                icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                text = { Text("Place memorial") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

            // Attribution required by the OpenStreetMap tile usage policy
            Text(
                text = "© OpenStreetMap contributors",
                color = Color.DarkGray,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
