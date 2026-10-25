package com.kanhaji.singularity.feature.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.navigation.screen.NavScreen
import androidx.compose.runtime.rememberCoroutineScope
import com.kanhaji.singularity.feature.docs.TextPreviewActivity
import com.kanhaji.singularity.feature.docs.data.DocumentDownloader
import com.kanhaji.singularity.feature.docs.util.DocFileUtils
import com.kanhaji.singularity.feature.docs.util.DocumentOpener
import com.kanhaji.singularity.feature.files.data.FsDialogManager
import com.kanhaji.singularity.feature.files.security.FsBiometricHelper
import com.kanhaji.singularity.feature.files.security.FsSudoSessionManager
import com.kanhaji.singularity.feature.image.ImageViewerActivity
import com.kanhaji.singularity.feature.image.util.ImageFileUtils
import com.kanhaji.singularity.feature.player.util.VideoFileUtils
import com.kanhaji.singularity.navigation.LocalRootNavigator
import com.kanhaji.singularity.ui.components.AppSnackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class FolderNavScreen(val path: String = "~") : NavScreen {
    @Composable
    override fun Content() {
        val fileNavigator = LocalNavigator.current
        val rootNav = LocalRootNavigator.current ?: fileNavigator
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        FolderContentList(
            path = path,
            onFolderClick = { subfolderPath ->
                if (FsSudoSessionManager.canAccessPath(subfolderPath)) {
                    fileNavigator.navigate(FolderNavScreen(subfolderPath))
                } else {
                    FsBiometricHelper.promptSudoElevation(
                        context = context,
                        title = "Sudo Mode Required",
                        subtitle = "Verify fingerprint to open $subfolderPath",
                        onSuccess = {
                            fileNavigator.navigate(FolderNavScreen(subfolderPath))
                        }
                    )
                }
            },
            onFileClick = { item ->
                when {
                    VideoFileUtils.isVideoFile(item.name) -> {
                        val intent = com.kanhaji.singularity.feature.player.VideoPlayerActivity.createIntent(
                            context = context,
                            remotePath = item.path,
                            title = item.name
                        )
                        context.startActivity(intent)
                    }
                    ImageFileUtils.isImageFile(item.name) -> {
                        val intent = ImageViewerActivity.createIntent(
                            context = context,
                            currentPath = item.path,
                            folderPath = path,
                            title = item.name
                        )
                        context.startActivity(intent)
                    }
                    DocFileUtils.isTextFile(item.name) -> {
                        val intent = TextPreviewActivity.createIntent(
                            context = context,
                            remotePath = item.path,
                            title = item.name,
                            formattedSize = item.formattedSize
                        )
                        context.startActivity(intent)
                    }
                    DocFileUtils.isPdfOrOffice(item.name) -> {
                        coroutineScope.launch {
                            AppSnackbar.show("Opening ${item.name}...")
                            val downloadResult = DocumentDownloader.downloadToCache(context, item.path, item.name)
                            if (downloadResult.isSuccess) {
                                val file = downloadResult.getOrThrow()
                                DocumentOpener.openDocument(context, file, DocFileUtils.getMimeType(item.name))
                            } else {
                                AppSnackbar.show("Failed to open ${item.name}")
                            }
                        }
                    }
                    else -> {
                        AppSnackbar.show("${item.name} (${item.formattedSize})")
                    }
                }
            },
            onItemMoreClick = { item ->
                FsDialogManager.openContextMenu(item)
            }
        )
    }
}
