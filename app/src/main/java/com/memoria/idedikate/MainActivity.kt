package com.memoria.idedikate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds
import com.memoria.idedikate.ui.MapScreen
import com.memoria.idedikate.ui.ARScreen
import com.memoria.idedikate.ui.theme.IDedikateTheme
import kotlinx.serialization.Serializable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import com.memoria.idedikate.ads.RewardedAdHelper
import com.memoria.idedikate.ads.RewardAdType
import com.memoria.idedikate.ads.BannerAdView
import androidx.compose.runtime.remember
import android.app.Activity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import android.widget.Toast
import com.memoria.idedikate.ui.LoginScreen
import com.memoria.idedikate.ui.AuthViewModel
import com.memoria.idedikate.model.ArMemorial
import com.memoria.idedikate.model.toArMemorial
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import android.content.Context
import android.content.ContextWrapper
import kotlin.coroutines.cancellation.CancellationException
import androidx.credentials.ClearCredentialStateRequest
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Serializable
data object MapRoute : NavKey

@Serializable
/** [memorial] is the memorial chosen on the map; null when opened from the tab bar. */
data class ARRoute(val memorial: ArMemorial? = null) : NavKey

@Serializable
data object ListRoute : NavKey

@Serializable
data object WalletRoute : NavKey

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    
    if (!isUserLoggedIn) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val errorMessage by authViewModel.errorMessage.collectAsState()
        
        LoginScreen(
            errorMessage = errorMessage,
            onGoogleSignInClick = {
                authViewModel.clearErrorMessage()
                coroutineScope.launch {
                    try {
                        val credentialManager = CredentialManager.create(context)
                        
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(context.getString(R.string.default_web_client_id))
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
                        authViewModel.setErrorMessage("Couldn't find a Google account to sign in with. Make sure one is added in Settings, then try again.")
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

    val backStack = rememberNavBackStack(MapRoute)
    val context = LocalContext.current
    val activity = context.findActivity()
    val coroutineScope = rememberCoroutineScope()
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You can sign back in any time. Your wallet stays saved on this device.") },
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
                title = { Text("iDedikate Dashboard") },
                actions = {
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            val currentRoute = backStack.lastOrNull()
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute is MapRoute,
                    onClick = {
                        if (currentRoute !is MapRoute) {
                            backStack.clear()
                            backStack.add(MapRoute)
                        }
                    },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = currentRoute is ARRoute,
                    onClick = {
                        if (currentRoute !is ARRoute) {
                            backStack.clear()
                            backStack.add(ARRoute())
                        }
                    },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "AR") },
                    label = { Text("AR") }
                )
                NavigationBarItem(
                    selected = currentRoute is ListRoute,
                    onClick = {
                        if (currentRoute !is ListRoute) {
                            backStack.clear()
                            backStack.add(ListRoute)
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "List") },
                    label = { Text("List") }
                )
                NavigationBarItem(
                    selected = currentRoute is WalletRoute,
                    onClick = {
                        if (currentRoute !is WalletRoute) {
                            backStack.clear()
                            backStack.add(WalletRoute)
                        }
                    },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
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
                                    onViewInAr = { memorial ->
                                        backStack.clear()
                                        backStack.add(ARRoute(memorial.toArMemorial()))
                                    }
                                )
                            }
                            is ARRoute -> NavEntry(key) { ARScreen(key.memorial) }
                            is ListRoute -> NavEntry(key) { ListScreen() }
                            is WalletRoute -> NavEntry(key) { WalletScreen(tokenViewModel) }
                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
            BannerAdView()
        }
    }
}


@Composable
fun ListScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.padding(16.dp))
            Text("List View", style = MaterialTheme.typography.headlineMedium)
            Text("List of memorials will go here")
        }
    }
}

@Composable
fun WalletScreen(tokenViewModel: TokenViewModel) {
    val tokens by tokenViewModel.tokens.collectAsState()
    val plaques by tokenViewModel.plaques.collectAsState()
    val incense by tokenViewModel.incenseSticks.collectAsState()
    val fruits by tokenViewModel.fruitOfferings.collectAsState()
    val food by tokenViewModel.foodOfferings.collectAsState()

    val context = LocalContext.current
    val adHelper = remember { RewardedAdHelper(context) }
    
    val inventoryItems = listOf(
        InventoryItem("General Tokens", Icons.Default.Star, RewardAdType.REWARDED_TOKENS, tokens),
        InventoryItem("Memorial Plaque", Icons.Default.Dashboard, RewardAdType.REWARDED_DISPLAY, plaques),
        InventoryItem("Incense Sticks", Icons.Default.LocalFireDepartment, RewardAdType.REWARDED_INCENSE, incense),
        InventoryItem("Fruit Offerings", Icons.Default.LocalFlorist, RewardAdType.REWARDED_FRUITS, fruits),
        InventoryItem("Food Offerings", Icons.Default.Restaurant, RewardAdType.REWARDED_FOOD, food)
    )
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text("Your Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Manage your tokens and offerings", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Text(
            text = "Inventory Items",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(inventoryItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.name,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Owned: ${item.balance}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val activity = context.findActivity() ?: return@Button
                                val shown = adHelper.showAd(activity, item.adType) { reward, _ ->
                                    tokenViewModel.addItem(item.adType, reward)
                                }
                                if (!shown) {
                                    Toast.makeText(context, "Ad not ready yet, please try again shortly", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Earn", textAlign = TextAlign.Center)
                        }
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
    val balance: Int
)