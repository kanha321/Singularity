package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsClipboardOp
import com.kanhaji.singularity.feature.files.model.FsClipboardState
import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FsClipboardManager {
    private val _state = MutableStateFlow(FsClipboardState())
    val state: StateFlow<FsClipboardState> = _state.asStateFlow()

    fun copy(items: Collection<FsItem>) {
        if (items.isEmpty()) return
        _state.value = FsClipboardState(items = items.toSet(), op = FsClipboardOp.COPY)
    }

    fun cut(items: Collection<FsItem>) {
        if (items.isEmpty()) return
        _state.value = FsClipboardState(items = items.toSet(), op = FsClipboardOp.CUT)
    }

    fun clear() {
        _state.value = FsClipboardState()
    }
}
