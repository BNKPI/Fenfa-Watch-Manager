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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
            }
        }
        viewModelScope.launch {
            repository.fenfaUrlsFlow.collect { urls ->
                _fenfaUrls.value = urls
            }
        }
        viewModelScope.launch {
            repository.managedAppsFlow.collect { list ->
                _apps.value = list
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
            _statusMessage.value = "Lade Manager-Update v${release.versionName} herunter…"
            val manager = SelfUpdateManager(activity, fenfaClient)
            val result = manager.startSelfUpdate(release) { progress ->
                _selfUpdateProgress.value = progress
            }
            _isSelfUpdating.value = false
            _selfUpdateRelease.value = null
            result.onFailure { error ->
                _statusMessage.value = "Manager-Update fehlgeschlagen: ${error.localizedMessage}"
            }
        }
    }

    fun scanForWatch() {
        if (_isScanningWatch.value) return
        _isScanningWatch.value = true
        _statusMessage.value = "Suche Galaxy Watch im WLAN (mDNS)…"

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
                        repository.saveWatchConfig(watch.ip, watch.port)
                        _statusMessage.value = "Watch gefunden auf ${watch.ip}:${watch.port}! Verbinde…"
                        testWatchConnection()
                    }
                }
                delay(6000L)
                if (!found) {
                    _statusMessage.value = "Keine Watch im WLAN gefunden. Prüfe IP & Port manuell."
                }
            } finally {
                _isScanningWatch.value = false
            }
        }
    }

    fun testWatchConnection() {
        viewModelScope.launch {
            val config = _watchConfig.value
            _statusMessage.value = "Prüfe Verbindung zu ${config.ip}:${config.connectPort}…"
            val result = watchAdbService.testConnection(config.ip, config.connectPort)
            result.onSuccess { model ->
                _watchConfig.value = config.copy(deviceModel = model, isConnected = true)
                _statusMessage.value = "Erfolgreich verbunden mit: $model"
                refreshInstalledVersionsOnWatch()
            }.onFailure { error ->
                _watchConfig.value = config.copy(deviceModel = null, isConnected = false)
                _statusMessage.value = "Verbindung zu ${config.ip}:${config.connectPort} fehlgeschlagen. Bitte Connect-Port auf der Uhr prüfen!"
            }
        }
    }

    fun pairWatch(ip: String, pairingPort: Int, pairingCode: String, connectPort: Int?) {
        viewModelScope.launch {
            _statusMessage.value = "Kopple mit $ip:$pairingPort…"
            val result = watchAdbService.pair(ip, pairingPort, pairingCode)
            result.onSuccess {
                val currentConfig = _watchConfig.value
                val portToUse = if (connectPort != null && connectPort > 0) connectPort else currentConfig.connectPort
                repository.saveWatchConfig(ip, portToUse)
                _watchConfig.value = currentConfig.copy(ip = ip, connectPort = portToUse)
                if (connectPort != null && connectPort > 0) {
                    _statusMessage.value = "Erfolgreich gekoppelt! Verbinde zu Port $connectPort…"
                    testWatchConnection()
                } else {
                    _statusMessage.value = "Kopplung erfolgreich! IP $ip gespeichert. Gehe auf der Watch einen Schritt zurück und trage den Connect-Port ein."
                }
            }.onFailure { error ->
                _statusMessage.value = "Kopplung fehlgeschlagen: ${error.localizedMessage}"
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
                status = AppInstallStatus.Error("Fenfa-Fehler: ${error.localizedMessage}")
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
                updateAppInList(app.copy(status = AppInstallStatus.Error("Kein Release zum Installieren")))
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
                        status = AppInstallStatus.Success("Erfolgreich auf Watch installiert!")
                    )
                    updateAppInList(updatedApp)
                    saveCurrentApps()
                    _statusMessage.value = "${app.name} v${app.latestVersionName} erfolgreich auf Watch installiert!"
                }.onFailure { error ->
                    updateAppInList(app.copy(status = AppInstallStatus.Error("Install-Fehler: ${error.localizedMessage}")))
                    _statusMessage.value = "Installation von ${app.name} fehlgeschlagen"
                }
            }.onFailure { error ->
                updateAppInList(app.copy(status = AppInstallStatus.Error("Download-Fehler: ${error.localizedMessage}")))
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
