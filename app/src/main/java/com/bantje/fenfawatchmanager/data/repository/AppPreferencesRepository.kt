package com.bantje.fenfawatchmanager.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bantje.fenfawatchmanager.BuildConfig
import com.bantje.fenfawatchmanager.data.model.ManagedApp
import com.bantje.fenfawatchmanager.data.model.WatchConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_manager_prefs")

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val WATCH_IP = stringPreferencesKey("watch_ip")
        val WATCH_PORT = intPreferencesKey("watch_port")
        val FENFA_LOCAL_URL = stringPreferencesKey("fenfa_local_url")
        val FENFA_REMOTE_URL = stringPreferencesKey("fenfa_remote_url")
        val MANAGED_APPS_JSON = stringPreferencesKey("managed_apps_json")
    }

    val watchConfigFlow: Flow<WatchConfig> = context.dataStore.data.map { prefs ->
        val savedIp = prefs[Keys.WATCH_IP] ?: ""
        val savedPort = prefs[Keys.WATCH_PORT] ?: 0
        WatchConfig(
            ip = savedIp,
            connectPort = savedPort
        )
    }

    val fenfaUrlsFlow: Flow<Pair<String, String>> = context.dataStore.data.map { prefs ->
        val local = prefs[Keys.FENFA_LOCAL_URL] ?: BuildConfig.FENFA_LOCAL_URL
        val remote = prefs[Keys.FENFA_REMOTE_URL] ?: BuildConfig.FENFA_REMOTE_URL
        Pair(local, remote)
    }

    val managedAppsFlow: Flow<List<ManagedApp>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.MANAGED_APPS_JSON]
        if (json.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                deserializeApps(json)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveWatchConfig(ip: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WATCH_IP] = ip
            prefs[Keys.WATCH_PORT] = port
        }
    }

    suspend fun saveFenfaUrls(localUrl: String, remoteUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FENFA_LOCAL_URL] = localUrl.trimEnd('/')
            prefs[Keys.FENFA_REMOTE_URL] = remoteUrl.trimEnd('/')
        }
    }

    suspend fun saveManagedApps(apps: List<ManagedApp>) {
        val json = serializeApps(apps)
        context.dataStore.edit { prefs ->
            prefs[Keys.MANAGED_APPS_JSON] = json
        }
    }

    private fun defaultApps(): List<ManagedApp> = emptyList()

    private fun serializeApps(apps: List<ManagedApp>): String {
        val array = JSONArray()
        for (app in apps) {
            val obj = JSONObject().apply {
                put("id", app.id)
                put("name", app.name)
                put("slug", app.slug)
                put("packageName", app.packageName)
                put("autoUpdate", app.autoUpdate)
                put("installedVersionName", app.installedVersionName ?: "")
                put("installedVersionCode", app.installedVersionCode ?: -1)
                put("latestVersionName", app.latestVersionName ?: "")
                put("latestVersionCode", app.latestVersionCode ?: -1)
                put("latestReleaseId", app.latestReleaseId ?: "")
                put("downloadBaseUrl", app.downloadBaseUrl ?: "")
                put("changelog", app.changelog ?: "")
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeApps(json: String): List<ManagedApp> {
        val list = mutableListOf<ManagedApp>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val slug = obj.optString("slug").takeIf { it.isNotBlank() } ?: continue
            val packageName = obj.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: slug
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: slug
            val autoUpdate = obj.optBoolean("autoUpdate", true)
            val installedVName = obj.optString("installedVersionName").takeIf { it.isNotBlank() }
            val installedVCode = obj.optInt("installedVersionCode", -1).takeIf { it >= 0 }
            val latestVName = obj.optString("latestVersionName").takeIf { it.isNotBlank() }
            val latestVCode = obj.optInt("latestVersionCode", -1).takeIf { it >= 0 }
            val latestReleaseId = obj.optString("latestReleaseId").takeIf { it.isNotBlank() }
            val downloadBaseUrl = obj.optString("downloadBaseUrl").takeIf { it.isNotBlank() }
            val changelog = obj.optString("changelog").takeIf { it.isNotBlank() }

            list.add(
                ManagedApp(
                    id = id,
                    name = name,
                    slug = slug,
                    packageName = packageName,
                    autoUpdate = autoUpdate,
                    installedVersionName = installedVName,
                    installedVersionCode = installedVCode,
                    latestVersionName = latestVName,
                    latestVersionCode = latestVCode,
                    latestReleaseId = latestReleaseId,
                    downloadBaseUrl = downloadBaseUrl,
                    changelog = changelog
                )
            )
        }
        return list
    }
}
