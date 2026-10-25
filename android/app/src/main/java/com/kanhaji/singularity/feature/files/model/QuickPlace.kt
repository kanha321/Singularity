package com.kanhaji.singularity.feature.files.model

enum class QuickPlaceIcon {
    HOME,
    DOWNLOADS,
    PROJECTS,
    DOCUMENTS,
    PICTURES,
    VIDEOS,
    ROOT,
    BOOKMARK
}

data class QuickPlace(
    val id: String,
    val name: String,
    val path: String,
    val iconType: QuickPlaceIcon = QuickPlaceIcon.BOOKMARK,
    val isProtected: Boolean = false,
    val isCustomBookmark: Boolean = false
)
