package com.kanhaji.singularity.feature.files.security

data class FsSudoState(
    val isElevated: Boolean = false
) {
    fun isExpired(): Boolean = !isElevated
}
