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
            defaultApps()
        } else {
            try {
                deserializeApps(json)
            } catch (_: Exception) {
                defaultApps()
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

    private fun defaultApps(): List<ManagedApp> {
        return listOf(
            ManagedApp(
                id = "opengymwear",
                name = "OpenGym Wear",
                slug = "opengymwear",
                packageName = "com.example.opengymwear",
                autoUpdate = true
            )
        )
    }

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
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeApps(json: String): List<ManagedApp> {
        val list = mutableListOf<ManagedApp>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val vCode = obj.optInt("installedVersionCode", -1)
            list.add(
                ManagedApp(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    slug = obj.getString("slug"),
                    packageName = obj.getString("packageName"),
                    autoUpdate = obj.optBoolean("autoUpdate", true),
                    installedVersionName = obj.optString("installedVersionName").takeIf { it.isNotBlank() },
                    installedVersionCode = if (vCode >= 0) vCode else null
                )
            )
        }
        return list
    }
}
