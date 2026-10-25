package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FsDialogManager {
    private val _contextMenuItem = MutableStateFlow<FsItem?>(null)
    val contextMenuItem: StateFlow<FsItem?> = _contextMenuItem.asStateFlow()

    private val _renameItem = MutableStateFlow<FsItem?>(null)
    val renameItem: StateFlow<FsItem?> = _renameItem.asStateFlow()

    private val _deleteItems = MutableStateFlow<List<FsItem>?>(null)
    val deleteItems: StateFlow<List<FsItem>?> = _deleteItems.asStateFlow()

    private val _showNewFolder = MutableStateFlow(false)
    val showNewFolder: StateFlow<Boolean> = _showNewFolder.asStateFlow()

    private val _compressItems = MutableStateFlow<List<FsItem>?>(null)
    val compressItems: StateFlow<List<FsItem>?> = _compressItems.asStateFlow()

    private val _extractArchiveItem = MutableStateFlow<FsItem?>(null)
    val extractArchiveItem: StateFlow<FsItem?> = _extractArchiveItem.asStateFlow()

    fun openContextMenu(item: FsItem) {
        _contextMenuItem.value = item
    }

    fun closeContextMenu() {
        _contextMenuItem.value = null
    }

    fun openRename(item: FsItem) {
        _renameItem.value = item
    }

    fun closeRename() {
        _renameItem.value = null
    }

    fun openDelete(items: List<FsItem>) {
        _deleteItems.value = items
    }

    fun closeDelete() {
        _deleteItems.value = null
    }

    fun openNewFolder() {
        _showNewFolder.value = true
    }

    fun closeNewFolder() {
        _showNewFolder.value = false
    }

    fun openCompress(items: List<FsItem>) {
        _compressItems.value = items
    }

    fun closeCompress() {
        _compressItems.value = null
    }

    fun openExtract(item: FsItem) {
        _extractArchiveItem.value = item
    }

    fun closeExtract() {
        _extractArchiveItem.value = null
    }
}
