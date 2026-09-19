package com.bantje.fenfawatchmanager.data.model

data class FenfaRelease(
    val releaseId: String,
    val versionName: String,
    val versionCode: Int,
    val changelog: String?,
    val downloadBaseUrl: String
)
