package com.kanhaji.singularity.feature.notification.model

import org.json.JSONArray
import org.json.JSONObject

data class NotificationButton(
    val actionId: String,
    val label: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action_id", actionId)
        put("label", label)
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationButton {
            return NotificationButton(
                actionId = json.optString("action_id"),
                label = json.optString("label")
            )
        }
    }
}

data class PhoneNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isClearable: Boolean = true,
    val canReply: Boolean = false,
    val actions: List<NotificationButton> = emptyList(),
    val iconBase64: String? = null,
    val groupKey: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("package_name", packageName)
        put("app_name", appName)
        put("title", title)
        put("text", text)
        put("timestamp", timestamp)
        put("is_clearable", isClearable)
        put("can_reply", canReply)
        val arr = JSONArray()
        actions.forEach { arr.put(it.toJson()) }
        put("actions", arr)
        if (iconBase64 != null) put("icon_base64", iconBase64)
        if (groupKey != null) put("group_key", groupKey)
    }

    companion object {
        fun fromJson(json: JSONObject): PhoneNotification {
            val actList = mutableListOf<NotificationButton>()
            val arr = json.optJSONArray("actions")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { actList.add(NotificationButton.fromJson(it)) }
                }
            }
            return PhoneNotification(
                id = json.optString("id"),
                packageName = json.optString("package_name"),
                appName = json.optString("app_name"),
                title = json.optString("title"),
                text = json.optString("text"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isClearable = json.optBoolean("is_clearable", true),
                canReply = json.optBoolean("can_reply", false),
                actions = actList,
                iconBase64 = if (json.has("icon_base64")) json.optString("icon_base64") else null,
                groupKey = if (json.has("group_key")) json.optString("group_key") else null
            )
        }
    }
}

data class DesktopNotification(
    val id: Int,
    val appName: String,
    val summary: String,
    val body: String,
    val urgency: Int = 1,
    val timeoutMs: Int = 5000
) {
    companion object {
        fun fromJson(json: JSONObject): DesktopNotification {
            return DesktopNotification(
                id = json.optInt("id", 0),
                appName = json.optString("app_name", "Linux Desktop"),
                summary = json.optString("summary", ""),
                body = json.optString("body", ""),
                urgency = json.optInt("urgency", 1),
                timeoutMs = json.optInt("timeout_ms", 5000)
            )
        }
    }
}

data class NotificationDismiss(
    val id: String,
    val source: String = "desktop"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", source)
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationDismiss {
            return NotificationDismiss(
                id = json.optString("id"),
                source = json.optString("source", "desktop")
            )
        }
    }
}

data class NotificationActionInvoke(
    val id: String,
    val actionId: String,
    val replyText: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("action_id", actionId)
        if (replyText != null) put("reply_text", replyText)
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationActionInvoke {
            return NotificationActionInvoke(
                id = json.optString("id"),
                actionId = json.optString("action_id"),
                replyText = if (json.has("reply_text")) json.optString("reply_text") else null
            )
        }
    }
}

