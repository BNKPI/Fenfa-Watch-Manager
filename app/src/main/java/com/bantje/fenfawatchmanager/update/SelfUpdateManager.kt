package com.bantje.fenfawatchmanager.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bantje.fenfawatchmanager.BuildConfig
import com.bantje.fenfawatchmanager.R
import com.bantje.fenfawatchmanager.data.model.FenfaRelease
import com.bantje.fenfawatchmanager.fenfa.FenfaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SelfUpdateManager(
    private val activity: Activity,
    private val fenfaClient: FenfaClient = FenfaClient()
) {
    companion object {
        private const val TAG = "SelfUpdateManager"
    }

    private var pendingInstallFile: File? = null

    suspend fun checkForSelfUpdate(baseUrls: List<String>): FenfaRelease? = withContext(Dispatchers.IO) {
        if (!BuildConfig.ENABLE_SELF_UPDATE) return@withContext null
        try {
            val result = fenfaClient.fetchLatestRelease(BuildConfig.FENFA_PRODUCT_SLUG, baseUrls)
            val release = result.getOrNull() ?: return@withContext null
            val installedCode = BuildConfig.VERSION_CODE
            if (release.versionCode > installedCode) {
                Log.d(TAG, "Update available: ${release.versionName} (${release.versionCode}) > $installedCode")
                release
            } else {
                Log.d(TAG, "App up to date (current $installedCode, latest ${release.versionCode})")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Self-update check failed", e)
            null
        }
    }

    suspend fun startSelfUpdate(
        release: FenfaRelease,
        onProgress: (Float?) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val destFile = File(
            activity.getExternalFilesDir(null) ?: activity.filesDir,
            "fenfawatchmanager-update-${release.versionCode}.apk"
        )
        val downloadUrl = "${release.downloadBaseUrl.trimEnd('/')}/d/${release.releaseId}"

        val downloadResult = fenfaClient.downloadApk(downloadUrl, destFile) { copied, total ->
            val progress = if (total > 0) copied.toFloat() / total.toFloat() else null
            onProgress(progress)
        }

        downloadResult.mapCatching { apkFile ->
            withContext(Dispatchers.Main) {
                installApk(apkFile)
            }
        }
    }

    fun resumePendingWork() {
        if (!BuildConfig.ENABLE_SELF_UPDATE) return
        pendingInstallFile?.let { file ->
            if (file.exists()) {
                installApk(file)
            } else {
                pendingInstallFile = null
            }
        }
    }

    private fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_install_failed),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = apkFile
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            return
        }

        pendingInstallFile = null

        // Try PackageInstaller session first
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(activity.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(InstallResultReceiver.pendingIntent(activity).intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller session failed, falling back to ACTION_VIEW", e)
            startActionViewInstaller(apkFile)
        }
    }

    private fun startActionViewInstaller(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_VIEW install failed", e)
            Toast.makeText(
                activity,
                activity.getString(R.string.update_install_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
