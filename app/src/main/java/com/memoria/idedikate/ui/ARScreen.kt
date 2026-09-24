package com.memoria.idedikate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.memoria.idedikate.ar.MemorialItems
import com.memoria.idedikate.model.ArMemorial
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener

@Composable
fun ARScreen(memorial: ArMemorial?) {
    if (memorial == null) {
        // Opened from the tab bar: there's nothing to place until a memorial is chosen on the map
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Choose a memorial on the Map, then tap \"View in AR\" to place it here.",
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required for AR", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        var status by remember { mutableStateOf("Move your phone slowly to find a flat surface") }

        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneViewCompose(memorial = memorial, onStatusChange = { status = it })

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(text = status, color = Color.White)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required.")
        }
    }
}

@Composable
fun ARSceneViewCompose(memorial: ArMemorial, onStatusChange: (String) -> Unit) {
    val layout = remember(memorial) { MemorialItems.layoutFor(memorial.offerings) }
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val childNodes = rememberNodes()
    // Only read inside callbacks, so updating it every frame doesn't recompose
    var frame by remember { mutableStateOf<Frame?>(null) }
    var hasPlaced by remember { mutableStateOf(false) }
    var isTrackingPlane by remember { mutableStateOf(false) }

    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        materialLoader = materialLoader,
        childNodes = childNodes,
        planeRenderer = !hasPlaced,
        sessionConfiguration = { session, config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            config.depthMode =
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC
                else Config.DepthMode.DISABLED
        },
        onSessionFailed = { exception ->
            Log.e("ARScreen", "AR session failed", exception)
            onStatusChange(exception.toArUnavailableMessage())
        },
        onSessionUpdated = { _, updatedFrame ->
            frame = updatedFrame
            if (!hasPlaced && !isTrackingPlane &&
                updatedFrame.getUpdatedTrackables(Plane::class.java).isNotEmpty()
            ) {
                isTrackingPlane = true
                onStatusChange("Tap a surface to place \"${memorial.title}\"")
            }
        },
        onTrackingFailureChanged = { reason ->
            if (!hasPlaced) {
                onStatusChange(reason.toMessage() ?: "Move your phone slowly to find a flat surface")
            }
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                if (hasPlaced || node != null) return@rememberOnGestureListener
                val hit = frame?.hitTest(motionEvent)?.firstOrNull { hitResult ->
                    val plane = hitResult.trackable as? Plane
                    plane != null && plane.isPoseInPolygon(hitResult.hitPose)
                } ?: return@rememberOnGestureListener
                val anchor = runCatching { hit.createAnchor() }.getOrNull() ?: return@rememberOnGestureListener

                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                MemorialItems.renderOfferings(engine, materialLoader, anchorNode, layout)
                childNodes += anchorNode
                hasPlaced = true
                onStatusChange("${memorial.title} · ${memorial.offerings.summary()}")
            }
        )
    )
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
