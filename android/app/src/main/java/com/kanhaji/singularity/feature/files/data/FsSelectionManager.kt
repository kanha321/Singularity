package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FsSelectionManager {
    private val _state = MutableStateFlow(FsSelectionState())
    val state = _state.asStateFlow()

    private val _currentItems = MutableStateFlow<List<FsItem>>(emptyList())
    val currentItems = _currentItems.asStateFlow()

    fun toggle(item: FsItem) {
        _state.value = _state.value.toggle(item)
    }

    fun startSelection(item: FsItem) {
        _state.value = FsSelectionState(setOf(item))
    }

    fun selectAll() {
        _state.value = _state.value.selectAll(_currentItems.value)
    }

    fun clear() {
        _state.value = _state.value.clear()
    }

    fun setCurrentItems(items: List<FsItem>) {
        _currentItems.value = items
    }
}
