package com.kanhaji.singularity.feature.files.model

enum class FsClipboardOp {
    COPY,
    CUT
}

data class FsClipboardState(
    val items: Set<FsItem> = emptySet(),
    val op: FsClipboardOp? = null
) {
    val isActive: Boolean get() = items.isNotEmpty() && op != null
    val count: Int get() = items.size
}
