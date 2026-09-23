package com.memoria.idedikate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.memoria.idedikate.ar.MemorialItemType
import com.memoria.idedikate.ar.MemorialItems
import com.memoria.idedikate.ar.Offset

@Composable
fun ARScreen() {
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
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneViewCompose()
            
            // Simulating AR Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Virtual Memorial (GPS Location)",
                    color = Color.White
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required.")
        }
    }
}

@Composable
fun ARSceneViewCompose() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    if (activity != null) {
        val fragmentManager = activity.supportFragmentManager
        val containerId = remember { View.generateViewId() }
        
        AndroidView(
            factory = { ctx ->
                val view = FragmentContainerView(ctx)
                view.id = containerId
                
                val fragment = ArFragment()
                
                var hasPlaced = false
                fragment.setOnTapArPlaneListener { hitResult, _, _ ->
                    if (hasPlaced) return@setOnTapArPlaneListener
                    hasPlaced = true
                    
                    val anchor = hitResult.createAnchor()
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(fragment.arSceneView.scene)
                    
                    val items = listOf(
                        MemorialItemType.PLAQUE to Offset(0f, 0f, 0f, 0f),
                        MemorialItemType.FRUIT_OFFERING to Offset(-0.3f, 0f, 0.2f, 0f),
                        MemorialItemType.FOOD_OFFERING to Offset(0.3f, 0f, 0.2f, 0f),
                        MemorialItemType.CANDLE to Offset(-0.4f, 0f, 0.1f, 0f),
                        MemorialItemType.CANDLE to Offset(0.4f, 0f, 0.1f, 0f),
                        MemorialItemType.INCENSE_POT to Offset(0f, 0f, 0.25f, 0f),
                        MemorialItemType.INCENSE_STICK to Offset(0f, 0.05f, 0.25f, 0f)
                    )
                    
                    MemorialItems.renderOfferings(ctx, anchorNode, items, null)
                }
                
                fragmentManager.beginTransaction()
                    .replace(containerId, fragment, "AR_FRAGMENT_$containerId")
                    .commit()
                
                view
            },
            onRelease = {
                val fragment = fragmentManager.findFragmentByTag("AR_FRAGMENT_$containerId")
                if (fragment != null) {
                    fragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("AR requires a FragmentActivity")
        }
    }
}
