package com.kanhaji.singularity.feature.docs.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kanhaji.singularity.ui.components.AppSnackbar
import java.io.File

object DocumentOpener {

    fun openDocument(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            AppSnackbar.show("No app found to open ${file.name}")
        }
    }
}
