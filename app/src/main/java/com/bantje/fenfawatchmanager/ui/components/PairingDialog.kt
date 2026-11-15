package com.bantje.fenfawatchmanager.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.wifi.WifiManager
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bantje.fenfawatchmanager.ui.PairingUiState
import com.bantje.fenfawatchmanager.ui.theme.GreenSuccess

@Composable
fun PairingDialog(
    state: PairingUiState,
    onIpChange: (String) -> Unit,
    onPairingPortChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onConnectPortChange: (String) -> Unit,
    onTestReachability: () -> Unit,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onResetKeys: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiLock = wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "FenfaWatchManager:PairingLock"
        )?.apply {
            setReferenceCounted(false)
            try {
                acquire()
            } catch (_: Exception) {}
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                if (wifiLock?.isHeld == true) {
                    wifiLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!state.isPairing && !state.isConnecting) {
                onDismiss()
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wireless ADB Pairing")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "How to pair:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. On watch: Developer options → Wireless debugging → Tap 'Pair new device'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "2. Keep the pairing dialog OPEN and display AWAKE on the watch while clicking 'Pair & Connect' below!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "📱 Phone screen stays awake automatically while this dialog is open.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ 'Host unreachable'? Wear OS often cuts Wi-Fi when the screen dims or when connected via Bluetooth. Keep watch screen on, or temporarily turn off Bluetooth on phone to force watch onto Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error Banner
                if (!state.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Success Banner
                if (!state.successMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.successMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watch IP Field + Test Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.ip,
                        onValueChange = onIpChange,
                        label = { Text("Watch IP address") },
                        placeholder = { Text("e.g. 192.168.1.150") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isPairing && !state.isConnecting && !state.isPinging,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onTestReachability,
                        enabled = state.ip.isNotBlank() && !state.isPairing && !state.isConnecting && !state.isPinging,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (state.isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test IP")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pairing Port Field
                OutlinedTextField(
                    value = state.pairingPort,
                    onValueChange = onPairingPortChange,
                    label = { Text("Pairing Port (from watch popup)") },
                    placeholder = { Text("e.g. 38472") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isPairing && !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 6-digit Code Field
                OutlinedTextField(
                    value = state.pairingCode,
                    onValueChange = onPairingCodeChange,
                    label = { Text("6-digit Wi-Fi pairing code") },
                    placeholder = { Text("e.g. 123456") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isPairing && !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Connect Port Field
                OutlinedTextField(
                    value = state.connectPort,
                    onValueChange = onConnectPortChange,
                    label = { Text("Connect Port (main Wireless debugging screen)") },
                    placeholder = { Text("e.g. 41235 (visible after navigating back)") },
                    supportingText = {
                        Text("Port changes each time Wireless debugging is toggled on the watch.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isPairing && !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reset ADB keys option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showResetConfirm = true },
                        enabled = !state.isPairing && !state.isConnecting
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset ADB Keys", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            if (state.isPairing) {
                FilledTonalButton(onClick = {}, enabled = false) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairing…")
                }
            } else if (state.isConnecting) {
                FilledTonalButton(onClick = {}, enabled = false) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting…")
                }
            } else if (state.isPairedSuccessfully) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConnect,
                        enabled = state.connectPort.isNotBlank()
                    ) {
                        Text("Connect")
                    }
                }
            } else {
                Button(
                    onClick = onPair,
                    enabled = state.ip.isNotBlank() && state.pairingPort.isNotBlank() && state.pairingCode.isNotBlank()
                ) {
                    Text("Pair & Connect")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isPairing && !state.isConnecting
            ) {
                Text(if (state.isPairedSuccessfully) "Done" else "Cancel")
            }
        }
    )

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset ADB Keypair?") },
            text = {
                Text("This will delete the phone's stored ADB private key and generate a fresh keypair. You will need to pair your watch again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetKeys()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

