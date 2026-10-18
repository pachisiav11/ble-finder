package com.blefinder.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Half-circle hot/cold gauge (UI choice 6A). [strength] is 0..1; [active] greys it out when the
 * signal is lost so the last value stays readable without looking live.
 */
@Composable
fun Gauge(strength: Double, label: String, sublabel: String, active: Boolean, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(strength.toFloat().coerceIn(0f, 1f), tween(300), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val color = signalColor(animated.toDouble())
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .alpha(if (active) 1f else 0.4f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 22.dp.toPx()
            val diameter = minOf(size.width, size.height * 2) - stroke
            val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
            val arc = Size(diameter, diameter)
            drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0.005f) {
                drawArc(color, 180f, 180f * animated, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(label, style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(sublabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
