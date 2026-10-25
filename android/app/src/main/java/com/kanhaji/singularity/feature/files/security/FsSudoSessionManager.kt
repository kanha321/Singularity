package com.kanhaji.singularity.feature.files.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FsSudoSessionManager {

    private val _state = MutableStateFlow(FsSudoState())
    val state = _state.asStateFlow()

    fun elevate() {
        _state.value = FsSudoState(isElevated = true)
    }

    fun dropPrivileges() {
        _state.value = FsSudoState(isElevated = false)
    }

    fun isPathProtected(path: String): Boolean {
        if (path.isEmpty() || path == "~" || path.startsWith("~/")) return false
        if (path.startsWith("/home/kanhaji")) return false
        return path.startsWith("/")
    }

    fun canAccessPath(path: String): Boolean {
        if (!isPathProtected(path)) return true
        return _state.value.isElevated
    }
}
