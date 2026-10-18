package com.blefinder.android.map

import com.blefinder.android.signal.ColorRamp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeatmapGridTest {
    @Test
    fun binsIntoHalfMetreCellsIncludingNegativeCoordinates() {
        val g = HeatmapGrid()
        g.add(0.1, 0.1, -60.0)
        g.add(0.4, 0.4, -70.0)
        g.add(-0.1, -0.6, -80.0)
        val s = g.snapshot()!!
        val byCell = s.cells.associateBy { it.ix to it.iy }
        assertThat(byCell.getValue(0 to 0).mean).isEqualTo(-65.0)
        assertThat(byCell.getValue(0 to 0).count).isEqualTo(2)
        assertThat(byCell.getValue(-1 to -2).mean).isEqualTo(-80.0)
        assertThat(listOf(s.minIx, s.maxIx, s.minIy, s.maxIy)).containsExactly(-1, 0, -2, 0).inOrder()
    }

    @Test
    fun emptyGridHasNoSnapshot() {
        assertThat(HeatmapGrid().snapshot()).isNull()
    }

    @Test
    fun scaleKeepsTheStrongEndAndAMinimumSpan() {
        assertThat(HeatScale.range(listOf(-62.0, -60.0))).isEqualTo(-75.0 to -60.0)
        assertThat(HeatScale.range(listOf(-90.0, -50.0))).isEqualTo(-90.0 to -50.0)
    }

    @Test
    fun alphaGrowsWithSampleCount() {
        assertThat(HeatScale.alpha(1)).isLessThan(HeatScale.alpha(4))
        assertThat(HeatScale.alpha(100)).isEqualTo(1.0)
    }

    @Test
    fun rasterPutsCellsAtTheirPixelsWithAFaintFringe() {
        val g = HeatmapGrid()
        repeat(10) { g.add(0.2, 0.2, -50.0) } // cell (0, 0), strongest
        repeat(10) { g.add(1.2, 0.2, -90.0) } // cell (2, 0), weakest
        val s = g.snapshot()!!
        val r = HeatRasterizer.render(s, pad = 2)
        assertThat(r.width).isEqualTo(3 + 4)
        assertThat(r.height).isEqualTo(1 + 4)
        assertThat(r.originIx).isEqualTo(-2)
        assertThat(r.originIy).isEqualTo(2)
        fun px(ix: Int, iy: Int) = r.pixels[(r.originIy - iy) * r.width + (ix - r.originIx)]
        assertThat(px(0, 0)).isEqualTo(ColorRamp.argb(1.0))
        assertThat(px(2, 0)).isEqualTo(ColorRamp.argb(0.0))
        val gap = px(1, 0)
        assertThat(gap ushr 24).isGreaterThan(0)
        assertThat(gap ushr 24).isLessThan(255)
        assertThat(px(4, 0)).isEqualTo(0)
    }
}
