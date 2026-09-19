package com.bantje.fenfawatchmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PairingDialog(
    initialIp: String,
    initialConnectPort: Int,
    onDismiss: () -> Unit,
    onPair: (ip: String, pairingPort: Int, pairingCode: String, connectPort: Int?) -> Unit
) {
    var ip by remember { mutableStateOf(initialIp) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf(if (initialConnectPort > 0) initialConnectPort.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with Galaxy Watch") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "1. On watch: Developer options → Wireless debugging → 'Pair new device'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Watch IP address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = { pairingPort = it },
                    label = { Text("Pairing port (from watch pairing dialog)") },
                    placeholder = { Text("e.g. 38472") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("6-digit Wi-Fi pairing code") },
                    placeholder = { Text("e.g. 123456") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = connectPort,
                    onValueChange = { connectPort = it },
                    label = { Text("Connect port (from Wireless debugging screen)") },
                    placeholder = { Text("e.g. 41235 (visible after navigating back)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pPort = pairingPort.toIntOrNull() ?: 0
                    val cPort = connectPort.toIntOrNull()
                    if (ip.isNotBlank() && pPort > 0 && pairingCode.isNotBlank()) {
                        onPair(ip.trim(), pPort, pairingCode.trim(), cPort)
                    }
                },
                enabled = ip.isNotBlank() && pairingPort.isNotBlank() && pairingCode.isNotBlank()
            ) {
                Text("Pair & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
