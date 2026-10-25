package com.kanhaji.singularity.feature.player.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class LeftSideOvalShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * 0.9f, 0f)
            cubicTo(
                x1 = size.width * 0.9f, y1 = 0f,
                x2 = size.width * 1.1f, y2 = size.height / 2,
                x3 = size.width * 0.9f, y3 = size.height
            )
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path = path)
    }
}

class RightSideOvalShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width, size.height)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.1f, 0f)
            cubicTo(
                x1 = size.width * 0.1f, y1 = 0f,
                x2 = -size.width * 0.1f, y2 = size.height / 2,
                x3 = size.width * 0.1f, y3 = size.height
            )
            close()
        }
        return Outline.Generic(path = path)
    }
}
