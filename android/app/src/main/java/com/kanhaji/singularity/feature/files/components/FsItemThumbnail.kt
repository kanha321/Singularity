package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.util.buildThumbnailUrl
import com.kanhaji.singularity.service.AppBridgeService

@Composable
fun FsItemThumbnail(
    item: FsItem,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val shape = RoundedCornerShape(12.dp)
    val fileServerUrl by AppBridgeService.fileServerUrl.collectAsState()
    val thumbUrl = if (item.isThumbnailEligible) {
        buildThumbnailUrl(fileServerUrl ?: "http://127.0.0.1:8920", item.path)
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(primaryColor.copy(alpha = 0.12f), shape)
            .border(BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(iconSize)
        )

        if (thumbUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
