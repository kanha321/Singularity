package com.kanhaji.singularity.service

data class PendingAuth(
    val reason: String,
    val timeoutSecs: Long,
    val startTime: Long = System.currentTimeMillis()
)

data class AuthResultData(
    val success: Boolean,
    val error: String?,
    val failedAttempts: Int = 0,
    val isLockout: Boolean = false
)

