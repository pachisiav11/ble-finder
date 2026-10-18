package com.blefinder.android.diagnostics

import com.blefinder.android.bluetooth.ConnectedVia
import com.blefinder.android.bluetooth.DeviceKind
import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.LinkType
import com.blefinder.android.bluetooth.RssiSample
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.bluetooth.UnmatchedAdvert
import com.blefinder.android.signal.SignalTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsReportTest {
    private val s = 1_000_000_000L
    private val buds = KnownDevice("AA:AA:AA:AA:AA:01", "Earbuds Pro", LinkType.DUAL, DeviceKind.AUDIO, true, setOf(ConnectedVia.A2DP))
    private val tag = KnownDevice("AA:AA:AA:AA:AA:02", "Key tag", LinkType.LE, DeviceKind.OTHER, true, emptySet())

    @Test
    fun nameMatchesIgnoreCaseAndSkipOtherNames() {
        val unmatched = listOf(
            UnmatchedAdvert("5A:00:00:00:00:01", "earbuds pro", -60, 0),
            UnmatchedAdvert("5A:00:00:00:00:02", "Someone's TV", -70, 0),
        )
        assertThat(DiagnosticsReport.nameMatches(listOf(buds, tag), unmatched).map { it.address })
            .containsExactly("5A:00:00:00:00:01")
    }

    @Test
    fun reportListsEveryDeviceWithItsSignalAndCounts() {
        val tracker = SignalTracker()
        tracker.onSample(RssiSample(buds.address, -58, null, Source.ADV, 10 * s))
        tracker.onSample(RssiSample(buds.address, -60, null, Source.GATT, 11 * s))
        val report = DiagnosticsReport.build(
            DiagnosticsInput(
                appVersion = "0.1.0 (2)",
                phone = "Acme P1",
                android = "Android 15 (API 35)",
                bluetoothOn = true,
                locationOn = null,
                permissions = listOf("android.permission.BLUETOOTH_SCAN" to true),
                sensors = listOf("step detector" to false),
                scanStatus = "scanning",
                gattState = "IDLE",
                gattLastError = 133,
                classicRunning = false,
                devices = listOf(buds, tag),
                signals = tracker.states.value,
                unmatched = listOf(UnmatchedAdvert("5A:00:00:00:00:01", "Earbuds Pro", -61, 12 * s)),
                nowNanos = 13 * s,
            ),
        )
        assertThat(report).contains("Phone: Acme P1, Android 15 (API 35)")
        assertThat(report).contains("BLUETOOTH_SCAN=yes")
        assertThat(report).contains("step detector=no")
        assertThat(report).contains("(last error 133)")
        assertThat(report).contains("- Earbuds Pro [AA:AA:AA:AA:AA:01] DUAL, AUDIO, bonded, connected: A2DP")
        assertThat(report).contains("ADV=1 GATT=1 CLASSIC=0")
        assertThat(report).contains("- Key tag [AA:AA:AA:AA:AA:02]")
        assertThat(report).contains("signal: never heard")
        assertThat(report).contains("Known names heard from a different address (1):")
        assertThat(report).contains("\"Earbuds Pro\" at 5A:00:00:00:00:01")
        assertThat(report).doesNotContain("Location services")
    }
}
