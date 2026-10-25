package com.kanhaji.singularity.feature.files.download

import android.content.Context
import com.kanhaji.singularity.feature.files.download.model.DownloadProgress
import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FileDownloadManager {

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    fun enqueue(context: Context, items: List<FsItem>) {
        if (items.isEmpty()) return
        _progress.value = DownloadProgress(
            currentFileName = items.first().name,
            totalFiles = items.size,
            isRunning = true
        )
        FileDownloadService.start(context, items)
    }

    internal fun updateProgress(
        name: String,
        index: Int,
        total: Int,
        bytes: Long,
        totalBytes: Long,
        pct: Int,
        running: Boolean,
        isResuming: Boolean = false
    ) {
        _progress.value = DownloadProgress(
            currentFileName = name,
            currentIndex = index,
            totalFiles = total,
            bytesDownloaded = bytes,
            totalBytes = totalBytes,
            percentage = pct,
            isRunning = running,
            isResuming = isResuming,
            isReconnecting = false
        )
    }

    internal fun updateReconnecting(
        name: String,
        index: Int,
        total: Int,
        pct: Int
    ) {
        val current = _progress.value
        _progress.value = current.copy(
            currentFileName = name,
            currentIndex = index,
            totalFiles = total,
            percentage = pct,
            isRunning = true,
            isReconnecting = true
        )
    }

    internal fun complete(success: Int, total: Int) {
        _progress.value = DownloadProgress(
            currentFileName = "",
            currentIndex = success,
            totalFiles = total,
            isRunning = false,
            isCompleted = true
        )
    }
}
