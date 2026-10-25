package com.kanhaji.singularity.feature.files.model

data class FsSelectionState(
    val selectedItems: Set<FsItem> = emptySet()
) {
    val count: Int get() = selectedItems.size
    val isActive: Boolean get() = selectedItems.isNotEmpty()

    fun isSelected(item: FsItem): Boolean = selectedItems.any { it.path == item.path }

    fun toggle(item: FsItem): FsSelectionState {
        val newSet = if (isSelected(item)) {
            selectedItems.filterNot { it.path == item.path }.toSet()
        } else {
            selectedItems + item
        }
        return copy(selectedItems = newSet)
    }

    fun selectAll(items: List<FsItem>): FsSelectionState {
        return copy(selectedItems = items.toSet())
    }

    fun clear(): FsSelectionState = copy(selectedItems = emptySet())

    val totalSize: Long get() = selectedItems.sumOf { it.size }
    val filesCount: Int get() = selectedItems.count { !it.is_dir }
    val foldersCount: Int get() = selectedItems.count { it.is_dir }

    val formattedTotalSize: String
        get() {
            val bytes = totalSize
            return when {
                bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
                bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
                bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024)
                else -> "$bytes B"
            }
        }
}
