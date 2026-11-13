package com.kanhaji.singularity.util

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceIdentityHelper {

    fun getFriendlyDeviceName(context: Context): String {
        return try {
            val globalName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else {
                null
            }
            if (!globalName.isNullOrBlank()) return globalName.trim()

            val btName = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            if (!btName.isNullOrBlank()) return btName.trim()

            formatManufacturerModel()
        } catch (_: Exception) {
            formatManufacturerModel()
        }
    }

    private fun formatManufacturerModel(): String {
        val manufacturer = (Build.MANUFACTURER ?: "").trim().replaceFirstChar { it.uppercase() }
        val model = (Build.MODEL ?: "").trim()
        return when {
            model.isNotBlank() && manufacturer.isNotBlank() && model.startsWith(manufacturer, ignoreCase = true) -> model
            model.isNotBlank() && manufacturer.isNotBlank() -> "$manufacturer $model"
            model.isNotBlank() -> model
            manufacturer.isNotBlank() -> manufacturer
            else -> "Android Device"
        }
    }
}
