package com.kanhaji.singularity.service

data class PendingAuth(
    val reason: String,
    val timeoutSecs: Long,
    val nonce: String? = null,
    val timestampSecs: Long = 0,
    val authAction: String = "sudo",
    val startTime: Long = System.currentTimeMillis()
)

data class AuthResultData(
    val success: Boolean,
    val error: String?,
    val failedAttempts: Int = 0,
    val isLockout: Boolean = false,
    val signature: String? = null,
    val counter: Long = 0
)
