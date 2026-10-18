package com.blefinder.android.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blefinder.android.Permissions
import com.blefinder.android.map.HeatRaster
import com.blefinder.android.map.MapFrame
import com.blefinder.android.motion.MotionAvailability
import com.blefinder.android.signal.ColorRamp
import kotlin.math.max
import kotlin.math.roundToInt

private const val CELL = 0.5

/** The 2D signal map below the gauge (UI choice 5A), or why it is not available. */
@Composable
fun FinderMapArea(frame: MapFrame, availability: MotionAvailability, onMotionGranted: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (availability) {
                MotionAvailability.NO_SENSOR -> MapMessage("This phone has no step sensor, so the map is off. The gauge and vibration still work.")
                MotionAvailability.NO_PERMISSION -> {
                    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) onMotionGranted()
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MapMessage("Allow Physical activity so the step sensor can draw the map as you walk.")
                        FilledTonalButton(onClick = { launcher.launch(Permissions.MOTION) }) { Text("Allow") }
                    }
                }
                MotionAvailability.OK -> {
                    HeatmapCanvas(frame)
                    if (frame.snapshot == null) MapMessage("Walk around slowly to paint the map.")
                }
            }
        }
        frame.snapshot?.takeIf { availability == MotionAvailability.OK }?.let { Legend(it.lo, it.hi) }
    }
}

@Composable
private fun MapMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun HeatmapCanvas(frame: MapFrame) {
    val bitmap = remember(frame.raster) { frame.raster?.toImageBitmap() }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        val view = Viewport.fit(frame, size.width, size.height)
        val raster = frame.raster
        if (bitmap != null && raster != null) {
            val topLeft = view.toScreen(raster.originIx * CELL, (raster.originIy + 1) * CELL)
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize((raster.width * CELL * view.scale).roundToInt(), (raster.height * CELL * view.scale).roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
        drawPathLine(frame.path, view, ink)
        drawStart(view, ink)
        drawPosition(frame, view, ink)
        drawScaleBar(view, ink)
    }
}

private fun HeatRaster.toImageBitmap() =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** World metres (x east, y forward) to canvas pixels, fitted around everything worth showing. */
private class Viewport(val scale: Float, val midX: Double, val midY: Double, val cx: Float, val cy: Float) {
    fun toScreen(x: Double, y: Double) = Offset(cx + ((x - midX) * scale).toFloat(), cy - ((y - midY) * scale).toFloat())

    companion object {
        private const val MARGIN_M = 1.0
        private const val MIN_EXTENT_M = 4.0

        fun fit(frame: MapFrame, width: Float, height: Float): Viewport {
            var minX = frame.pose.x
            var maxX = frame.pose.x
            var minY = frame.pose.y
            var maxY = frame.pose.y
            frame.path.forEach { (x, y) ->
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
            frame.snapshot?.let { s ->
                minX = minOf(minX, s.minIx * CELL); maxX = maxOf(maxX, (s.maxIx + 1) * CELL)
                minY = minOf(minY, s.minIy * CELL); maxY = maxOf(maxY, (s.maxIy + 1) * CELL)
            }
            val extentX = max(maxX - minX + 2 * MARGIN_M, MIN_EXTENT_M)
            val extentY = max(maxY - minY + 2 * MARGIN_M, MIN_EXTENT_M)
            val scale = minOf(width / extentX, height / extentY).toFloat()
            return Viewport(scale, (minX + maxX) / 2, (minY + maxY) / 2, width / 2, height / 2)
        }
    }
}

private fun DrawScope.drawPathLine(path: List<Pair<Double, Double>>, view: Viewport, ink: Color) {
    if (path.size < 2) return
    val p = Path()
    path.forEachIndexed { i, (x, y) ->
        val o = view.toScreen(x, y)
        if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
    }
    drawPath(
        p,
        ink.copy(alpha = 0.55f),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}

private fun DrawScope.drawStart(view: Viewport, ink: Color) {
    drawCircle(ink.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = view.toScreen(0.0, 0.0), style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawPosition(frame: MapFrame, view: Viewport, ink: Color) {
    val c = view.toScreen(frame.pose.x, frame.pose.y)
    val r = 7.dp.toPx()
    rotate(Math.toDegrees(frame.pose.heading).toFloat(), pivot = c) {
        val arrow = Path().apply {
            moveTo(c.x, c.y - r * 1.8f)
            lineTo(c.x + r * 0.9f, c.y - r * 0.2f)
            lineTo(c.x - r * 0.9f, c.y - r * 0.2f)
            close()
        }
        drawPath(arrow, ink)
    }
    drawCircle(ink, radius = r * 0.75f, center = c)
}

private fun DrawScope.drawScaleBar(view: Viewport, ink: Color) {
    val metre = view.scale
    val y = size.height - 2.dp.toPx()
    drawLine(ink.copy(alpha = 0.6f), Offset(0f, y), Offset(metre, y), strokeWidth = 2.dp.toPx())
    drawLine(ink.copy(alpha = 0.6f), Offset(0f, y - 4.dp.toPx()), Offset(0f, y), strokeWidth = 2.dp.toPx())
    drawLine(ink.copy(alpha = 0.6f), Offset(metre, y - 4.dp.toPx()), Offset(metre, y), strokeWidth = 2.dp.toPx())
}

@Composable
private fun Legend(lo: Double, hi: Double) {
    val ramp = remember { (0..4).map { Color(ColorRamp.argb(it / 4.0)) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text("${lo.roundToInt()} dBm", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .background(Brush.horizontalGradient(ramp), RoundedCornerShape(3.dp)),
        )
        Text("${hi.roundToInt()} dBm", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        Text("· bar = 1 m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
