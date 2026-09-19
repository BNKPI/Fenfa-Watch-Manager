package com.bantje.fenfawatchmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bantje.fenfawatchmanager.data.model.AppInstallStatus
import com.bantje.fenfawatchmanager.data.model.ManagedApp
import com.bantje.fenfawatchmanager.ui.theme.GreenSuccess
import com.bantje.fenfawatchmanager.ui.theme.OrangeWarning

@Composable
fun AppCard(
    app: ManagedApp,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Slug: ${app.slug} • ${app.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove app",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Version info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Watch (installed):", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = if (app.installedVersionCode != null) {
                            "${app.installedVersionName ?: "v?"} (Build ${app.installedVersionCode})"
                        } else {
                            "Not found / Unknown"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Fenfa (latest):", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = if (app.latestVersionCode != null) {
                            "${app.latestVersionName ?: "v?"} (Build ${app.latestVersionCode})"
                        } else {
                            "—"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (app.hasUpdate) OrangeWarning else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!app.changelog.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${app.changelog}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status display & progress
            when (val status = app.status) {
                is AppInstallStatus.Checking -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking Fenfa & Watch…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is AppInstallStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Downloading APK…", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (status.progress != null) {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is AppInstallStatus.Installing -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installing on Galaxy Watch via ADB…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is AppInstallStatus.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                }
                is AppInstallStatus.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(onClick = onCheck) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check")
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (app.hasUpdate) {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install on Watch")
                    }
                } else if (app.latestReleaseId != null) {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reinstall")
                    }
                }
            }
        }
    }
}
