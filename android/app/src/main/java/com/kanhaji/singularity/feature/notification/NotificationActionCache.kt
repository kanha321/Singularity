package com.kanhaji.singularity.feature.notification

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object NotificationActionCache {
    private const val TAG = "NotificationActionCache"

    data class CachedAction(
        val pendingIntent: PendingIntent?,
        val remoteInputs: Array<RemoteInput>?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val actions = ConcurrentHashMap<String, CachedAction>()

    fun register(notifKey: String, actionId: String, pendingIntent: PendingIntent?, remoteInputs: Array<RemoteInput>?) {
        actions["$notifKey:$actionId"] = CachedAction(pendingIntent, remoteInputs)
    }

    fun removeForNotification(notifKey: String) {
        val cutoff = System.currentTimeMillis() - 120_000L
        actions.entries.removeIf { it.value.timestamp < cutoff }
    }

    fun invoke(context: Context, notifKey: String, actionId: String, replyText: String?): Boolean {
        val allKeys = actions.keys().toList()
        val matchingKeys = allKeys.filter { key ->
            key.startsWith("$notifKey:") ||
            (notifKey.isNotEmpty() && key.contains("|$notifKey|")) ||
            (notifKey.isNotEmpty() && key.contains(":$notifKey:")) ||
            (notifKey.length >= 4 && key.contains(notifKey))
        }

        val targetKey = if (actionId == "reply") {
            matchingKeys.firstOrNull { actions[it]?.remoteInputs != null }
                ?: matchingKeys.firstOrNull()
                ?: "$notifKey:action_0"
        } else {
            matchingKeys.firstOrNull { it.endsWith(":$actionId") }
                ?: "$notifKey:$actionId"
        }

        val entry = actions[targetKey] ?: run {
            Log.w(TAG, "No cached action found for notifKey=$notifKey actionId=$actionId (tried $targetKey, cached=${actions.size})")
            return false
        }

        return try {
            val options = if (android.os.Build.VERSION.SDK_INT >= 34) {
                android.app.ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            } else null

            if (replyText != null && entry.remoteInputs != null) {
                val intent = Intent()
                val bundle = Bundle()
                for (ri in entry.remoteInputs) {
                    bundle.putCharSequence(ri.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(entry.remoteInputs, intent, bundle)
                entry.pendingIntent?.send(context, 0, intent, null, null, null, options)
                Log.d(TAG, "Dispatched inline reply to notifKey=$notifKey text='$replyText'")
            } else {
                entry.pendingIntent?.send(context, 0, null, null, null, null, options)
                Log.d(TAG, "Dispatched one-tap action click to notifKey=$notifKey actionId=$actionId")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed invoking action on notifKey=$notifKey: ${e.message}")
            false
        }
    }
}
