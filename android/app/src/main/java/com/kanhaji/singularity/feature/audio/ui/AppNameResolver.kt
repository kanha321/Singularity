package com.kanhaji.singularity.feature.audio.ui

import android.content.Context
import android.content.pm.PackageManager

object AppNameResolver {
    fun resolve(context: Context, rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        if (!rawName.contains('.')) return rawName
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(rawName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            rawName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        } catch (_: Throwable) {
            rawName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
