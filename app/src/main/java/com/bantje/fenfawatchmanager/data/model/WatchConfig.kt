package com.bantje.fenfawatchmanager.data.model

data class WatchConfig(
    val ip: String = "",
    val connectPort: Int = 0,
    val deviceModel: String? = null,
    val isConnected: Boolean = false,
    val lastChecked: Long = 0L
)
