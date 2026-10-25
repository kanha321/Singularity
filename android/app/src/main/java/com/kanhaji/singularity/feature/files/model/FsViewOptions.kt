package com.kanhaji.singularity.feature.files.model

enum class SortField(val displayName: String) {
    NAME("Name"),
    MODIFIED("Date"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class FsViewOptions(
    val sortBy: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING,
    val showHidden: Boolean = false,
    val foldersFirst: Boolean = true
)

fun List<FsItem>.applyOptions(options: FsViewOptions): List<FsItem> {
    val filtered = if (options.showHidden) this else this.filter { !it.is_hidden }

    return filtered.sortedWith { a, b ->
        if (options.foldersFirst && a.is_dir != b.is_dir) {
            return@sortedWith if (a.is_dir) -1 else 1
        }

        val cmp = when (options.sortBy) {
            SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
            SortField.MODIFIED -> a.modified.compareTo(b.modified)
            SortField.SIZE -> a.size.compareTo(b.size)
            SortField.TYPE -> {
                val typeA = if (a.is_dir) "0" else a.mime_type
                val typeB = if (b.is_dir) "0" else b.mime_type
                typeA.compareTo(typeB)
            }
        }

        if (options.direction == SortDirection.ASCENDING) cmp else -cmp
    }
}
