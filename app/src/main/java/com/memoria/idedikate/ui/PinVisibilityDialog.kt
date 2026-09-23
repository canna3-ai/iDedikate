package com.memoria.idedikate.ui

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.memoria.idedikate.model.PinVisibility

private const val MAX_SHARED_EMAILS = 20

private fun PinVisibility.description(): String = when (this) {
    PinVisibility.PRIVATE -> "Only you can see this memorial"
    PinVisibility.SHARED -> "You and the people you add by email"
    PinVisibility.PUBLIC -> "Everyone using iDedikate"
}

/**
 * Picks who can see a pin. Used both when dropping a new pin and when the owner edits one
 * (pass [onDelete] to show the delete action).
 */
@Composable
fun PinVisibilityDialog(
    title: String,
    confirmLabel: String,
    ownEmail: String?,
    onConfirm: (PinVisibility, List<String>) -> Unit,
    onDismiss: () -> Unit,
    initialVisibility: PinVisibility = PinVisibility.PUBLIC,
    initialSharedWith: List<String> = emptyList(),
    onDelete: (() -> Unit)? = null
) {
    var visibility by remember { mutableStateOf(initialVisibility) }
    val sharedWith = remember { mutableStateListOf<String>().apply { addAll(initialSharedWith) } }
    var emailInput by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Validates and adds the typed email. Returns false if the input was invalid.
    fun addEmail(): Boolean {
        val email = emailInput.trim().lowercase()
        if (email.isEmpty()) return true
        emailError = when {
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address"
            email == ownEmail -> "You can always see your own memorials"
            email in sharedWith -> "Already added"
            sharedWith.size >= MAX_SHARED_EMAILS -> "You can share with up to $MAX_SHARED_EMAILS people"
            else -> null
        }
        if (emailError != null) return false
        sharedWith += email
        emailInput = ""
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.selectableGroup()) {
                    PinVisibility.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = visibility == option,
                                    onClick = { visibility = option },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = visibility == option, onClick = null)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    option.description(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (visibility == PinVisibility.SHARED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            emailError = null
                        },
                        label = { Text("Google account email") },
                        singleLine = true,
                        isError = emailError != null,
                        supportingText = emailError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addEmail() }),
                        trailingIcon = {
                            IconButton(onClick = { addEmail() }) {
                                Icon(Icons.Default.Add, contentDescription = "Add person")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    sharedWith.forEach { email ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(email, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { sharedWith.remove(email) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove $email")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // A shared pin needs at least one person (a typed-but-not-added email counts)
                enabled = visibility != PinVisibility.SHARED || sharedWith.isNotEmpty() || emailInput.isNotBlank(),
                onClick = {
                    // Include an email that was typed but not yet added
                    if (visibility == PinVisibility.SHARED && !addEmail()) return@TextButton
                    onConfirm(visibility, if (visibility == PinVisibility.SHARED) sharedWith.toList() else emptyList())
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(if (confirmDelete) "Confirm delete" else "Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
