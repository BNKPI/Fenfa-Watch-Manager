package com.bantje.fenfawatchmanager.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bantje.fenfawatchmanager.ui.components.AddAppDialog
import com.bantje.fenfawatchmanager.ui.components.AppCard
import com.bantje.fenfawatchmanager.ui.components.PairingDialog
import com.bantje.fenfawatchmanager.ui.components.SettingsDialog
import com.bantje.fenfawatchmanager.ui.components.WatchConnectionCard
import com.bantje.fenfawatchmanager.ui.theme.OrangeWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val watchConfig by viewModel.watchConfig.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val fenfaUrls by viewModel.fenfaUrls.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isScanningWatch by viewModel.isScanningWatch.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val selfUpdateRelease by viewModel.selfUpdateRelease.collectAsState()
    val selfUpdateProgress by viewModel.selfUpdateProgress.collectAsState()
    val isSelfUpdating by viewModel.isSelfUpdating.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Fenfa Watch Manager") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.checkAllApps() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Alle prüfen"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Einstellungen"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "App hinzufügen")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Self-Update Banner falls verfügbar
            selfUpdateRelease?.let { release ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = OrangeWarning)
                                Spacer(modifier = Modifier.padding(start = 8.dp))
                                Text(
                                    text = "Update für Watch Manager verfügbar!",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Version ${release.versionName} (Build ${release.versionCode}) steht auf Fenfa bereit.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!release.changelog.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = release.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            if (isSelfUpdating) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (selfUpdateProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { selfUpdateProgress!! },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.dismissSelfUpdate() }) {
                                    Text("Später")
                                }
                                TextButton(
                                    onClick = {
                                        activity?.let { viewModel.startSelfUpdate(it) }
                                    },
                                    enabled = !isSelfUpdating && activity != null
                                ) {
                                    Text("Jetzt aktualisieren")
                                }
                            }
                        }
                    }
                }
            }

            item {
                WatchConnectionCard(
                    config = watchConfig,
                    isScanning = isScanningWatch,
                    onTestConnection = { viewModel.testWatchConnection() },
                    onScanWatch = { viewModel.scanForWatch() },
                    onOpenPairing = { showPairingDialog = true },
                    onOpenSettings = { showSettingsDialog = true }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Verwaltete Watch-Apps (${apps.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (apps.isEmpty()) {
                item {
                    Text(
                        text = "Keine Apps vorhanden. Tippe auf '+', um eine Watch-App hinzuzufügen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(apps, key = { it.id }) { app ->
                    AppCard(
                        app = app,
                        onCheck = { viewModel.checkApp(app) },
                        onInstall = { viewModel.installApp(app) },
                        onDelete = { viewModel.removeApp(app.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        AddAppDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, slug, pkg ->
                viewModel.addApp(name, slug, pkg)
                showAddDialog = false
            }
        )
    }

    if (showPairingDialog) {
        PairingDialog(
            initialIp = watchConfig.ip,
            initialConnectPort = watchConfig.connectPort,
            onDismiss = { showPairingDialog = false },
            onPair = { ip, pPort, code, cPort ->
                viewModel.pairWatch(ip, pPort, code, cPort)
                showPairingDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            initialIp = watchConfig.ip,
            initialPort = watchConfig.connectPort,
            initialLocalUrl = fenfaUrls.first,
            initialRemoteUrl = fenfaUrls.second,
            onCheckSelfUpdate = { viewModel.checkSelfUpdate() },
            onDismiss = { showSettingsDialog = false },
            onSave = { ip, port, localUrl, remoteUrl ->
                viewModel.saveWatchConfig(ip, port)
                viewModel.saveFenfaUrls(localUrl, remoteUrl)
                showSettingsDialog = false
            }
        )
    }
}
