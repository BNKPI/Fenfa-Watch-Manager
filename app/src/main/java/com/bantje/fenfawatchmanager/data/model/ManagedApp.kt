package com.bantje.fenfawatchmanager.data.model

sealed interface AppInstallStatus {
    object Idle : AppInstallStatus
    object Checking : AppInstallStatus
    object UpToDate : AppInstallStatus
    data class UpdateAvailable(val latestVersion: String, val latestCode: Int) : AppInstallStatus
    data class Downloading(val progress: Float?) : AppInstallStatus
    object Installing : AppInstallStatus
    data class Success(val message: String) : AppInstallStatus
    data class Error(val error: String) : AppInstallStatus
}

data class ManagedApp(
    val id: String,
    val name: String,
    val slug: String,
    val packageName: String,
    val autoUpdate: Boolean = true,
    val installedVersionName: String? = null,
    val installedVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val latestVersionCode: Int? = null,
    val latestReleaseId: String? = null,
    val downloadBaseUrl: String? = null,
    val changelog: String? = null,
    val status: AppInstallStatus = AppInstallStatus.Idle
) {
    val hasUpdate: Boolean
        get() = latestVersionCode != null && (installedVersionCode == null || latestVersionCode > installedVersionCode)
}
