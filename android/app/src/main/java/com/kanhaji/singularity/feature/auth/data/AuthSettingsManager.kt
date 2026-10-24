package com.kanhaji.singularity.feature.auth.data

import android.content.Context
import android.content.SharedPreferences
import com.kanhaji.singularity.feature.auth.model.AuthLogItem
import com.kanhaji.singularity.feature.auth.model.AuthSettings
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object AuthSettingsManager {
    private const val PREFS_NAME = "singularity_auth_customization"
    private const val KEY_SETTINGS_JSON = "auth_settings_json"

    private val _settings = MutableStateFlow(AuthSettings())
    val settings = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<AuthLogItem>>(emptyList())
    val logs = _logs.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs?.getString(KEY_SETTINGS_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                _settings.value = AuthSettings.fromJson(JSONObject(rawJson))
            } catch (_: Exception) {}
        }
    }

    fun updateSettings(newSettings: AuthSettings) {
        _settings.value = newSettings
        prefs?.edit()?.putString(KEY_SETTINGS_JSON, newSettings.toJson().toString())?.apply()
        AppBridgeService.pushAuthSettings(newSettings)
    }

    fun setSnooze(minutes: Int) {
        val until = if (minutes <= 0) 0L else System.currentTimeMillis() + (minutes * 60 * 1000L)
        updateSettings(_settings.value.copy(
            snoozeUntilEpochMs = until,
            snoozeDurationMinutes = if (minutes <= 0) 0 else minutes
        ))
    }

    fun clearSnooze() {
        setSnooze(0)
    }

    fun addLog(action: String, mode: String, result: String) {
        val item = AuthLogItem(action = action, mode = mode, result = result)
        val current = _logs.value.toMutableList()
        current.add(0, item)
        if (current.size > 30) current.removeAt(current.size - 1)
        _logs.value = current
    }
}
