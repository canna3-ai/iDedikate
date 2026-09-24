package com.memoria.idedikate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.PinVisibility
import java.text.DateFormat
import java.util.Date

/** Colour used for a memorial's visibility on its map marker and in the list. */
fun PinVisibility.color(): Color = when (this) {
    PinVisibility.PUBLIC -> Color(0xFFD32F2F)
    PinVisibility.SHARED -> Color(0xFF2E7D32)
    PinVisibility.PRIVATE -> Color(0xFF7B1FA2)
}

/** The user's memorials and those shared with them, anywhere (the map only loads the visible area). */
@Composable
fun MemorialListScreen(
    onViewInAr: (MemorialItem) -> Unit,
    onShowOnMap: (MemorialItem) -> Unit,
    mapViewModel: MapViewModel = viewModel()
) {
    val mine by mapViewModel.myMemorials.collectAsState()
    val shared by mapViewModel.sharedWithMe.collectAsState()

    if (mine.isEmpty() && shared.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No memorials yet. Place one from the Map tab with \"Place memorial\".",
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { SectionHeader("My memorials (${mine.size})") }
        if (mine.isEmpty()) {
            item { Text("You haven't placed any memorials yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(mine, key = { "mine-${it.id}" }) { memorial ->
            MemorialCard(memorial, isOwn = true, onViewInAr = onViewInAr, onShowOnMap = onShowOnMap)
        }

        if (shared.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(8.dp)); SectionHeader("Shared with me (${shared.size})") }
            items(shared, key = { "shared-${it.id}" }) { memorial ->
                MemorialCard(memorial, isOwn = false, onViewInAr = onViewInAr, onShowOnMap = onShowOnMap)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MemorialCard(
    memorial: MemorialItem,
    isOwn: Boolean,
    onViewInAr: (MemorialItem) -> Unit,
    onShowOnMap: (MemorialItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                OfferingIcons.MemorialPlaque,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(memorial.message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(memorial.visibility.color(), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        visibilityText(memorial, isOwn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(memorial.offerings.summary(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    placedText(memorial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onViewInAr(memorial) }) {
                        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View in AR")
                    }
                    OutlinedButton(onClick = { onShowOnMap(memorial) }) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Map")
                    }
                }
            }
        }
    }
}

private fun visibilityText(memorial: MemorialItem, isOwn: Boolean): String = when {
    !isOwn -> "Shared with you"
    memorial.visibility == PinVisibility.SHARED -> "Shared with ${memorial.sharedWith.size}"
    else -> memorial.visibility.label
}

private fun placedText(memorial: MemorialItem): String =
    if (memorial.createdAtMillis == 0L) "Placing…"
    else "Placed " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(memorial.createdAtMillis))
