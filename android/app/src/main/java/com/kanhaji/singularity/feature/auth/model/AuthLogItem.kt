package com.kanhaji.singularity.feature.auth.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AuthLogItem(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val mode: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val isSuccess: Boolean
        get() = result.equals("Approved", ignoreCase = true)
}
