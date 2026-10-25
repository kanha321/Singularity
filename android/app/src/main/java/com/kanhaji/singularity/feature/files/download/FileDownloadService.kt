package com.kanhaji.singularity.feature.files.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.kanhaji.singularity.feature.files.data.FileBrowserRepository
import com.kanhaji.singularity.feature.files.data.FileDownloader
import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FileDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationHelper: DownloadNotificationHelper

    companion object {
        const val EXTRA_ITEMS_JSON = "extra_items_json"
        private const val MAX_RETRIES = 3
        private const val CONCURRENCY_LIMIT = 3

        fun start(context: Context, items: List<FsItem>) {
            val intent = Intent(context, FileDownloadService::class.java).apply {
                putExtra(EXTRA_ITEMS_JSON, Json.encodeToString(items))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationHelper = DownloadNotificationHelper(this, nm)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val json = intent?.getStringExtra(EXTRA_ITEMS_JSON)
        val items = try {
            if (!json.isNullOrEmpty()) Json.decodeFromString<List<FsItem>>(json) else emptyList()
        } catch (_: Exception) { emptyList() }

        if (items.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val scanNotif = notificationHelper.buildScanningNotification("Scanning and preparing download...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, DownloadNotificationHelper.NOTIFICATION_ID, scanNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(DownloadNotificationHelper.NOTIFICATION_ID, scanNotif)
        }

        serviceScope.launch {
            val resolved = FolderDownloadResolver.resolve(items) { FileBrowserRepository.listDirectory(it) }
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Singularity")
            resolved.directories.forEach { File(baseDir, it).mkdirs() }

            if (resolved.targets.isEmpty()) {
                FileDownloadManager.complete(0, 0)
                notificationHelper.showCompletion(resolved.rootLabel, 0, 0, 0L, 0)
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@launch
            }

            processDownloadTargets(resolved)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun processDownloadTargets(resolved: ResolvedDownload) {
        val targets = resolved.targets
        val (synced, toDownload) = targets.partition { FileDownloader.isTargetAlreadySynced(it) }
        val completed = AtomicInteger(synced.size)
        val success = AtomicInteger(synced.size)
        val progressMap = ConcurrentHashMap<String, Long>().apply {
            synced.forEach { this[it.relativeLocalPath] = it.size }
        }
        val totalBytes = resolved.totalBytes
        val semaphore = Semaphore(if (toDownload.size > 1) CONCURRENCY_LIMIT else 1)

        coroutineScope {
            toDownload.map { target ->
                async {
                    semaphore.withPermit {
                        val ok = downloadWithRetry(target) { read, resuming ->
                            progressMap[target.relativeLocalPath] = read
                            val currentTotal = progressMap.values.sum()
                            val pct = FileDownloadUtils.calculatePercentage(currentTotal, totalBytes)
                            FileDownloadManager.updateProgress(target.relativeLocalPath, completed.get(), targets.size, currentTotal, totalBytes, pct, true, resuming)
                            notificationHelper.updateProgress(resolved.rootLabel, completed.get(), targets.size, currentTotal, totalBytes, pct, resuming)
                        }
                        completed.incrementAndGet()
                        if (ok) success.incrementAndGet()
                    }
                }
            }.awaitAll()
        }

        FileDownloadManager.complete(success.get(), targets.size)
        notificationHelper.showCompletion(resolved.rootLabel, success.get(), targets.size, totalBytes, synced.size)
    }

    private suspend fun downloadWithRetry(target: DownloadTarget, onProgress: (Long, Boolean) -> Unit): Boolean {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            attempt++
            val result = FileDownloader.downloadTarget(target) { read, _, resuming -> onProgress(read, resuming) }
            if (result.isSuccess) return true
            if (attempt < MAX_RETRIES) delay(2000L)
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
