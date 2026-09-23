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

@Serializable
data object MapRoute : NavKey

@Serializable
data object ARRoute : NavKey

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
        LoginScreen(
            onGoogleSignInClick = { /* Handle Google Sign In in a real app */ },
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("iDedikate Dashboard") }
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
                            backStack.add(ARRoute)
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
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is MapRoute -> NavEntry(key) { MapScreen() }
                            is ARRoute -> NavEntry(key) { ARScreen() }
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
                                adHelper.showAd(context as Activity, item.adType) { reward, _ ->
                                    tokenViewModel.addItem(item.adType, reward)
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

data class InventoryItem(
    val name: String,
    val icon: ImageVector,
    val adType: RewardAdType,
    val balance: Int
)