package com.kanhaji.singularity.security

import android.content.Context

object CounterManager {
    private const val PREFS_NAME = "singularity_crypto_prefs"
    private const val KEY_COUNTER = "auth_monotonic_counter"

    @Synchronized
    fun nextCounter(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getLong(KEY_COUNTER, 0L) + 1L
        prefs.edit().putLong(KEY_COUNTER, next).apply()
        return next
    }

    fun getCurrentCounter(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_COUNTER, 0L)
    }
}

