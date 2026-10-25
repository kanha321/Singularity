package com.kanhaji.singularity.feature.files.download.model

data class DownloadProgress(
    val currentFileName: String = "",
    val currentIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percentage: Int = 0,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val isResuming: Boolean = false,
    val isReconnecting: Boolean = false,
    val error: String? = null
)

