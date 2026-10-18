package com.blefinder.android.map

import com.blefinder.android.signal.ColorRamp
import kotlin.math.floor
import kotlin.math.min

data class CellValue(val ix: Int, val iy: Int, val mean: Double, val count: Int)

data class HeatmapSnapshot(
    val cells: List<CellValue>,
    val minIx: Int,
    val maxIx: Int,
    val minIy: Int,
    val maxIy: Int,
    /** Colour scale ends in dBm: [lo] is blue, [hi] is red. */
    val lo: Double,
    val hi: Double,
) {
    fun normalized(mean: Double): Double = ((mean - lo) / (hi - lo)).coerceIn(0.0, 1.0)
}

/** Readings binned into square cells of [cellSize] metres around the walked path. */
class HeatmapGrid(val cellSize: Double = 0.5) {
    private class Cell(var sum: Double = 0.0, var count: Int = 0)

    private val cells = HashMap<Long, Cell>()

    val isEmpty: Boolean get() = cells.isEmpty()

    fun add(x: Double, y: Double, rssi: Double) {
        val ix = floor(x / cellSize).toInt()
        val iy = floor(y / cellSize).toInt()
        val cell = cells.getOrPut(key(ix, iy)) { Cell() }
        cell.sum += rssi
        cell.count++
    }

    fun clear() = cells.clear()

    fun snapshot(): HeatmapSnapshot? {
        if (cells.isEmpty()) return null
        val values = cells.map { (k, c) -> CellValue((k shr 32).toInt(), k.toInt(), c.sum / c.count, c.count) }
        val (lo, hi) = HeatScale.range(values.map { it.mean })
        return HeatmapSnapshot(
            cells = values,
            minIx = values.minOf { it.ix },
            maxIx = values.maxOf { it.ix },
            minIy = values.minOf { it.iy },
            maxIy = values.maxOf { it.iy },
            lo = lo,
            hi = hi,
        )
    }

    private fun key(ix: Int, iy: Int): Long = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)
}

object HeatScale {
    const val MIN_SPAN_DB = 15.0

    /**
     * Scales to this session's weakest and strongest cells, so the map has contrast at any range.
     * The strong end is kept and the span is widened downwards to at least [MIN_SPAN_DB], so a
     * few dB of noise between cells never looks like a hotspot.
     */
    fun range(means: List<Double>): Pair<Double, Double> {
        val hi = means.max()
        val lo = min(means.min(), hi - MIN_SPAN_DB)
        return lo to hi
    }

    /** Cells with few readings are drawn more transparent. */
    fun alpha(count: Int): Double = min(1.0, 0.35 + count / 8.0)
}

/** ARGB pixels, one per cell, with [originIx]/[originIy] being the cell at pixel (0, 0) (top-left, y up in world). */
class HeatRaster(val width: Int, val height: Int, val pixels: IntArray, val originIx: Int, val originIy: Int)

object HeatRasterizer {
    /**
     * One pixel per cell plus [pad] empty pixels around. Empty pixels next to measured ones get a
     * faint average of their neighbours, so once the bitmap is drawn scaled up with bilinear
     * filtering the map reads as a soft heat blur (UI choice 7B) while measured cells keep their
     * own colour and opacity.
     */
    fun render(s: HeatmapSnapshot, pad: Int = 2): HeatRaster {
        val w = s.maxIx - s.minIx + 1 + 2 * pad
        val h = s.maxIy - s.minIy + 1 + 2 * pad
        val px = IntArray(w * h)
        val originIx = s.minIx - pad
        val originIy = s.maxIy + pad
        for (c in s.cells) {
            val x = c.ix - originIx
            val y = originIy - c.iy
            px[y * w + x] = ColorRamp.argb(s.normalized(c.mean), (HeatScale.alpha(c.count) * 255).toInt())
        }
        val out = px.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] ushr 24 != 0) continue
            var a = 0.0
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val p = px[ny * w + nx]
                val pa = (p ushr 24) / 255.0
                if (pa == 0.0) continue
                a += pa
                r += (p shr 16 and 0xFF) * pa
                g += (p shr 8 and 0xFF) * pa
                b += (p and 0xFF) * pa
                n++
            }
            if (n == 0) continue
            val alpha = (FRINGE * a / n * 255).toInt()
            out[y * w + x] = (alpha shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
        }
        return HeatRaster(w, h, out, originIx, originIy)
    }

    private const val FRINGE = 0.45
}
