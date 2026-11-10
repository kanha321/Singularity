package com.kanhaji.singularity.feature.notification

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.kanhaji.singularity.feature.media.session.SingularityMediaListenerService
import com.kanhaji.singularity.feature.notification.model.NotificationButton
import com.kanhaji.singularity.feature.notification.model.NotificationDismiss
import com.kanhaji.singularity.feature.notification.model.PhoneNotification
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object PhoneNotificationTracker {
    private const val TAG = "PhoneNotificationTracker"

    private val pendingPosted = ConcurrentLinkedQueue<PhoneNotification>()
    private val pendingDismissals = ConcurrentLinkedQueue<NotificationDismiss>()
    private val sentIconPackages = ConcurrentHashMap.newKeySet<String>()

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val isTest = extras.getBoolean("singularity_test_notif", false)
        if (sbn.packageName == context.packageName && !isTest) return
        if (sbn.isOngoing && !isTest) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim() ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().trim()
            if (label.isNotEmpty() && !label.contains(".")) label else cleanPackageFallback(sbn.packageName)
        } catch (_: Exception) {
            cleanPackageFallback(sbn.packageName)
        }

        val actionButtons = mutableListOf<NotificationButton>()
        var canReply = false
        sbn.notification.contentIntent?.let { contentIntent ->
            NotificationActionCache.register(sbn.key, "default", contentIntent, null)
        }
        sbn.notification.actions?.forEachIndexed { idx, act ->
            val label = act.title?.toString()?.trim() ?: ""
            if (label.isNotEmpty()) {
                val actId = "action_$idx"
                actionButtons.add(NotificationButton(actionId = actId, label = label))
                NotificationActionCache.register(sbn.key, actId, act.actionIntent, act.remoteInputs)
                if (act.remoteInputs != null && act.remoteInputs.isNotEmpty()) {
                    canReply = true
                }
            }
        }
        if (!canReply) {
            canReply = actionButtons.any { it.label.contains("Reply", ignoreCase = true) }
        }

        val iconBase64 = if (!sentIconPackages.contains(sbn.packageName)) {
            val b64 = extractAppIconBase64(pm, sbn.packageName)
            if (b64 != null) sentIconPackages.add(sbn.packageName)
            b64
        } else {
            null
        }

        val notif = PhoneNotification(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            isClearable = sbn.isClearable,
            canReply = canReply,
            actions = actionButtons,
            iconBase64 = iconBase64,
            groupKey = sbn.groupKey
        )

        pendingPosted.add(notif)
        Log.d(TAG, "Queued phone notification from $appName: $title (canReply=$canReply, actions=${actionButtons.size})")
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        NotificationActionCache.removeForNotification(sbn.key)
        pendingDismissals.add(NotificationDismiss(id = sbn.key, source = "phone"))
    }

    fun enqueueDismissal(dismiss: NotificationDismiss) {
        pendingDismissals.add(dismiss)
        Log.d(TAG, "Enqueued dismissal for id: ${dismiss.id}")
    }

    fun drainPending(): Pair<List<PhoneNotification>, List<NotificationDismiss>> {
        val postedList = mutableListOf<PhoneNotification>()
        while (true) {
            val item = pendingPosted.poll() ?: break
            postedList.add(item)
        }

        val dismissList = mutableListOf<NotificationDismiss>()
        while (true) {
            val item = pendingDismissals.poll() ?: break
            dismissList.add(item)
        }

        if (dismissList.isNotEmpty()) {
            Log.d(TAG, "Draining ${dismissList.size} dismissals")
        }
        return Pair(postedList, dismissList)
    }

    fun cancelPhoneNotification(key: String) {
        try {
            SingularityMediaListenerService.getInstance()?.cancelNotification(key)
            NotificationActionCache.removeForNotification(key)
            Log.d(TAG, "Cancelled phone notification with key: $key")
        } catch (e: Exception) {
            Log.w(TAG, "Failed cancelling phone notification: ${e.message}")
        }
    }

    private fun extractAppIconBase64(pm: PackageManager, packageName: String): String? {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(appInfo)
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val w = drawable.intrinsicWidth.coerceIn(48, 96)
                    val h = drawable.intrinsicHeight.coerceIn(48, 96)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun cleanPackageFallback(pkg: String): String {
        val parts = pkg.split(".")
        val filtered = parts.reversed().firstOrNull { part ->
            val p = part.lowercase()
            p != "android" && p != "app" && p != "mobile" && p != "apps" && p != "mshop" && p != "shopping"
        } ?: parts.lastOrNull() ?: pkg
        return filtered.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
