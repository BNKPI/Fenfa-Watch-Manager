package com.bantje.fenfawatchmanager.fenfa

import android.util.Log
import com.bantje.fenfawatchmanager.data.model.FenfaRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class FenfaClient {

    companion object {
        private const val TAG = "FenfaClient"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloadClient = httpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchLatestRelease(
        productSlug: String,
        baseUrls: List<String>
    ): Result<FenfaRelease> = withContext(Dispatchers.IO) {
        if (baseUrls.isEmpty() || productSlug.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URLs or slug empty"))
        }

        var lastError: Exception? = null

        for ((index, baseUrl) in baseUrls.withIndex()) {
            val timeoutMs = if (index == 0) 3_000L else 10_000L
            try {
                val client = httpClient.newBuilder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs + 2_000L, TimeUnit.MILLISECONDS)
                    .build()

                val url = "${baseUrl.trimEnd('/')}/api/products/slug/$productSlug"
                val response = client.newCall(
                    Request.Builder().url(url).get().build()
                ).execute()

                response.use { httpResponse ->
                    if (!httpResponse.isSuccessful) {
                        lastError = IOException("HTTP ${httpResponse.code} from $baseUrl")
                        return@use
                    }
                    val body = httpResponse.body?.string() ?: return@use
                    val release = parseReleaseResponse(body, baseUrl)
                    if (release != null) {
                        return@withContext Result.success(release)
                    } else {
                        lastError = IllegalStateException("No Android release found in Fenfa ($baseUrl)")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed contacting $baseUrl: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: IOException("Fenfa is not reachable via any configured URL"))
    }

    suspend fun downloadApk(
        downloadUrl: String,
        destFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (destFile.exists()) {
                destFile.delete()
            }
            destFile.parentFile?.mkdirs()

            val request = Request.Builder().url(downloadUrl).get().build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Download failed with HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(IOException("Empty server response"))
                val totalLength = body.contentLength()

                body.byteStream().use { input ->
                    destFile.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastEmitAt = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= 80L) {
                                lastEmitAt = now
                                onProgress(copied, totalLength)
                            }
                        }
                        onProgress(copied, totalLength)
                    }
                }
            }

            if (!isValidApk(destFile)) {
                destFile.delete()
                return@withContext Result.failure(IOException("Downloaded file is not a valid Android APK"))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            destFile.delete()
            Result.failure(e)
        }
    }

    private fun parseReleaseResponse(body: String, baseUrl: String): FenfaRelease? {
        val root = JSONObject(body)
        val data = when {
            root.optBoolean("ok", false) -> root.optJSONObject("data") ?: return null
            root.has("variants") -> root
            else -> return null
        }

        val variants = data.optJSONArray("variants") ?: return null
        val androidVariant = findAndroidVariant(variants) ?: return null
        val release = findLatestRelease(androidVariant) ?: return null

        val releaseId = release.optString("id").ifBlank { release.optString("release_id") }
        if (releaseId.isBlank()) return null

        val versionCode = release.optInt("build", release.optInt("version_code", -1))
        val versionName = release.optString("version", release.optString("version_name", ""))
        val changelog = release.optString("changelog").takeIf { it.isNotBlank() }

        return FenfaRelease(
            releaseId = releaseId,
            versionName = versionName.ifBlank { versionCode.toString() },
            versionCode = versionCode,
            changelog = changelog,
            downloadBaseUrl = baseUrl.trimEnd('/')
        )
    }

    private fun findAndroidVariant(variants: JSONArray): JSONObject? {
        for (i in 0 until variants.length()) {
            val variant = variants.optJSONObject(i) ?: continue
            val platform = variant.optString("platform").lowercase()
            if (platform == "android") return variant
        }
        return null
    }

    private fun findLatestRelease(variant: JSONObject): JSONObject? {
        variant.optJSONObject("latest_release")?.let { return it }

        val releases = variant.optJSONArray("releases") ?: return null
        var best: JSONObject? = null
        var bestBuild = -1
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            val build = release.optInt("build", release.optInt("version_code", -1))
            if (build > bestBuild) {
                bestBuild = build
                best = release
            }
        }
        return best
    }

    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        FileInputStream(file).use { input ->
            val magic = ByteArray(2)
            if (input.read(magic) != 2) return false
            return magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        }
    }
}
