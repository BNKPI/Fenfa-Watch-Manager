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

    suspend fun resetKeys(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (context != null) {
                val keyFile = File(context.filesDir, "kadb_key.pem")
                if (keyFile.exists()) {
                    keyFile.delete()
                }
            }
            KadbCert.clear()
            KadbCert.rotate()
            Log.d(TAG, "Reset KadbCert keys successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset Kadb keys", e)
            Result.failure(e)
        }
    }

    suspend fun pair(ip: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        val cleanCode = code.filter { it.isDigit() }
        if (cleanIp.isBlank() || port <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Valid IP address and pairing port (> 0) required"))
        }
        if (cleanCode.length != 6) {
            return@withContext Result.failure(IllegalArgumentException("Pairing code must be exactly 6 digits (received: '$code')"))
        }
        try {
            Log.d(TAG, "Pairing with $cleanIp:$port using code $cleanCode")
            Kadb.pair(cleanIp, port, cleanCode)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(ip: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || port <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Valid IP address and connect port (> 0) required"))
        }
        try {
            Log.d(TAG, "Testing connection to $cleanIp:$port")
            Kadb.create(cleanIp, port, 5000, 5000).use { kadb ->
                val model = kadb.shell("getprop ro.product.model").output.trim()
                val brand = kadb.shell("getprop ro.product.brand").output.trim()
                val name = if (model.isNotBlank()) "$brand $model".trim() else "Connected"
                Result.success(name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection to $cleanIp:$port failed", e)
            Result.failure(e)
        }
    }

    suspend fun getInstalledVersion(
        ip: String,
        port: Int,
        packageName: String
    ): Result<Pair<Int, String>?> = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || port <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Valid IP address and connect port required"))
        }
        try {
            Kadb.create(cleanIp, port, 5000, 5000).use { kadb ->
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
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || port <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Valid IP address and connect port required"))
        }
        try {
            if (!apkFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("APK does not exist: ${apkFile.absolutePath}"))
            }
            Log.d(TAG, "Installing ${apkFile.name} onto $cleanIp:$port")
            Kadb.create(cleanIp, port, 10000, 60000).use { kadb ->
                kadb.install(apkFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }
}

fun formatAdbError(error: Throwable, ip: String, port: Int, isPairing: Boolean): String {
    val msg = error.message.orEmpty()
    val errorClass = error::class.simpleName ?: "Error"
    return when {
        error is java.net.NoRouteToHostException || msg.contains("No route to host", ignoreCase = true) || msg.contains("unreachable", ignoreCase = true) || msg.contains("EHOSTUNREACH", ignoreCase = true) -> {
            "Host unreachable ($ip:$port).\n\n" +
            "Troubleshooting:\n" +
            "1. VPN / Tailscale on phone: If active, disable VPN or enable 'Allow local network access' in Tailscale settings!\n" +
            "2. Watch Wi-Fi asleep: Galaxy Watches turn off Wi-Fi when screen dims or while Bluetooth is connected. Keep watch display awake!\n" +
            "3. Temporary fix: Turn OFF Bluetooth on your phone so the watch is forced to stay on Wi-Fi."
        }
        error is java.net.ConnectException || msg.contains("Connection refused", ignoreCase = true) -> {
            if (isPairing) {
                "Connection refused ($ip:$port). Ensure 'Pair new device' is open on the watch right now and the screen stays on!"
            } else {
                "Connection refused ($ip:$port). Check if Wireless Debugging is enabled on the watch and verify the Connect Port (it changes when re-enabled!)."
            }
        }
        error is java.net.SocketTimeoutException || msg.contains("timed out", ignoreCase = true) || msg.contains("Timeout", ignoreCase = true) -> {
            "Connection timed out ($ip:$port). Ensure phone and watch are on the same Wi-Fi network and the watch screen is awake."
        }
        error is java.net.UnknownHostException || msg.contains("No address", ignoreCase = true) -> {
            "Invalid or unreachable IP address ($ip)."
        }
        msg.contains("auth", ignoreCase = true) || msg.contains("unauthorized", ignoreCase = true) || msg.contains("bad password", ignoreCase = true) -> {
            if (isPairing) {
                "Pairing code incorrect or expired. Please re-enter the 6-digit code from the watch."
            } else {
                "Device unauthorized. Please tap 'Pair' to authorize this phone on your watch."
            }
        }
        else -> {
            val detail = if (msg.isNotBlank()) msg else errorClass
            if (isPairing) "Pairing failed: $detail" else "Connection failed: $detail"
        }
    }
}
