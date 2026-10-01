package com.memoria.idedikate.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor.TerrainAnchorState
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.memoria.idedikate.ar.ArPhoto
import com.memoria.idedikate.ar.GeoMath
import com.memoria.idedikate.ar.MemorialItems
import com.memoria.idedikate.model.ArMemorial
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.TerrainAnchorNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** How the memorial is placed in AR. */
private enum class ArMode(val label: String) {
    /** Anchored at the memorial's real-world latitude/longitude (ARCore Geospatial API). */
    AT_LOCATION("At its location"),
    /** Tap any flat surface, wherever the user is. */
    PLACE_ANYWHERE("Place anywhere")
}

/** Only anchor memorials this close to the viewer; beyond that they'd be too small to see anyway. */
private const val MAX_AR_DISTANCE_M = 100.0
/** Localization must be at least this good before anchoring, or the memorial lands in the wrong spot. */
private const val MAX_HORIZONTAL_ACCURACY_M = 10.0
private const val MAX_YAW_ACCURACY_DEG = 15.0
/** A tap off the detected planes may land on ground this close to level (cosine of the max tilt, ~25°). */
private const val LEVEL_SURFACE_MIN_UP = 0.9f

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun ARScreen(memorial: ArMemorial?) {
    if (memorial == null) {
        // Opened from the tab bar: there's nothing to place until a memorial is chosen on the map
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Choose a memorial on the Memorials tab or the Map, then tap \"View in AR\" to see it here.",
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.has(Manifest.permission.CAMERA)) }
    // The Geospatial API needs precise location; approximate location isn't enough
    var hasPreciseLocation by remember { mutableStateOf(context.has(Manifest.permission.ACCESS_FINE_LOCATION)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasCamera = granted[Manifest.permission.CAMERA] ?: hasCamera
        hasPreciseLocation = granted[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasPreciseLocation
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCamera) add(Manifest.permission.CAMERA)
            if (!hasPreciseLocation) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    if (!hasCamera) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required for AR.")
        }
        return
    }

    val hasCoordinates = memorial.latitude != null && memorial.longitude != null
    val locationModeAvailable = hasCoordinates && hasPreciseLocation
    var mode by remember { mutableStateOf(if (locationModeAvailable) ArMode.AT_LOCATION else ArMode.PLACE_ANYWHERE) }
    // Permission can be granted after the first composition
    LaunchedEffect(locationModeAvailable) {
        if (locationModeAvailable) mode = ArMode.AT_LOCATION
    }
    var status by remember { mutableStateOf("") }
    var arView by remember { mutableStateOf<SurfaceView?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // One AR session for both modes: SceneView closes a discarded session on a background thread
        // without pausing it, so starting a new one alongside it crashes ARCore's camera teardown
        ARSceneViewCompose(
            memorial = memorial,
            mode = mode,
            onStatusChange = { status = it },
            onArViewChange = { arView = it }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            ArMode.entries.forEach { option ->
                val enabled = option == ArMode.PLACE_ANYWHERE || locationModeAvailable
                FilterChip(
                    selected = mode == option,
                    enabled = enabled,
                    onClick = { mode = option },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.85f))
                )
            }
        }

        // Explain why "At its location" is greyed out
        val hint = if (mode == ArMode.PLACE_ANYWHERE && hasCoordinates && !hasPreciseLocation) {
            "Allow precise location to see this memorial at its real location."
        } else ""
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            arView?.let { view ->
                ExtendedFloatingActionButton(
                    text = { Text(if (isSharing) "Preparing photo…" else "Share photo") },
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    onClick = {
                        if (isSharing) return@ExtendedFloatingActionButton
                        isSharing = true
                        scope.launch {
                            try {
                                val photo = ArPhoto.capture(view)
                                if (photo == null) {
                                    Toast.makeText(context, "Couldn't take the photo, please try again", Toast.LENGTH_SHORT).show()
                                } else {
                                    ArPhoto.addCaption(photo, memorial.title)
                                    ArPhoto.share(context, photo, memorial.title)
                                    photo.recycle()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("ARScreen", "Couldn't share AR photo", e)
                                Toast.makeText(context, "Couldn't share the photo", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    }
                )
            }
            Text(
                text = listOf(status, hint).filter { it.isNotEmpty() }.joinToString("\n"),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            )
        }
    }
}

/** Per-mode bookkeeping for location mode; plain fields, so per-frame updates don't recompose. */
private class GeoPlacement {
    var resolving = false
    var node: AnchorNode? = null
    var failed = false
    var lastStatus = ""
    /** Set once the user switches away, so a terrain anchor still resolving is dropped. */
    var cancelled = false
}

/** Session-wide bookkeeping that outlives mode switches. */
private class SessionState {
    var geospatialSupported = true
    /** The mode the session is currently configured for; null until the first frame. */
    var configuredMode: ArMode? = null
    /** Nodes from a previous mode, already out of the scene; destroyed with the screen. */
    val retiredNodes = mutableListOf<AnchorNode>()
}

@Composable
private fun ARSceneViewCompose(
    memorial: ArMemorial,
    mode: ArMode,
    onStatusChange: (String) -> Unit,
    /** Reports the AR view for taking photos, or null once it's gone. */
    onArViewChange: (SurfaceView?) -> Unit
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val childNodes = rememberNodes()
    val layout = remember(memorial) { MemorialItems.layoutFor(memorial.offerings) }
    // Only read inside callbacks, so updating it every frame doesn't recompose
    var frame by remember { mutableStateOf<Frame?>(null) }
    var sessionFailed by remember { mutableStateOf(false) }
    val sessionState = remember { SessionState() }
    // Everything below starts over whenever the mode changes
    var hasPlaced by remember(mode) { mutableStateOf(false) }
    var isTrackingPlane by remember(mode) { mutableStateOf(false) }
    val geo = remember(mode) { GeoPlacement() }

    DisposableEffect(mode) {
        onDispose {
            // Leaving this mode: stop anchor resolution and take its memorial out of the scene
            geo.cancelled = true
            childNodes.filterIsInstance<AnchorNode>().forEach { node ->
                runCatching { node.detachAnchor() }
                sessionState.retiredNodes += node
            }
            childNodes.clear()
        }
    }
    DisposableEffect(Unit) {
        // Registered after the engine, so this runs before the engine is destroyed
        onDispose {
            sessionState.retiredNodes.forEach { runCatching { it.destroy() } }
            sessionState.retiredNodes.clear()
        }
    }

    fun setStatus(text: String) {
        if (text != geo.lastStatus) {
            geo.lastStatus = text
            onStatusChange(text)
        }
    }

    LaunchedEffect(mode) {
        // The session can fail before this runs; don't overwrite that (more useful) message
        if (geo.lastStatus.isEmpty()) {
            setStatus(
                if (mode == ArMode.AT_LOCATION) "Starting location-based AR…"
                else "Move your phone slowly to find a flat surface"
            )
        }
    }

    /** Anchors the memorial on the surface under the user's finger, facing them. */
    fun placeAt(motionEvent: MotionEvent) {
        val currentFrame = frame ?: return
        if (currentFrame.camera.trackingState != TrackingState.TRACKING) {
            return setStatus("Hold on, still getting a sense of the room. Move your phone slowly.")
        }
        val hit = runCatching { currentFrame.surfaceHitAt(motionEvent) }
            .onFailure { Log.w("ARScreen", "Hit test failed", it) }
            .getOrNull()
            ?: return setStatus(
                if (isTrackingPlane) "No flat surface there. Tap on the highlighted area, or move a little closer."
                else "Still looking for a flat surface. Move your phone slowly."
            )
        val anchor = runCatching { hit.trackable.createAnchor(facingCamera(hit.hitPose, currentFrame.camera.pose)) }
            .onFailure { Log.w("ARScreen", "Couldn't create anchor", it) }
            .getOrNull()
            ?: return setStatus("Couldn't place it there, please try again.")

        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        MemorialItems.renderOfferings(engine, materialLoader, anchorNode, layout)
        childNodes += anchorNode
        hasPlaced = true
        setStatus("${memorial.title} · ${memorial.offerings.summary()}")
    }
    // The gesture listener outlives recompositions, so it calls through this to see the current mode
    val onTap by rememberUpdatedState<(MotionEvent) -> Unit> { motionEvent ->
        if (mode == ArMode.PLACE_ANYWHERE && !hasPlaced) placeAt(motionEvent)
    }

    if (sessionFailed) {
        // Without a session the AR surface shows stale graphics memory, so drop it entirely
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        materialLoader = materialLoader,
        childNodes = childNodes,
        planeRenderer = mode == ArMode.PLACE_ANYWHERE && !hasPlaced,
        sessionConfiguration = { session, config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            config.depthMode =
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC
                else Config.DepthMode.DISABLED
            sessionState.geospatialSupported = session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)
        },
        onSessionFailed = { exception ->
            Log.e("ARScreen", "AR session failed", exception)
            setStatus(exception.toArUnavailableMessage())
            sessionFailed = true
        },
        onSessionUpdated = { session, updatedFrame ->
            frame = updatedFrame
            if (sessionState.configuredMode != mode) {
                sessionState.configuredMode = mode
                configureGeospatial(session, sessionState, enabled = mode == ArMode.AT_LOCATION)
            }
            when (mode) {
                ArMode.AT_LOCATION ->
                    if (!sessionState.geospatialSupported) {
                        setStatus("This phone doesn't support location-based AR. Use \"Place anywhere\" instead.")
                    } else {
                        updateGeospatial(session, memorial, geo, ::setStatus) { place ->
                            TerrainAnchorNode.resolve(
                                engine = engine,
                                session = session,
                                latitude = place.latitude,
                                longitude = place.longitude,
                                // Sit on the ground at the memorial's spot
                                altitudeAboveTerrain = 0.0,
                                eusQuaternion = GeoMath.facingQuaternion(place.bearingToViewer)
                            ) { state, node ->
                                geo.resolving = false
                                if (geo.cancelled) {
                                    // The user switched modes while this was resolving
                                    node?.let { runCatching { it.detachAnchor() }; it.destroy() }
                                } else if (node != null) {
                                    MemorialItems.renderOfferings(engine, materialLoader, node, layout)
                                    childNodes += node
                                    geo.node = node
                                } else {
                                    geo.failed = true
                                    setStatus(state.toMessage())
                                }
                            }
                        }
                    }

                ArMode.PLACE_ANYWHERE ->
                    if (!hasPlaced && !isTrackingPlane &&
                        updatedFrame.getUpdatedTrackables(Plane::class.java).isNotEmpty()
                    ) {
                        isTrackingPlane = true
                        setStatus("Tap a surface to place \"${memorial.title}\"")
                    }
            }
        },
        onTrackingFailureChanged = { reason ->
            if (mode == ArMode.PLACE_ANYWHERE && !hasPlaced) {
                setStatus(reason.toMessage() ?: "Move your phone slowly to find a flat surface")
            }
        },
        onGestureListener = rememberOnGestureListener(
            // Not onSingleTapConfirmed: that waits out the double-tap timeout and is dropped
            // entirely when the user taps again quickly, so repeated taps never placed anything
            onSingleTapUp = { motionEvent, _ -> onTap(motionEvent) }
        ),
        onViewCreated = { onArViewChange(this) }
    )
    DisposableEffect(Unit) {
        onDispose { onArViewChange(null) }
    }
}

/**
 * The surface under a tap: a detected plane if possible, otherwise a depth or feature point on
 * level ground (so a tap just beside the detected area still works). Walls and slopes are skipped.
 */
private fun Frame.surfaceHitAt(motionEvent: MotionEvent): HitResult? {
    val hits = hitTest(motionEvent).filter { it.trackable.trackingState == TrackingState.TRACKING }
    return hits.firstOrNull { hit ->
        val plane = hit.trackable as? Plane
        plane != null && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(hit.hitPose)
    } ?: hits.firstOrNull { hit ->
        val trackable = hit.trackable
        val isSurfacePoint = trackable is DepthPoint ||
            (trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
        // The hit pose's Y axis is the surface normal; keep it close to straight up
        isSurfacePoint && hit.hitPose.yAxis[1] > LEVEL_SURFACE_MIN_UP
    }
}

/** Points the memorial's front (+z) at the camera, standing upright at [hitPose]. */
private fun facingCamera(hitPose: Pose, cameraPose: Pose): Pose {
    val yaw = atan2(cameraPose.tx() - hitPose.tx(), cameraPose.tz() - hitPose.tz())
    return Pose(
        floatArrayOf(hitPose.tx(), hitPose.ty(), hitPose.tz()),
        floatArrayOf(0f, sin(yaw / 2), 0f, cos(yaw / 2))
    )
}

/** Turns the Geospatial API on for location mode and off otherwise, so "Place anywhere" doesn't pay for it. */
private fun configureGeospatial(session: Session, state: SessionState, enabled: Boolean) {
    // Configuring an unsupported mode throws, so only enable it when supported
    val geospatialMode =
        if (enabled && state.geospatialSupported) Config.GeospatialMode.ENABLED else Config.GeospatialMode.DISABLED
    val config = session.config
    if (config.geospatialMode == geospatialMode) return
    config.geospatialMode = geospatialMode
    runCatching { session.configure(config) }.onFailure {
        Log.e("ARScreen", "Couldn't set geospatial mode to $geospatialMode", it)
        if (geospatialMode == Config.GeospatialMode.ENABLED) state.geospatialSupported = false
    }
}

/** Where to anchor a memorial, and which way it should face. */
private class GeoTarget(val latitude: Double, val longitude: Double, val bearingToViewer: Double)

/**
 * One frame of location mode: waits for ARCore to localize the phone precisely, then either tells
 * the user how far away the memorial is, or (once within range) asks [resolveAnchor] to place it.
 */
private fun updateGeospatial(
    session: Session,
    memorial: ArMemorial,
    geo: GeoPlacement,
    setStatus: (String) -> Unit,
    resolveAnchor: (GeoTarget) -> Unit
) {
    val lat = memorial.latitude ?: return
    val lng = memorial.longitude ?: return
    if (geo.failed) return

    val earth = session.earth ?: return setStatus("Starting location-based AR…")
    if (earth.earthState != Earth.EarthState.ENABLED) return setStatus(earth.earthState.toMessage())
    if (earth.trackingState != TrackingState.TRACKING) {
        return setStatus("Point your camera at nearby buildings and streets to find your position…")
    }

    val pose = earth.cameraGeospatialPose
    val distance = GeoMath.distanceMeters(pose.latitude, pose.longitude, lat, lng)
    val direction = GeoMath.compassPoint(GeoMath.bearingDegrees(pose.latitude, pose.longitude, lat, lng))

    val placed = geo.node
    if (placed != null) {
        return setStatus("${memorial.title} · ${GeoMath.formatDistance(distance)} away · ${memorial.offerings.summary()}")
    }
    if (pose.horizontalAccuracy > MAX_HORIZONTAL_ACCURACY_M || pose.orientationYawAccuracy > MAX_YAW_ACCURACY_DEG) {
        return setStatus("Finding your exact position… (±${pose.horizontalAccuracy.toInt()} m). Keep scanning around you.")
    }
    if (distance > MAX_AR_DISTANCE_M) {
        return setStatus(
            "\"${memorial.title}\" is ${GeoMath.formatDistance(distance)} away to the $direction. " +
                "Walk within ${MAX_AR_DISTANCE_M.toInt()} m to see it here."
        )
    }
    if (!geo.resolving) {
        geo.resolving = true
        setStatus("Placing the memorial at its location…")
        resolveAnchor(GeoTarget(lat, lng, GeoMath.bearingDegrees(lat, lng, pose.latitude, pose.longitude)))
    }
}

private fun Earth.EarthState.toMessage(): String = when (this) {
    Earth.EarthState.ENABLED -> ""
    Earth.EarthState.ERROR_NOT_AUTHORIZED ->
        "Location-based AR isn't authorized for this app. Enable the ARCore API in Google Cloud."
    Earth.EarthState.ERROR_RESOURCE_EXHAUSTED -> "Location-based AR is busy right now. Try again later."
    Earth.EarthState.ERROR_APK_VERSION_TOO_OLD -> "Please update Google Play Services for AR."
    else -> "Location-based AR isn't available right now. Use \"Place anywhere\" instead."
}

private fun TerrainAnchorState.toMessage(): String = when (this) {
    TerrainAnchorState.ERROR_UNSUPPORTED_LOCATION ->
        "Location-based AR isn't supported at this spot. Use \"Place anywhere\" instead."
    TerrainAnchorState.ERROR_NOT_AUTHORIZED ->
        "Location-based AR isn't authorized for this app. Enable the ARCore API in Google Cloud."
    else -> "Couldn't place the memorial at its location. Try \"Place anywhere\" instead."
}

private fun TrackingFailureReason?.toMessage(): String? = when (this) {
    null, TrackingFailureReason.NONE -> null
    TrackingFailureReason.BAD_STATE -> "AR tracking error, please restart the AR view"
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark, try moving to a brighter area"
    TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast, slow down"
    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a surface with more texture or detail"
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
}

// ARCore's unavailability exceptions usually carry no message, so describe them by type
private fun Exception.toArUnavailableMessage(): String = when (this) {
    is UnavailableDeviceNotCompatibleException -> "This device doesn't support AR"
    is UnavailableArcoreNotInstalledException,
    is UnavailableUserDeclinedInstallationException -> "Google Play Services for AR is required. Install it from the Play Store to use AR."
    is UnavailableApkTooOldException -> "Please update Google Play Services for AR"
    is UnavailableSdkTooOldException -> "Please update iDedikate to use AR"
    is CameraNotAvailableException -> "The camera is in use by another app"
    else -> "AR couldn't start" + (localizedMessage?.let { ": $it" } ?: "")
}
