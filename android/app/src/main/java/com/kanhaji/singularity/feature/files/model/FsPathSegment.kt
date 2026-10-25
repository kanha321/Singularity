package com.kanhaji.singularity.feature.files.model

import com.kanhaji.singularity.feature.files.util.isArchivePath
import com.kanhaji.singularity.feature.files.util.splitArchivePath

data class PathSegment(
    val name: String,
    val fullPath: String,
    val isHome: Boolean = false,
    val isRoot: Boolean = false,
    val isArchive: Boolean = false
)

fun parsePathSegments(raw: String, isSudoActive: Boolean = false): List<PathSegment> {
    if (isArchivePath(raw)) {
        val (archivePath, internalSubpath) = splitArchivePath(raw)
        val baseSegments = parseNormalSegments(archivePath, isSudoActive).toMutableList()
        if (baseSegments.isNotEmpty()) {
            val last = baseSegments.removeAt(baseSegments.size - 1)
            baseSegments.add(last.copy(fullPath = "$archivePath!/", isArchive = true))
        }
        if (internalSubpath.isNotEmpty()) {
            var cum = "$archivePath!"
            for (part in internalSubpath.split("/").filter { it.isNotEmpty() }) {
                cum = "$cum/$part"
                baseSegments.add(PathSegment(name = part, fullPath = cum))
            }
        }
        return baseSegments
    }
    return parseNormalSegments(raw, isSudoActive)
}

private fun parseNormalSegments(raw: String, isSudoActive: Boolean): List<PathSegment> {
    val segs = mutableListOf<PathSegment>()

    if (raw.isEmpty() || raw == "~") {
        if (isSudoActive) segs.add(PathSegment("/", "/", isRoot = true))
        segs.add(PathSegment("Home", "~", isHome = true))
        return segs
    }

    if (raw.startsWith("~/")) {
        if (isSudoActive) segs.add(PathSegment("/", "/", isRoot = true))
        segs.add(PathSegment("Home", "~", isHome = true))
        var cum = "~"
        for (part in raw.removePrefix("~/").split("/").filter { it.isNotEmpty() }) {
            cum = "$cum/$part"
            segs.add(PathSegment(part, cum))
        }
        return segs
    }

    if (raw.startsWith("/home/kanhaji")) {
        if (isSudoActive) segs.add(PathSegment("/", "/", isRoot = true))
        segs.add(PathSegment("Home", "~", isHome = true))
        val rest = raw.removePrefix("/home/kanhaji").trim('/')
        if (rest.isNotEmpty()) {
            var cum = "/home/kanhaji"
            for (p in rest.split("/").filter { it.isNotEmpty() }) {
                cum = "$cum/$p"
                segs.add(PathSegment(p, cum))
            }
        }
        return segs
    }

    if (raw.startsWith("/")) {
        segs.add(PathSegment("/", "/", isRoot = true))
        var cum = ""
        for (p in raw.trim('/').split("/").filter { it.isNotEmpty() }) {
            cum = "$cum/$p"
            segs.add(PathSegment(p, cum))
        }
        return segs
    }

    return listOf(PathSegment(raw, raw))
}
