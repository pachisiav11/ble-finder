package com.blefinder.android.signal

import kotlin.math.roundToInt

/**
 * The cold-to-hot ramp used everywhere a signal is coloured: list bars, gauge and map. It is fixed
 * rather than themed so that red means "close" no matter what the wallpaper palette is.
 */
object ColorRamp {
    private val stops = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)
    private val colors = intArrayOf(0x3D6BFF, 0x22C3E6, 0x3CCF7A, 0xF2C94C, 0xFF5A4E)

    /** Opaque ARGB for [t] in 0..1 (clamped). */
    fun argb(t: Double): Int = argb(t, 255)

    fun argb(t: Double, alpha: Int): Int {
        val x = if (t.isNaN()) 0.0 else t.coerceIn(0.0, 1.0)
        var i = 0
        while (i < stops.size - 2 && x > stops[i + 1]) i++
        val f = (x - stops[i]) / (stops[i + 1] - stops[i])
        val a = colors[i]
        val b = colors[i + 1]
        val r = lerp(a shr 16 and 0xFF, b shr 16 and 0xFF, f)
        val g = lerp(a shr 8 and 0xFF, b shr 8 and 0xFF, f)
        val bl = lerp(a and 0xFF, b and 0xFF, f)
        return (alpha.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun lerp(a: Int, b: Int, f: Double): Int = (a + (b - a) * f).roundToInt()
}
