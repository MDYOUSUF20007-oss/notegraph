package com.notegraph.app.ui.common

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Lightweight persistent vault PIN manager for locked notes (`NoteEntity.isLocked`).
 */
object NoteSecurityManager {
    private const val PREFS_NAME = "notegraph_prefs"
    private const val KEY_PIN = "vault_lock_pin"

    fun hasPin(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !sp.getString(KEY_PIN, null).isNullOrBlank()
    }

    fun setPin(context: Context, pin: String) {
        val clean = pin.trim()
        if (clean.isNotEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PIN, clean)
                .apply()
        }
    }

    fun verifyPin(context: Context, attempt: String): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = sp.getString(KEY_PIN, null) ?: return true
        return attempt.trim() == stored
    }
}

@Composable
fun VaultPinDialog(
    isSettingNewPin: Boolean,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSuccess: (enteredPin: String) -> Unit,
    onVerify: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 8) {
                            pinInput = it
                            errorMsg = null
                        }
                    },
                    label = { Text(if (isSettingNewPin) "Enter 4–8 digit PIN" else "Enter Vault PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = pinInput.trim()
                    if (isSettingNewPin) {
                        if (clean.length < 4) {
                            errorMsg = "PIN must be at least 4 digits"
                        } else {
                            onSuccess(clean)
                        }
                    } else {
                        if (onVerify(clean)) {
                            onSuccess(clean)
                        } else {
                            errorMsg = "Incorrect PIN"
                        }
                    }
                }
            ) {
                Text(if (isSettingNewPin) "Save PIN & Lock" else "Unlock")
            }
        }
    )
}
