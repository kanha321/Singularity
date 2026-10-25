package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsViewOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FsSettingsManager {
    private val _viewOptions = MutableStateFlow(FsViewOptions())
    val viewOptions = _viewOptions.asStateFlow()

    fun updateOptions(transform: (FsViewOptions) -> FsViewOptions) {
        _viewOptions.value = transform(_viewOptions.value)
    }

    fun setOptions(newOptions: FsViewOptions) {
        _viewOptions.value = newOptions
    }
}
