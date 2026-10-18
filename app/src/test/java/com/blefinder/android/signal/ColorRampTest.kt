package com.blefinder.android.signal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorRampTest {
    @Test
    fun endsAreBlueAndRed() {
        assertThat(ColorRamp.argb(0.0)).isEqualTo(0xFF3D6BFF.toInt())
        assertThat(ColorRamp.argb(1.0)).isEqualTo(0xFFFF5A4E.toInt())
    }

    @Test
    fun hitsEachStopExactly() {
        assertThat(ColorRamp.argb(0.25)).isEqualTo(0xFF22C3E6.toInt())
        assertThat(ColorRamp.argb(0.5)).isEqualTo(0xFF3CCF7A.toInt())
        assertThat(ColorRamp.argb(0.75)).isEqualTo(0xFFF2C94C.toInt())
    }

    @Test
    fun clampsOutOfRangeAndNaN() {
        assertThat(ColorRamp.argb(-1.0)).isEqualTo(ColorRamp.argb(0.0))
        assertThat(ColorRamp.argb(2.0)).isEqualTo(ColorRamp.argb(1.0))
        assertThat(ColorRamp.argb(Double.NaN)).isEqualTo(ColorRamp.argb(0.0))
    }

    @Test
    fun appliesAlpha() {
        assertThat(ColorRamp.argb(1.0, 0x80) ushr 24).isEqualTo(0x80)
    }

    @Test
    fun redChannelRisesTowardsHot() {
        val reds = (0..10).map { ColorRamp.argb(it / 10.0) shr 16 and 0xFF }
        assertThat(reds.last()).isGreaterThan(reds.first())
    }
}
