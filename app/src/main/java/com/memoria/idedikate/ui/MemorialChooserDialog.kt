package com.memoria.idedikate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memoria.idedikate.model.MemorialItem
import com.memoria.idedikate.model.PinVisibility

/**
 * Lists the memorials behind a stacked map marker so the user can pick one. [onZoomIn] is null
 * when they're all at the same spot, where zooming wouldn't separate them.
 */
@Composable
fun MemorialChooserDialog(
    group: MemorialGroup,
    currentUid: String?,
    onChoose: (MemorialItem) -> Unit,
    onZoomIn: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group.isSameSpot) "${group.size} memorials at this spot" else "${group.size} memorials nearby") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                group.memorials.forEachIndexed { index, memorial ->
                    if (index > 0) HorizontalDivider()
                    ChooserRow(memorial, isOwn = memorial.ownerUid == currentUid, onClick = { onChoose(memorial) })
                }
            }
        },
        confirmButton = {
            if (onZoomIn != null) {
                TextButton(onClick = onZoomIn) { Text("Zoom in") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChooserRow(memorial: MemorialItem, isOwn: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(
            OfferingIcons.MemorialPlaque,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(memorial.message, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(memorial.visibility.color(), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    whoCanSee(memorial, isOwn),
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
        }
        // Own memorials open their options; others open in AR
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isOwn) "Options" else "View in AR",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun whoCanSee(memorial: MemorialItem, isOwn: Boolean): String = when {
    !isOwn && memorial.visibility == PinVisibility.SHARED -> "Shared with you"
    !isOwn -> memorial.visibility.label
    memorial.visibility == PinVisibility.SHARED -> "Yours · shared with ${memorial.sharedWith.size}"
    else -> "Yours · ${memorial.visibility.label}"
}
