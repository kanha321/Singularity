package com.kanhaji.singularity.feature.files.data

import androidx.compose.foundation.lazy.LazyListState
import java.util.concurrent.ConcurrentHashMap

object FsScrollStateManager {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()
    @Volatile
    var activeListState: LazyListState? = null

    fun savePosition(path: String, index: Int, offset: Int) {
        if (path.isNotEmpty()) {
            scrollPositions[path] = Pair(index, offset)
        }
    }

    fun getPosition(path: String): Pair<Int, Int>? {
        return scrollPositions[path]
    }

    val canExpand: Boolean
        get() {
            val state = activeListState ?: return true
            return state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        }

    fun clear() {
        scrollPositions.clear()
        activeListState = null
    }
}
