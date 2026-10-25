package com.kanhaji.singularity.feature.docs.util

import android.webkit.MimeTypeMap

object DocFileUtils {

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "log", "json", "rs", "kt", "kts", "java", "py",
        "sh", "bash", "zsh", "fish", "toml", "yaml", "yml", "xml", "html", "htm",
        "css", "scss", "js", "ts", "jsx", "tsx", "c", "cpp", "cc", "h", "hpp",
        "conf", "ini", "cfg", "env", "properties", "sql", "diff", "patch",
        "lock", "service", "desktop", "csv", "tsv"
    )

    private val PDF_OFFICE_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "odp", "epub"
    )

    fun isTextFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    fun isPdfOrOffice(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in PDF_OFFICE_EXTENSIONS
    }

    fun getMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext == "pdf") return "application/pdf"
        if (ext in listOf("doc", "docx")) return "application/msword"
        if (ext in listOf("xls", "xlsx")) return "application/vnd.ms-excel"
        if (ext in listOf("ppt", "pptx")) return "application/vnd.ms-powerpoint"
        if (ext == "epub") return "application/epub+zip"
        if (isTextFile(filename)) return "text/plain"

        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return fromMap ?: "application/octet-stream"
    }
}
