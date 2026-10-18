package com.blefinder.android.diagnostics

import com.blefinder.android.bluetooth.ConnectedVia
import com.blefinder.android.bluetooth.DeviceKind
import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.LinkType
import com.blefinder.android.bluetooth.RssiSample
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.bluetooth.SourceTestResult
import com.blefinder.android.bluetooth.UnmatchedAdvert
import com.blefinder.android.signal.SignalTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsReportTest {
    private val s = 1_000_000_000L
    private val buds = KnownDevice("AA:AA:AA:AA:AA:01", "Earbuds Pro", LinkType.DUAL, DeviceKind.AUDIO, true, setOf(ConnectedVia.A2DP))
    private val tag = KnownDevice("AA:AA:AA:AA:AA:02", "Key tag", LinkType.LE, DeviceKind.OTHER, true, emptySet())
    private val renamed = KnownDevice(
        "AA:AA:AA:AA:AA:03", "My headphones", LinkType.DUAL, DeviceKind.AUDIO, true, emptySet(), ownName = "Hesh ANC",
    )

    private fun input(
        devices: List<KnownDevice>,
        tracker: SignalTracker = SignalTracker(),
        tests: Map<String, SourceTestResult> = emptyMap(),
        unmatched: List<UnmatchedAdvert> = emptyList(),
        nowNanos: Long = 13 * s,
    ) = DiagnosticsInput(
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
        devices = devices,
        signals = tracker.states.value,
        tests = tests,
        unmatched = unmatched,
        nowNanos = nowNanos,
    )

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
    fun nameMatchesUseTheDevicesOwnNameNotOnlyTheAlias() {
        val unmatched = listOf(UnmatchedAdvert("5A:00:00:00:00:03", "Hesh ANC", -60, 0))
        assertThat(DiagnosticsReport.nameMatches(listOf(renamed), unmatched)).hasSize(1)
    }

    @Test
    fun reportListsEveryDeviceWithItsSignalAndCounts() {
        val tracker = SignalTracker()
        tracker.onSample(RssiSample(buds.address, -58, null, Source.ADV, 10 * s))
        tracker.onSample(RssiSample(buds.address, -60, null, Source.GATT, 11 * s))
        val report = DiagnosticsReport.build(
            input(
                devices = listOf(buds, tag, renamed),
                tracker = tracker,
                unmatched = listOf(UnmatchedAdvert("5A:00:00:00:00:01", "Earbuds Pro", -61, 12 * s)),
            ),
        )
        assertThat(report).contains("Phone: Acme P1, Android 15 (API 35)")
        assertThat(report).contains("BLUETOOTH_SCAN=yes")
        assertThat(report).contains("step detector=no")
        assertThat(report).contains("(last error 133)")
        assertThat(report).contains("- Earbuds Pro [AA:AA:AA:AA:AA:01] DUAL, AUDIO, bonded, connected: A2DP")
        assertThat(report).contains("ADV=1 GATT=1 CLASSIC=0")
        assertThat(report).contains("- Key tag [AA:AA:AA:AA:AA:02]")
        assertThat(report).contains("- My headphones (own name \"Hesh ANC\") [AA:AA:AA:AA:AA:03]")
        assertThat(report).contains("signal: never heard")
        assertThat(report).contains("Known names heard from a different address (1):")
        assertThat(report).contains("\"Earbuds Pro\" at 5A:00:00:00:00:01")
        assertThat(report).contains("Other named adverts heard (0), strongest first:")
        assertThat(report).doesNotContain("Location services")
    }

    @Test
    fun reportShowsTestResultsOrNotRun() {
        val report = DiagnosticsReport.build(
            input(
                devices = listOf(buds, tag),
                tests = mapOf(buds.address to SourceTestResult(adv = 3, gatt = 0, gattConnected = false, gattError = 133, classic = 0)),
            ),
        )
        assertThat(report).contains("  test: BLE +3, GATT +0 (never connected, error 133), classic +0")
        assertThat(report).contains("  test: not run")
    }

    @Test
    fun otherAdvertsAreListedStrongestFirst() {
        val report = DiagnosticsReport.build(
            input(
                devices = listOf(buds),
                unmatched = listOf(
                    UnmatchedAdvert("5A:00:00:00:00:02", "Far TV", -90, 12 * s),
                    UnmatchedAdvert("5A:00:00:00:00:03", "Near watch", -40, 12 * s),
                ),
            ),
        )
        assertThat(report).contains("Other named adverts heard (2), strongest first:")
        assertThat(report.indexOf("Near watch")).isLessThan(report.indexOf("Far TV"))
        assertThat(report).contains("- \"Near watch\" at 5A:00:00:00:00:03, -40 dBm, 1.0 s ago")
    }

    @Test
    fun agesNeverGoNegative() {
        val tracker = SignalTracker()
        tracker.onSample(RssiSample(buds.address, -50, null, Source.ADV, 13 * s + 100_000_000))
        val report = DiagnosticsReport.build(input(devices = listOf(buds), tracker = tracker, nowNanos = 13 * s))
        assertThat(report).contains("0.0 s ago via ADV")
        assertThat(report).doesNotContain("-0.1 s ago")
    }
}
