package com.memoria.idedikate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.memoria.idedikate.ads.BannerAdView
import com.memoria.idedikate.ads.RewardAdType
import com.memoria.idedikate.ads.RewardedAdHelper
import com.memoria.idedikate.model.ArMemorial
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.toArMemorial
import com.memoria.idedikate.ui.ARScreen
import com.memoria.idedikate.ui.AuthViewModel
import com.memoria.idedikate.ui.LoginScreen
import com.memoria.idedikate.ui.MapScreen
import com.memoria.idedikate.ui.MemorialListScreen
import com.memoria.idedikate.ui.Onboarding
import com.memoria.idedikate.ui.OnboardingScreen
import com.memoria.idedikate.ui.OfferingIcons
import com.memoria.idedikate.ui.theme.IDedikateTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.osmdroid.util.GeoPoint

/** [focusLatitude]/[focusLongitude]: a memorial to center on (from the Memorials tab); null centers on the user. */
@Serializable
data class MapRoute(val focusLatitude: Double? = null, val focusLongitude: Double? = null) : NavKey {
    val focus: GeoPoint? get() = if (focusLatitude != null && focusLongitude != null) GeoPoint(focusLatitude, focusLongitude) else null
}

/** [memorial] is the memorial chosen on the map; null when opened from the tab bar. */
@Serializable
data class ARRoute(val memorial: ArMemorial? = null) : NavKey

@Serializable
data object ListRoute : NavKey

@Serializable
data object WalletRoute : NavKey

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // Debug builds use the real rewarded ad units (rewards need AdMob's verification
            // callback), so they must be marked as test devices to get test ads
            val testDeviceIds = BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build())
        }
        MobileAds.initialize(this) {}
        enableEdgeToEdge()
        setContent {
            IDedikateTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(tokenViewModel: TokenViewModel = viewModel(), authViewModel: AuthViewModel = viewModel()) {
    val isUserLoggedIn by authViewModel.isUserLoggedIn.collectAsState()
    val context = LocalContext.current
    // First launch opens with a short guide to what the app is for, before sign-in or any ads
    var showGuide by rememberSaveable { mutableStateOf(!Onboarding.isSeen(context)) }

    if (showGuide) {
        OnboardingScreen(onFinish = {
            Onboarding.markSeen(context)
            showGuide = false
        })
        return
    }

    if (!isUserLoggedIn) {
        val coroutineScope = rememberCoroutineScope()
        val errorMessage by authViewModel.errorMessage.collectAsState()
        val webClientId = stringResource(R.string.default_web_client_id)

        LoginScreen(
            errorMessage = errorMessage,
            onGoogleSignInClick = {
                authViewModel.clearErrorMessage()
                coroutineScope.launch {
                    try {
                        val credentialManager = CredentialManager.create(context)

                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val result = credentialManager.getCredential(
                            request = request,
                            context = context
                        )

                        val credential = result.credential

                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            authViewModel.handleGoogleCredential(googleIdTokenCredential.idToken)
                        } else {
                            authViewModel.setErrorMessage("Unexpected type of credential")
                        }
                    } catch (e: GetCredentialCancellationException) {
                        // User dismissed the account picker; not an error
                        Log.d("Auth", "Google Sign In cancelled by user")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: NoCredentialException) {
                        // Also raised for OAuth misconfiguration (e.g. SHA-1 not registered); check logcat "Auth" tag
                        Log.w("Auth", "No usable Google credential", e)
                        authViewModel.setErrorMessage("Google Sign-In failed. If a Google account is already on this device, this build may not be set up for sign-in yet.")
                    } catch (e: GetCredentialException) {
                        Log.e("Auth", "GetCredentialException", e)
                        authViewModel.setErrorMessage("Google Sign In failed: ${e.message}")
                    } catch (e: Exception) {
                        Log.e("Auth", "Exception", e)
                        authViewModel.setErrorMessage("An error occurred: ${e.message}")
                    }
                }
            },
            onFacebookSignInClick = {
                Toast.makeText(context, "Facebook Sign In coming soon", Toast.LENGTH_SHORT).show()
            },
            onAppleSignInClick = {
                Toast.makeText(context, "Apple Sign In coming soon", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    // Home is the Memorials tab: it explains itself to new users (unlike a bare map asking for
    // location access), and returning users land on their own memorials
    val backStack = rememberNavBackStack(ListRoute)
    val activity = context.findActivity()
    // Other tabs sit on top of Home, so Back returns there before leaving the app
    val selectTab: (NavKey) -> Unit = { route ->
        backStack.clear()
        backStack.add(ListRoute)
        if (route != ListRoute) backStack.add(route)
    }
    // Created once per signed-in session, so ads preloaded here survive switching tabs
    val adHelper = remember { RewardedAdHelper(context) }
    // Pushed on top of the current screen, so Back returns to the map or list it was opened from
    val openInAr: (MemorialItem) -> Unit = { memorial ->
        backStack.add(ARRoute(memorial.toArMemorial()))
    }
    val coroutineScope = rememberCoroutineScope()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    if (showHelp) {
        // Over the current screen rather than replacing it, so tabs and preloaded ads survive
        Dialog(
            onDismissRequest = { showHelp = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            OnboardingScreen(onFinish = { showHelp = false })
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You can sign back in any time. Your wallet is saved to your account.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    coroutineScope.launch {
                        // Clear the saved Google credential first so the account picker shows next time;
                        // signing out removes this screen (and cancels this scope)
                        try {
                            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("Auth", "Failed to clear credential state", e)
                        }
                        authViewModel.signOut()
                    }
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("iDedikate") },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "How iDedikate works")
                    }
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            val currentRoute = backStack.lastOrNull()
            NavigationBar {
                // Order follows the journey: your memorials, place one, visit it, then earn more
                NavigationBarItem(
                    selected = currentRoute is ListRoute,
                    onClick = { if (currentRoute !is ListRoute) selectTab(ListRoute) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Memorials") }
                )
                NavigationBarItem(
                    selected = currentRoute is MapRoute,
                    onClick = { if (currentRoute !is MapRoute) selectTab(MapRoute()) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = currentRoute is ARRoute,
                    onClick = { if (currentRoute !is ARRoute) selectTab(ARRoute()) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    label = { Text("AR View") }
                )
                NavigationBarItem(
                    selected = currentRoute is WalletRoute,
                    onClick = { if (currentRoute !is WalletRoute) selectTab(WalletRoute) },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Wallet") }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        // NavDisplay crashes on an empty back stack; exit instead of popping the last tab
                        if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
                    },
                    entryProvider = { key ->
                        when (key) {
                            is MapRoute -> NavEntry(key) {
                                MapScreen(
                                    tokenViewModel = tokenViewModel,
                                    focus = key.focus,
                                    onViewInAr = openInAr
                                )
                            }
                            is ARRoute -> NavEntry(key) { ARScreen(key.memorial) }
                            is ListRoute -> NavEntry(key) {
                                MemorialListScreen(
                                    onViewInAr = openInAr,
                                    onShowOnMap = { memorial ->
                                        backStack.add(MapRoute(memorial.latitude, memorial.longitude))
                                    },
                                    onPlaceMemorial = { selectTab(MapRoute()) },
                                    onShowGuide = { showHelp = true }
                                )
                            }
                            is WalletRoute -> NavEntry(key) { WalletScreen(tokenViewModel, adHelper) }
                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
            // Not on Home, so a new user's first screen is about their memorials rather than an ad
            if (backStack.lastOrNull() !is ListRoute) BannerAdView()
        }
    }
}


@Composable
fun WalletScreen(tokenViewModel: TokenViewModel, adHelper: RewardedAdHelper) {
    val tokens by tokenViewModel.tokens.collectAsState()
    val plaques by tokenViewModel.plaques.collectAsState()
    val incense by tokenViewModel.incenseSticks.collectAsState()
    val flowers by tokenViewModel.flowers.collectAsState()
    val candles by tokenViewModel.candles.collectAsState()

    val context = LocalContext.current

    val inventoryItems = listOf(
        InventoryItem("General Tokens", Icons.Default.Star, RewardAdType.REWARDED_TOKENS, tokens, isIllustration = false),
        InventoryItem("Memorial Plaque", OfferingIcons.MemorialPlaque, RewardAdType.REWARDED_DISPLAY, plaques),
        InventoryItem("Incense Sticks", OfferingIcons.IncenseStick, RewardAdType.REWARDED_INCENSE, incense),
        InventoryItem("Flowers", OfferingIcons.Flowers, RewardAdType.REWARDED_FLOWERS, flowers),
        InventoryItem("Candles", OfferingIcons.Candle, RewardAdType.REWARDED_CANDLES, candles)
    )

    // Compact rows so every item fits on a typical phone screen; still scrolls on smaller ones
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("Your Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Watch a short ad to earn tokens and offerings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(inventoryItems) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.name,
                            modifier = Modifier.size(if (item.isIllustration) 52.dp else 40.dp),
                            // Illustrations keep their own colors; plain icons follow the theme
                            tint = if (item.isIllustration) Color.Unspecified else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Owned: ${item.balance}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val activity = context.findActivity() ?: return@Button
                            val shown = adHelper.showAd(activity, item.adType) {
                                // AdMob verifies the view with our server, which then credits the wallet
                                Toast.makeText(context, "Reward earned! +${item.adType.configuredRewardAmount} ${item.name} will appear in your wallet shortly.", Toast.LENGTH_LONG).show()
                            }
                            if (!shown) {
                                Toast.makeText(context, "Ad not ready yet, please try again shortly", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Earn +${item.adType.configuredRewardAmount}")
                    }
                }
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

data class InventoryItem(
    val name: String,
    val icon: ImageVector,
    val adType: RewardAdType,
    val balance: Int,
    /** Full-color artwork (see [OfferingIcons]) rather than a single-color Material icon. */
    val isIllustration: Boolean = true
)