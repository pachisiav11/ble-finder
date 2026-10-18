package com.blefinder.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Phone-style bars; [filled] of 5 are drawn in [color]. */
@Composable
fun SignalBars(filled: Int, color: Color, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Canvas(modifier.size(width = 24.dp, height = 16.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * 4) / 5
        for (i in 0 until 5) {
            val h = size.height * (i + 1) / 5f
            drawRoundRect(
                color = if (i < filled) color else empty,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}
