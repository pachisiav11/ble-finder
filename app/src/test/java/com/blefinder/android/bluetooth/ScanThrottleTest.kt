package com.blefinder.android.bluetooth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanThrottleTest {
    private val s = 1_000_000_000L

    @Test
    fun allowsFourStartsThenMakesTheFifthWait() {
        val t = ScanThrottle()
        repeat(4) {
            assertThat(t.waitNanos(it * s)).isEqualTo(0)
            t.record(it * s)
        }
        assertThat(t.waitNanos(5 * s)).isEqualTo(25 * s)
    }

    @Test
    fun oldStartsLeaveTheWindow() {
        val t = ScanThrottle()
        repeat(4) { t.record(it * s) }
        assertThat(t.waitNanos(30 * s)).isEqualTo(0)
    }
}
