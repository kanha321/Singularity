package com.kanhaji.singularity.feature.auth.model

import org.json.JSONObject

data class AuthSettings(
    val loginMode: AuthMode = AuthMode.NOTIFY,
    val unlockMode: AuthMode = AuthMode.PROMPT,
    val sudoMode: AuthMode = AuthMode.PROMPT,
    val snoozeUntilEpochMs: Long = 0L,
    val snoozeDurationMinutes: Int = 0,
    val notifyTimeoutSecs: Int = 30,
    val promptTimeoutSecs: Int = 15,
    val maxFailedAttempts: Int = 2
) {
    val isSnoozed: Boolean
        get() = snoozeUntilEpochMs > System.currentTimeMillis()

    val snoozeRemainingMinutes: Long
        get() = if (isSnoozed) ((snoozeUntilEpochMs - System.currentTimeMillis()) / 60000L).coerceAtLeast(1L) else 0L

    fun getModeForAction(action: String): AuthMode {
        val act = action.lowercase()
        return when {
            act.contains("login") || act.contains("greetd") -> loginMode
            act.contains("unlock") || act.contains("lock") || act.contains("screen") || act.contains("dms") -> unlockMode
            else -> sudoMode
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("login_mode", loginMode.name.lowercase())
        put("unlock_mode", unlockMode.name.lowercase())
        put("sudo_mode", sudoMode.name.lowercase())
        put("snooze_until", snoozeUntilEpochMs)
        put("snooze_duration_mins", snoozeDurationMinutes)
        put("notify_timeout_secs", notifyTimeoutSecs)
        put("prompt_timeout_secs", promptTimeoutSecs)
        put("max_failed_attempts", maxFailedAttempts)
    }

    companion object {
        fun fromJson(json: JSONObject): AuthSettings = AuthSettings(
            loginMode = AuthMode.fromString(json.optString("login_mode", "notify")),
            unlockMode = AuthMode.fromString(json.optString("unlock_mode", "prompt")),
            sudoMode = AuthMode.fromString(json.optString("sudo_mode", "prompt")),
            snoozeUntilEpochMs = json.optLong("snooze_until", 0L),
            snoozeDurationMinutes = json.optInt("snooze_duration_mins", 0),
            notifyTimeoutSecs = json.optInt("notify_timeout_secs", 30),
            promptTimeoutSecs = json.optInt("prompt_timeout_secs", 15),
            maxFailedAttempts = json.optInt("max_failed_attempts", 2)
        )
    }
}
