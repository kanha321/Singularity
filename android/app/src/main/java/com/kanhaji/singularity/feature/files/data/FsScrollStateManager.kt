package com.kanhaji.singularity.feature.files.data

import java.util.concurrent.ConcurrentHashMap

object FsScrollStateManager {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun savePosition(path: String, index: Int, offset: Int) {
        if (path.isNotEmpty()) {
            scrollPositions[path] = Pair(index, offset)
        }
    }

    fun getPosition(path: String): Pair<Int, Int>? {
        return scrollPositions[path]
    }

    fun clear() {
        scrollPositions.clear()
    }
}
