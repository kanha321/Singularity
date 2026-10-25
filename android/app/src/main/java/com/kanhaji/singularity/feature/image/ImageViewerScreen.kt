package com.kanhaji.singularity.feature.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kanhaji.singularity.feature.files.data.FileBrowserRepository
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.image.state.ZoomableState
import com.kanhaji.singularity.feature.image.ui.ImageViewerTopBar
import com.kanhaji.singularity.feature.image.ui.ZoomableImage
import com.kanhaji.singularity.feature.image.util.ImageFileUtils

@Composable
fun ImageViewerScreen(
    initialPath: String,
    folderPath: String,
    initialTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var imageItems by remember {
        val cached = FileBrowserRepository.getCached(folderPath)
            ?.filter { ImageFileUtils.isImageFile(it.name) }
        mutableStateOf(
            if (!cached.isNullOrEmpty()) cached
            else listOf(FsItem(name = initialTitle, path = initialPath))
        )
    }

    LaunchedEffect(folderPath) {
        val result = FileBrowserRepository.listDirectory(folderPath)
        val fetched = result.getOrNull()?.filter { ImageFileUtils.isImageFile(it.name) }
        if (!fetched.isNullOrEmpty()) {
            imageItems = fetched
        }
    }

    val initialIndex = remember(imageItems) {
        imageItems.indexOfFirst { it.path == initialPath }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageItems.size }
    )

    val zoomStates = remember { mutableMapOf<Int, ZoomableState>() }
    val currentZoomState = zoomStates.getOrPut(pagerState.currentPage) { ZoomableState() }

    LaunchedEffect(pagerState.currentPage) {
        zoomStates.forEach { (index, state) ->
            if (index != pagerState.currentPage && state.isZoomed) {
                state.reset()
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    val currentItem = imageItems.getOrNull(pagerState.currentPage)
    val currentTitle = currentItem?.name ?: initialTitle

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentZoomState.isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = imageItems.getOrNull(page)
            if (item != null) {
                val pageZoomState = zoomStates.getOrPut(page) { ZoomableState() }
                ZoomableImage(
                    imageUrl = ImageFileUtils.buildImageUrl(item.path),
                    state = pageZoomState,
                    onSingleTap = { controlsVisible = !controlsVisible }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ImageViewerTopBar(
                title = currentTitle,
                currentIndex = pagerState.currentPage,
                totalCount = imageItems.size,
                onBack = onBack
            )
        }
    }
}
