package com.blefinder.android.ui

import com.blefinder.android.bluetooth.ConnectedVia
import com.blefinder.android.bluetooth.DeviceKind
import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.LinkType
import com.blefinder.android.bluetooth.RssiSample
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.data.Saved
import com.blefinder.android.signal.SignalTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceRowsTest {
    private val s = 1_000_000_000L
    private fun dev(n: Int, name: String, type: LinkType = LinkType.LE, connected: Set<ConnectedVia> = emptySet()) =
        KnownDevice("AA:AA:AA:AA:AA:0$n", name, type, DeviceKind.OTHER, true, connected)

    private val near = dev(1, "Near")
    private val far = dev(2, "Far")
    private val silent = dev(3, "Alpha silent")
    private val stale = dev(4, "Stale")
    private val car = dev(5, "Car", LinkType.CLASSIC, setOf(ConnectedVia.A2DP))
    private val devices = listOf(silent, far, car, stale, near)

    private val tracker = SignalTracker().apply {
        onSample(RssiSample(near.address, -50, null, Source.ADV, 100 * s))
        onSample(RssiSample(far.address, -90, null, Source.ADV, 100 * s))
        onSample(RssiSample(stale.address, -40, null, Source.ADV, 10 * s))
    }
    private val now = 101 * s

    @Test
    fun sortsPinnedThenHeardByBarsThenName() {
        val rows = DeviceRows.build(devices, Saved(pinned = setOf(far.address)), tracker.states.value, now, DeviceFilter.ALL)
        assertThat(rows.map { it.name }).containsExactly("Far", "Near", "Alpha silent", "Car", "Stale").inOrder()
    }

    @Test
    fun filtersSavedAndHeard() {
        val saved = Saved(pinned = setOf(stale.address))
        assertThat(DeviceRows.build(devices, saved, tracker.states.value, now, DeviceFilter.SAVED).map { it.name })
            .containsExactly("Stale")
        assertThat(DeviceRows.build(devices, saved, tracker.states.value, now, DeviceFilter.HEARD).map { it.name })
            .containsExactly("Near", "Far").inOrder()
    }

    @Test
    fun customNamesReplaceSystemNames() {
        val rows = DeviceRows.build(listOf(near), Saved(names = mapOf(near.address to "My buds")), emptyMap(), now, DeviceFilter.ALL)
        assertThat(rows.single().name).isEqualTo("My buds")
    }

    @Test
    fun subtitlesDescribeEachState() {
        val rows = DeviceRows.build(devices, Saved(), tracker.states.value, now, DeviceFilter.ALL).associateBy { it.name }
        assertThat(DeviceRows.subtitle(rows.getValue("Near"), now)).isEqualTo("~0.4 m · BLE · 1.0 s ago")
        assertThat(DeviceRows.subtitle(rows.getValue("Car"), now)).isEqualTo("Connected for audio · no signal reading")
        assertThat(DeviceRows.subtitle(rows.getValue("Stale"), now)).isEqualTo("Last heard 1 min ago")
        assertThat(DeviceRows.subtitle(rows.getValue("Alpha silent"), now)).isEqualTo("Not heard")
    }
}
