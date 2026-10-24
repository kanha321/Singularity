package com.kanhaji.singularity.feature.home.model

import androidx.compose.ui.graphics.vector.ImageVector

data class FeatureCardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val statusText: String,
    val isHighlighted: Boolean = false,
    val onClick: () -> Unit = {}
)
