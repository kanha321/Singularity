package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.QuickPlace
import com.kanhaji.singularity.feature.files.model.QuickPlaceIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object QuickPlacesRepository {

    private val defaultPlaces = listOf(
        QuickPlace("home", "Home", "~", QuickPlaceIcon.HOME, isProtected = false),
        QuickPlace("downloads", "Downloads", "~/Downloads", QuickPlaceIcon.DOWNLOADS, isProtected = false),
        QuickPlace("projects", "Projects", "~/Projects", QuickPlaceIcon.PROJECTS, isProtected = false),
        QuickPlace("documents", "Documents", "~/Documents", QuickPlaceIcon.DOCUMENTS, isProtected = false),
        QuickPlace("pictures", "Pictures", "~/Pictures", QuickPlaceIcon.PICTURES, isProtected = false),
        QuickPlace("videos", "Videos", "~/Videos", QuickPlaceIcon.VIDEOS, isProtected = false),
        QuickPlace("root", "Root", "/", QuickPlaceIcon.ROOT, isProtected = true)
    )

    private val _places = MutableStateFlow(defaultPlaces)
    val places = _places.asStateFlow()

    fun resetToDefaults() {
        _places.value = defaultPlaces
    }

    fun getPlaceByPath(path: String): QuickPlace? {
        return _places.value.find { it.path == path }
    }

    fun isBookmarked(path: String): Boolean {
        return _places.value.any { it.path == path }
    }

    fun addBookmark(name: String, path: String): Boolean {
        if (isBookmarked(path)) return false
        val newBookmark = QuickPlace(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            path = path,
            iconType = QuickPlaceIcon.BOOKMARK,
            isProtected = path.startsWith("/") && !path.startsWith("/home/kanhaji"),
            isCustomBookmark = true
        )
        _places.value = _places.value + newBookmark
        return true
    }

    fun removeBookmark(path: String): Boolean {
        val place = getPlaceByPath(path) ?: return false
        if (!place.isCustomBookmark) return false
        _places.value = _places.value.filterNot { it.path == path }
        return true
    }
}
