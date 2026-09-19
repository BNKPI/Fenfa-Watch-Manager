package com.bantje.fenfawatchmanager.watch

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

class WatchAdbService(private val context: Context? = null) {

    companion object {
        private const val TAG = "WatchAdbService"
        private var isConfigured = false

        fun initCertStore(context: Context) {
            if (isConfigured) return
            try {
                val keyFile = File(context.filesDir, "kadb_key.pem")
                KadbCert.configure(store = OkioFilePrivateKeyStore(keyFile.toOkioPath(), FileSystem.SYSTEM))
                isConfigured = true
                Log.d(TAG, "Initialized persistent KadbCert store at ${keyFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize persistent KadbCert store, fallback to in-memory", e)
            }
        }
    }

    init {
        context?.let { initCertStore(it) }
    }

    suspend fun pair(ip: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pairing with $ip:$port using code $code")
            Kadb.pair(ip, port, code)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(ip: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to $ip:$port")
            Kadb.create(ip, port).use { kadb ->
                val model = kadb.shell("getprop ro.product.model").output.trim()
                val brand = kadb.shell("getprop ro.product.brand").output.trim()
                val name = if (model.isNotBlank()) "$brand $model".trim() else "Verbunden"
                Result.success(name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection to $ip:$port failed", e)
            Result.failure(e)
        }
    }

    suspend fun getInstalledVersion(
        ip: String,
        port: Int,
        packageName: String
    ): Result<Pair<Int, String>?> = withContext(Dispatchers.IO) {
        try {
            Kadb.create(ip, port).use { kadb ->
                val output = kadb.shell("dumpsys package $packageName").output
                if (output.contains("Unable to find package") || !output.contains("versionCode=")) {
                    return@withContext Result.success(null)
                }

                val codeRegex = Regex("""versionCode=(\d+)""")
                val nameRegex = Regex("""versionName=([^\s]+)""")

                val codeMatch = codeRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
                val nameMatch = nameRegex.find(output)?.groupValues?.get(1)

                if (codeMatch != null) {
                    Result.success(Pair(codeMatch, nameMatch ?: codeMatch.toString()))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installApk(ip: String, port: Int, apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!apkFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("APK existiert nicht"))
            }
            Log.d(TAG, "Installing ${apkFile.name} onto $ip:$port")
            Kadb.create(ip, port).use { kadb ->
                kadb.install(apkFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }
}
