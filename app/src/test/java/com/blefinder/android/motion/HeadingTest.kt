package com.blefinder.android.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI

class HeadingTest {
    @Test
    fun flatPhoneUsesTheTopEdge() {
        assertThat(Heading.fromRotationMatrix(Heading.flatMatrix(0.0))!!).isWithin(1e-6).of(0.0)
        assertThat(Heading.fromRotationMatrix(Heading.flatMatrix(PI / 2))!!).isWithin(1e-6).of(PI / 2)
        assertThat(Heading.fromRotationMatrix(Heading.flatMatrix(-2.0))!!).isWithin(1e-6).of(-2.0)
    }

    @Test
    fun uprightPhoneUsesTheBack() {
        // Held upright facing north: top edge points up, screen faces south towards the user.
        val facingNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertThat(Heading.fromRotationMatrix(facingNorth)!!).isWithin(1e-6).of(0.0)
        // Same grip facing east: right edge points south, back points east.
        val facingEast = floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)
        assertThat(Heading.fromRotationMatrix(facingEast)!!).isWithin(1e-6).of(PI / 2)
    }

    @Test
    fun normalizeWrapsIntoMinusPiToPi() {
        assertThat(Heading.normalize(3 * PI / 2)).isWithin(1e-9).of(-PI / 2)
        assertThat(Heading.normalize(-3 * PI / 2)).isWithin(1e-9).of(PI / 2)
        assertThat(Heading.normalize(0.5)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun stepsMoveAlongTheHeading() {
        val north = Pose().step(0.7)
        assertThat(north.x).isWithin(1e-9).of(0.0)
        assertThat(north.y).isWithin(1e-9).of(0.7)
        val east = Pose(heading = PI / 2).step(0.5).step(0.5)
        assertThat(east.x).isWithin(1e-9).of(1.0)
        assertThat(east.y).isWithin(1e-9).of(0.0)
        assertThat(east.steps).isEqualTo(2)
    }
}
