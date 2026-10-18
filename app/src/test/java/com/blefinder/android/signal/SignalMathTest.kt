package com.blefinder.android.signal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignalMathTest {
    @Test
    fun rssiAtOneMetreDefaultsWhenTxPowerIsMissingOrImplausible() {
        assertThat(SignalMath.rssiAt1m(null)).isEqualTo(-59)
        assertThat(SignalMath.rssiAt1m(100)).isEqualTo(-59)
        assertThat(SignalMath.rssiAt1m(-60)).isEqualTo(-59)
    }

    @Test
    fun rssiAtOneMetreSubtractsFirstMetreLoss() {
        assertThat(SignalMath.rssiAt1m(0)).isEqualTo(-41)
        assertThat(SignalMath.rssiAt1m(-12)).isEqualTo(-53)
    }

    @Test
    fun distanceIsOneMetreAtReferenceAndGrowsAsSignalDrops() {
        assertThat(SignalMath.distanceMetres(-59.0)).isWithin(1e-9).of(1.0)
        assertThat(SignalMath.distanceMetres(-84.0)).isWithin(1e-6).of(10.0)
        assertThat(SignalMath.distanceMetres(-50.0)).isLessThan(1.0)
    }

    @Test
    fun formatsDistanceByRange() {
        assertThat(SignalMath.formatDistance(0.1)).isEqualTo("<0.3 m")
        assertThat(SignalMath.formatDistance(1.84)).isEqualTo("~1.8 m")
        assertThat(SignalMath.formatDistance(12.4)).isEqualTo("~12 m")
        assertThat(SignalMath.formatDistance(40.0)).isEqualTo(">30 m")
    }

    @Test
    fun barsStepEveryTenDecibels() {
        assertThat(SignalMath.bars(-40.0)).isEqualTo(5)
        assertThat(SignalMath.bars(-60.0)).isEqualTo(4)
        assertThat(SignalMath.bars(-70.0)).isEqualTo(3)
        assertThat(SignalMath.bars(-80.0)).isEqualTo(2)
        assertThat(SignalMath.bars(-99.0)).isEqualTo(1)
    }

    @Test
    fun strengthIsClampedToTheGaugeRange() {
        assertThat(SignalMath.strength(-100.0)).isEqualTo(0.0)
        assertThat(SignalMath.strength(-70.0)).isWithin(1e-9).of(0.5)
        assertThat(SignalMath.strength(-20.0)).isEqualTo(1.0)
        assertThat(SignalMath.strength(-120.0)).isEqualTo(0.0)
    }

    @Test
    fun vibrationSpeedsUpAsSignalStrengthens() {
        assertThat(SignalMath.vibrationIntervalMs(0.0)).isEqualTo(1500)
        assertThat(SignalMath.vibrationIntervalMs(1.0)).isEqualTo(120)
        assertThat(SignalMath.vibrationIntervalMs(0.5)).isEqualTo(810)
        assertThat(SignalMath.vibrationIntervalMs(3.0)).isEqualTo(120)
    }
}
