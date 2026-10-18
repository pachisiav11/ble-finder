package com.blefinder.android.signal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KalmanFilterTest {
    private val ms = 1_000_000L

    @Test
    fun firstReadingIsTakenAsIs() {
        assertThat(KalmanFilter().update(-70, 1_000 * ms)).isEqualTo(-70.0)
    }

    @Test
    fun noisyReadingsAroundAMeanSettleNearIt() {
        val f = KalmanFilter()
        var t = 0L
        repeat(200) { i ->
            t += 100 * ms
            f.update(if (i % 2 == 0) -55 else -65, t)
        }
        assertThat(f.estimate).isWithin(1.5).of(-60.0)
    }

    @Test
    fun followsAStepChangeWithinAboutTwoSecondsAtTenHertz() {
        val f = KalmanFilter()
        var t = 0L
        repeat(50) { t += 100 * ms; f.update(-80, t) }
        repeat(20) { t += 100 * ms; f.update(-50, t) }
        assertThat(f.estimate).isGreaterThan(-53.0)
    }

    @Test
    fun aLongGapResetsToTheNewReading() {
        val f = KalmanFilter()
        f.update(-90, 0)
        f.update(-40, 11_000 * ms)
        assertThat(f.estimate).isEqualTo(-40.0)
    }

    @Test
    fun anOutOfOrderReadingDoesNotReset() {
        val f = KalmanFilter()
        repeat(30) { f.update(-70, (it + 1) * 100 * ms) }
        f.update(-40, 2_000 * ms)
        assertThat(f.estimate).isLessThan(-60.0)
    }

    @Test
    fun slowSourceIsBarelySmoothed() {
        val f = KalmanFilter()
        f.update(-80, 0)
        f.update(-60, 9_000 * ms)
        assertThat(f.estimate).isGreaterThan(-63.0)
    }
}
