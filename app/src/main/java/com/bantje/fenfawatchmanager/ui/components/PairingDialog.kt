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
    var ip by remember { mutableStateOf(if (initialIp == "192.168.178.50") "" else initialIp) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf(if (initialConnectPort > 0 && initialConnectPort != 41235) initialConnectPort.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mit Galaxy Watch koppeln") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "1. Auf der Watch in: Entwickleroptionen → Drahtloses Debuggen → 'Neues Gerät koppeln'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Watch IP-Adresse") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = { pairingPort = it },
                    label = { Text("Pairing-Port (aus dem Koppeln-Fenster)") },
                    placeholder = { Text("z. B. 38472") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("6-stelliger WLAN-Kopplungscode") },
                    placeholder = { Text("z. B. 123456") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = connectPort,
                    onValueChange = { connectPort = it },
                    label = { Text("Connect-Port (von der Seite 'Drahtloses Debuggen')") },
                    placeholder = { Text("z. B. 41235 (nach Zurückgehen sichtbar)") },
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
                Text("Koppeln & Verbinden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
