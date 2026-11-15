package com.bantje.fenfawatchmanager.ui

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bantje.fenfawatchmanager.BuildConfig
import com.bantje.fenfawatchmanager.data.model.AppInstallStatus
import com.bantje.fenfawatchmanager.data.model.FenfaRelease
import com.bantje.fenfawatchmanager.data.model.ManagedApp
import com.bantje.fenfawatchmanager.data.model.WatchConfig
import com.bantje.fenfawatchmanager.data.repository.AppPreferencesRepository
import com.bantje.fenfawatchmanager.fenfa.FenfaClient
import com.bantje.fenfawatchmanager.update.SelfUpdateManager
import com.bantje.fenfawatchmanager.watch.WatchAdbService
import com.bantje.fenfawatchmanager.watch.WatchDiscoveryService
import com.bantje.fenfawatchmanager.watch.formatAdbError
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PairingUiState(
    val ip: String = "",
    val pairingPort: String = "",
    val pairingCode: String = "",
    val connectPort: String = "",
    val isPairing: Boolean = false,
    val isConnecting: Boolean = false,
    val isPinging: Boolean = false,
    val isPairedSuccessfully: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository = AppPreferencesRepository(application)
    private val fenfaClient = FenfaClient()
    private val watchAdbService = WatchAdbService(application)
    private val discoveryService = WatchDiscoveryService(application)

    private val _watchConfig = MutableStateFlow(WatchConfig())
    val watchConfig: StateFlow<WatchConfig> = _watchConfig.asStateFlow()

    private val _pairingUiState = MutableStateFlow(PairingUiState())
    val pairingUiState: StateFlow<PairingUiState> = _pairingUiState.asStateFlow()

    private val _apps = MutableStateFlow<List<ManagedApp>>(emptyList())
    val apps: StateFlow<List<ManagedApp>> = _apps.asStateFlow()

    private val _fenfaUrls = MutableStateFlow(Pair(BuildConfig.FENFA_LOCAL_URL, BuildConfig.FENFA_REMOTE_URL))
    val fenfaUrls: StateFlow<Pair<String, String>> = _fenfaUrls.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isScanningWatch = MutableStateFlow(false)
    val isScanningWatch: StateFlow<Boolean> = _isScanningWatch.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Self-Update States
    private val _selfUpdateRelease = MutableStateFlow<FenfaRelease?>(null)
    val selfUpdateRelease: StateFlow<FenfaRelease?> = _selfUpdateRelease.asStateFlow()

    private val _selfUpdateProgress = MutableStateFlow<Float?>(null)
    val selfUpdateProgress: StateFlow<Float?> = _selfUpdateProgress.asStateFlow()

    private val _isSelfUpdating = MutableStateFlow(false)
    val isSelfUpdating: StateFlow<Boolean> = _isSelfUpdating.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            repository.watchConfigFlow.collect { config ->
                _watchConfig.value = config
                if (_pairingUiState.value.ip.isBlank() && config.ip.isNotBlank()) {
                    _pairingUiState.value = _pairingUiState.value.copy(
                        ip = config.ip,
                        connectPort = if (config.connectPort > 0) config.connectPort.toString() else _pairingUiState.value.connectPort
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.fenfaUrlsFlow.collect { urls ->
                _fenfaUrls.value = urls
            }
        }
        viewModelScope.launch {
            var isInitialLoad = true
            repository.managedAppsFlow.collect { list ->
                _apps.value = list
                if (isInitialLoad && list.isNotEmpty()) {
                    isInitialLoad = false
                    for (app in list) {
                        checkAppInternal(app)
                    }
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissSelfUpdate() {
        _selfUpdateRelease.value = null
    }

    fun checkSelfUpdate() {
        if (!BuildConfig.ENABLE_SELF_UPDATE) return
        viewModelScope.launch {
            val urls = listOf(_fenfaUrls.value.first, _fenfaUrls.value.second).filter { it.isNotBlank() }
            val release = fenfaClient.fetchLatestRelease(BuildConfig.FENFA_PRODUCT_SLUG, urls).getOrNull()
            if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
                Log.d(TAG, "Self-update available: ${release.versionName} (${release.versionCode})")
                _selfUpdateRelease.value = release
            } else {
                Log.d(TAG, "Manager is up to date: current=${BuildConfig.VERSION_CODE}")
            }
        }
    }

    fun startSelfUpdate(activity: Activity) {
        val release = _selfUpdateRelease.value ?: return
        viewModelScope.launch {
            _isSelfUpdating.value = true
            _statusMessage.value = "Downloading Manager update v${release.versionName}…"
            val manager = SelfUpdateManager(activity, fenfaClient)
            val result = manager.startSelfUpdate(release) { progress ->
                _selfUpdateProgress.value = progress
            }
            _isSelfUpdating.value = false
            _selfUpdateRelease.value = null
            result.onFailure { error ->
                _statusMessage.value = "Manager update failed: ${error.localizedMessage}"
            }
        }
    }

    fun scanForWatch() {
        if (_isScanningWatch.value) return
        _isScanningWatch.value = true
        _statusMessage.value = "Searching for Galaxy Watch on Wi-Fi (mDNS)…"

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            var found = false
            try {
                launch {
                    discoveryService.discoverWatches().collect { watch ->
                        Log.d(TAG, "Watch discovered via mDNS: ${watch.ip}:${watch.port}")
                        found = true
                        _watchConfig.value = _watchConfig.value.copy(
                            ip = watch.ip,
                            connectPort = watch.port
                        )
                        _pairingUiState.value = _pairingUiState.value.copy(
                            ip = watch.ip,
                            connectPort = watch.port.toString()
                        )
                        repository.saveWatchConfig(watch.ip, watch.port)
                        _statusMessage.value = "Watch found at ${watch.ip}:${watch.port}! Connecting…"
                        testWatchConnection()
                    }
                }
                delay(6000L)
                if (!found) {
                    _statusMessage.value = "No watch found on Wi-Fi. Check IP & port manually."
                }
            } finally {
                _isScanningWatch.value = false
            }
        }
    }

    fun testWatchConnection() {
        viewModelScope.launch {
            val config = _watchConfig.value
            if (config.ip.isBlank() || config.connectPort <= 0) {
                _statusMessage.value = "Watch IP or Connect Port not configured. Tap 'IP/Port' or 'Pair'."
                return@launch
            }
            _statusMessage.value = "Testing connection to ${config.ip}:${config.connectPort}…"
            val result = watchAdbService.testConnection(config.ip, config.connectPort)
            result.onSuccess { model ->
                _watchConfig.value = config.copy(deviceModel = model, isConnected = true)
                _statusMessage.value = "Successfully connected to: $model"
                refreshInstalledVersionsOnWatch()
            }.onFailure { error ->
                _watchConfig.value = config.copy(deviceModel = null, isConnected = false)
                _statusMessage.value = formatAdbError(error, config.ip, config.connectPort, isPairing = false)
            }
        }
    }

    fun updatePairingIp(value: String) {
        if (value.contains(":")) {
            val parts = value.split(":")
            val extractedIp = parts[0].trim()
            val extractedPort = if (parts.size > 1) parts[1].trim().filter { it.isDigit() } else ""
            _pairingUiState.value = _pairingUiState.value.copy(
                ip = extractedIp,
                pairingPort = if (extractedPort.isNotBlank()) extractedPort else _pairingUiState.value.pairingPort,
                errorMessage = null
            )
        } else {
            _pairingUiState.value = _pairingUiState.value.copy(ip = value, errorMessage = null)
        }
    }

    fun updatePairingPort(value: String) {
        _pairingUiState.value = _pairingUiState.value.copy(
            pairingPort = value.filter { it.isDigit() },
            errorMessage = null
        )
    }

    fun updatePairingCode(value: String) {
        _pairingUiState.value = _pairingUiState.value.copy(
            pairingCode = value,
            errorMessage = null
        )
    }

    fun updateConnectPort(value: String) {
        _pairingUiState.value = _pairingUiState.value.copy(
            connectPort = value.filter { it.isDigit() },
            errorMessage = null
        )
    }

    fun resetPairingStatus() {
        _pairingUiState.value = _pairingUiState.value.copy(
            errorMessage = null,
            successMessage = null,
            isPairing = false,
            isConnecting = false
        )
    }

    fun resetAdbKeys() {
        viewModelScope.launch {
            val result = watchAdbService.resetKeys()
            result.onSuccess {
                _pairingUiState.value = _pairingUiState.value.copy(
                    isPairedSuccessfully = false,
                    errorMessage = null,
                    successMessage = "ADB keys reset and regenerated. Please pair with your watch again."
                )
                _statusMessage.value = "ADB keys reset successfully."
            }.onFailure { e ->
                _pairingUiState.value = _pairingUiState.value.copy(
                    errorMessage = "Failed to reset ADB keys: ${e.localizedMessage}"
                )
            }
        }
    }

    fun testIpReachability() {
        val ip = _pairingUiState.value.ip.trim()
        if (ip.isBlank()) {
            _pairingUiState.value = _pairingUiState.value.copy(
                errorMessage = "Please enter an IP address first."
            )
            return
        }
        viewModelScope.launch {
            _pairingUiState.value = _pairingUiState.value.copy(
                isPinging = true,
                errorMessage = null,
                successMessage = null
            )
            val reachable = withContext(Dispatchers.IO) {
                try {
                    val address = java.net.InetAddress.getByName(ip)
                    if (address.isReachable(1500)) {
                        true
                    } else {
                        val portToProbe = _pairingUiState.value.pairingPort.toIntOrNull() ?: 5555
                        try {
                            java.net.Socket().use { socket ->
                                socket.connect(java.net.InetSocketAddress(address, portToProbe), 1500)
                                true
                            }
                        } catch (e: java.net.ConnectException) {
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (reachable) {
                _pairingUiState.value = _pairingUiState.value.copy(
                    isPinging = false,
                    successMessage = "🟢 Watch IP ($ip) is reachable on your Wi-Fi!"
                )
            } else {
                _pairingUiState.value = _pairingUiState.value.copy(
                    isPinging = false,
                    errorMessage = "🔴 Host unreachable ($ip)!\n\n" +
                        "1. VPN / Tailscale on phone: If active, disable VPN or enable 'Allow local network access' in Tailscale!\n" +
                        "2. Watch screen asleep: Galaxy Watches disconnect Wi-Fi when screen dims or while Bluetooth is connected. Keep watch display awake!\n" +
                        "3. Tip: Turn off Bluetooth on your phone temporarily to force the watch onto Wi-Fi."
                )
            }
        }
    }

    fun pairWatch() {
        viewModelScope.launch {
            val state = _pairingUiState.value
            val cleanIp = state.ip.trim()
            val pPort = state.pairingPort.toIntOrNull() ?: 0
            val cleanCode = state.pairingCode.filter { it.isDigit() }
            val cPort = state.connectPort.toIntOrNull()

            if (cleanIp.isBlank()) {
                _pairingUiState.value = state.copy(errorMessage = "Please enter the watch IP address.")
                return@launch
            }
            if (pPort <= 0) {
                _pairingUiState.value = state.copy(errorMessage = "Please enter a valid pairing port (e.g. 38472).")
                return@launch
            }
            if (cleanCode.length != 6) {
                _pairingUiState.value = state.copy(errorMessage = "Pairing code must be exactly 6 digits (received ${cleanCode.length} digits).")
                return@launch
            }

            _pairingUiState.value = state.copy(
                isPairing = true,
                errorMessage = null,
                successMessage = null
            )
            _statusMessage.value = "Pairing with $cleanIp:$pPort…"

            val result = watchAdbService.pair(cleanIp, pPort, cleanCode)
            result.onSuccess {
                val currentConfig = _watchConfig.value
                val portToSave = if (cPort != null && cPort > 0) cPort else currentConfig.connectPort
                repository.saveWatchConfig(cleanIp, portToSave)
                _watchConfig.value = currentConfig.copy(ip = cleanIp, connectPort = portToSave)

                if (cPort != null && cPort > 0) {
                    _pairingUiState.value = _pairingUiState.value.copy(
                        isPairing = false,
                        isConnecting = true,
                        isPairedSuccessfully = true,
                        successMessage = "Pairing successful! Connecting to port $cPort…"
                    )
                    _statusMessage.value = "Pairing successful! Connecting to port $cPort…"

                    val connResult = watchAdbService.testConnection(cleanIp, cPort)
                    connResult.onSuccess { model ->
                        _watchConfig.value = _watchConfig.value.copy(deviceModel = model, isConnected = true)
                        _pairingUiState.value = _pairingUiState.value.copy(
                            isConnecting = false,
                            successMessage = "Successfully paired & connected to: $model!"
                        )
                        _statusMessage.value = "Successfully connected to: $model"
                        refreshInstalledVersionsOnWatch()
                    }.onFailure { connErr ->
                        val err = formatAdbError(connErr, cleanIp, cPort, isPairing = false)
                        _pairingUiState.value = _pairingUiState.value.copy(
                            isConnecting = false,
                            errorMessage = "Pairing succeeded! But connection to port $cPort failed:\n$err\n\nTip: The Connect Port is usually DIFFERENT from the Pairing Port. On your watch, navigate one step back to the 'Wireless debugging' screen and verify the port."
                        )
                        _statusMessage.value = "Pairing succeeded, but connection failed."
                    }
                } else {
                    _pairingUiState.value = _pairingUiState.value.copy(
                        isPairing = false,
                        isPairedSuccessfully = true,
                        successMessage = "✅ Pairing successful! Phone is now authorized on the watch.\n\nNow go back one step on your watch to the 'Wireless debugging' screen. Enter the Connect Port shown there and tap 'Connect'."
                    )
                    _statusMessage.value = "Pairing successful! Please enter Connect Port."
                }
            }.onFailure { error ->
                val errorMsg = formatAdbError(error, cleanIp, pPort, isPairing = true)
                _pairingUiState.value = _pairingUiState.value.copy(
                    isPairing = false,
                    errorMessage = errorMsg
                )
                _statusMessage.value = "Pairing failed: $errorMsg"
            }
        }
    }

    fun connectFromPairingDialog() {
        viewModelScope.launch {
            val state = _pairingUiState.value
            val cleanIp = state.ip.trim()
            val cPort = state.connectPort.toIntOrNull() ?: 0

            if (cleanIp.isBlank() || cPort <= 0) {
                _pairingUiState.value = state.copy(errorMessage = "Please enter a valid IP address and Connect Port.")
                return@launch
            }

            _pairingUiState.value = state.copy(isConnecting = true, errorMessage = null)
            _statusMessage.value = "Connecting to $cleanIp:$cPort…"

            val result = watchAdbService.testConnection(cleanIp, cPort)
            result.onSuccess { model ->
                repository.saveWatchConfig(cleanIp, cPort)
                _watchConfig.value = _watchConfig.value.copy(ip = cleanIp, connectPort = cPort, deviceModel = model, isConnected = true)
                _pairingUiState.value = _pairingUiState.value.copy(
                    isConnecting = false,
                    successMessage = "Successfully connected to: $model!"
                )
                _statusMessage.value = "Successfully connected to: $model"
                refreshInstalledVersionsOnWatch()
            }.onFailure { error ->
                _watchConfig.value = _watchConfig.value.copy(deviceModel = null, isConnected = false)
                val errorMsg = formatAdbError(error, cleanIp, cPort, isPairing = false)
                _pairingUiState.value = _pairingUiState.value.copy(
                    isConnecting = false,
                    errorMessage = errorMsg
                )
                _statusMessage.value = errorMsg
            }
        }
    }

    fun saveWatchConfig(ip: String, port: Int) {
        viewModelScope.launch {
            repository.saveWatchConfig(ip, port)
            _watchConfig.value = _watchConfig.value.copy(ip = ip, connectPort = port)
            testWatchConnection()
        }
    }

    fun saveFenfaUrls(localUrl: String, remoteUrl: String) {
        viewModelScope.launch {
            repository.saveFenfaUrls(localUrl, remoteUrl)
            _fenfaUrls.value = Pair(localUrl, remoteUrl)
            checkAllApps()
        }
    }

    fun addApp(name: String, slug: String, packageName: String) {
        viewModelScope.launch {
            val newApp = ManagedApp(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                slug = slug.trim().lowercase(),
                packageName = packageName.trim()
            )
            val updated = _apps.value + newApp
            _apps.value = updated
            repository.saveManagedApps(updated)
            checkApp(newApp)
        }
    }

    fun removeApp(appId: String) {
        viewModelScope.launch {
            val updated = _apps.value.filter { it.id != appId }
            _apps.value = updated
            repository.saveManagedApps(updated)
        }
    }

    fun checkAllApps() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                checkSelfUpdate()
                val currentApps = _apps.value
                for (app in currentApps) {
                    checkAppInternal(app)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun checkApp(app: ManagedApp) {
        viewModelScope.launch {
            checkAppInternal(app)
        }
    }

    private suspend fun checkAppInternal(app: ManagedApp) {
        updateAppInList(app.copy(status = AppInstallStatus.Checking))

        val urls = listOf(_fenfaUrls.value.first, _fenfaUrls.value.second).filter { it.isNotBlank() }
        val releaseResult = fenfaClient.fetchLatestRelease(app.slug, urls)

        val config = _watchConfig.value
        val installedVersion = if (config.isConnected || config.ip.isNotBlank()) {
            watchAdbService.getInstalledVersion(config.ip, config.connectPort, app.packageName).getOrNull()
        } else {
            null
        }

        val updatedInstalledCode = installedVersion?.first ?: app.installedVersionCode
        val updatedInstalledName = installedVersion?.second ?: app.installedVersionName

        releaseResult.onSuccess { release ->
            val hasUpdate = updatedInstalledCode == null || release.versionCode > updatedInstalledCode
            val status = if (hasUpdate) {
                AppInstallStatus.UpdateAvailable(release.versionName, release.versionCode)
            } else {
                AppInstallStatus.UpToDate
            }

            val updatedApp = app.copy(
                installedVersionCode = updatedInstalledCode,
                installedVersionName = updatedInstalledName,
                latestVersionCode = release.versionCode,
                latestVersionName = release.versionName,
                latestReleaseId = release.releaseId,
                downloadBaseUrl = release.downloadBaseUrl,
                changelog = release.changelog,
                status = status
            )
            updateAppInList(updatedApp)
            saveCurrentApps()

            if (hasUpdate && app.autoUpdate && config.isConnected) {
                installApp(updatedApp)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed checking app ${app.name}", error)
            val updatedApp = app.copy(
                installedVersionCode = updatedInstalledCode,
                installedVersionName = updatedInstalledName,
                status = AppInstallStatus.Error("Fenfa error: ${error.localizedMessage}")
            )
            updateAppInList(updatedApp)
        }
    }

    fun installApp(app: ManagedApp) {
        viewModelScope.launch {
            val releaseId = app.latestReleaseId
            val baseUrl = app.downloadBaseUrl
            val versionCode = app.latestVersionCode
            if (releaseId.isNullOrBlank() || baseUrl.isNullOrBlank() || versionCode == null) {
                updateAppInList(app.copy(status = AppInstallStatus.Error("No release available to install")))
                return@launch
            }

            val downloadUrl = "${baseUrl.trimEnd('/')}/d/$releaseId"
            val cacheDir = getApplication<Application>().cacheDir
            val apkFile = File(cacheDir, "${app.slug}-$versionCode.apk")

            updateAppInList(app.copy(status = AppInstallStatus.Downloading(null)))

            val downloadResult = fenfaClient.downloadApk(downloadUrl, apkFile) { copied, total ->
                val progress = if (total > 0) copied.toFloat() / total.toFloat() else null
                updateAppInList(app.copy(status = AppInstallStatus.Downloading(progress)))
            }

            downloadResult.onSuccess { downloadedApk ->
                updateAppInList(app.copy(status = AppInstallStatus.Installing))
                val config = _watchConfig.value
                val installResult = watchAdbService.installApk(config.ip, config.connectPort, downloadedApk)

                downloadedApk.delete()

                installResult.onSuccess {
                    val updatedApp = app.copy(
                        installedVersionCode = versionCode,
                        installedVersionName = app.latestVersionName ?: versionCode.toString(),
                        status = AppInstallStatus.Success("Successfully installed on watch!")
                    )
                    updateAppInList(updatedApp)
                    saveCurrentApps()
                    _statusMessage.value = "${app.name} v${app.latestVersionName} successfully installed on watch!"
                }.onFailure { error ->
                    updateAppInList(app.copy(status = AppInstallStatus.Error("Install error: ${error.localizedMessage}")))
                    _statusMessage.value = "Installation of ${app.name} failed"
                }
            }.onFailure { error ->
                updateAppInList(app.copy(status = AppInstallStatus.Error("Download error: ${error.localizedMessage}")))
            }
        }
    }

    private suspend fun refreshInstalledVersionsOnWatch() {
        val config = _watchConfig.value
        if (!config.isConnected) return
        val currentApps = _apps.value
        for (app in currentApps) {
            val installed = watchAdbService.getInstalledVersion(config.ip, config.connectPort, app.packageName).getOrNull()
            if (installed != null) {
                val hasUpdate = app.latestVersionCode != null && app.latestVersionCode > installed.first
                val status = if (hasUpdate) {
                    AppInstallStatus.UpdateAvailable(app.latestVersionName ?: "", app.latestVersionCode ?: 0)
                } else if (app.latestVersionCode != null) {
                    AppInstallStatus.UpToDate
                } else {
                    app.status
                }
                updateAppInList(app.copy(
                    installedVersionCode = installed.first,
                    installedVersionName = installed.second,
                    status = status
                ))
            }
        }
        saveCurrentApps()
    }

    private fun updateAppInList(updatedApp: ManagedApp) {
        val list = _apps.value.toMutableList()
        val index = list.indexOfFirst { it.id == updatedApp.id }
        if (index >= 0) {
            list[index] = updatedApp
            _apps.value = list
        }
    }

    private fun saveCurrentApps() {
        viewModelScope.launch {
            repository.saveManagedApps(_apps.value)
        }
    }
}
