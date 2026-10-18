package com.blefinder.android.ui

import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.data.Saved
import com.blefinder.android.signal.SignalMath
import com.blefinder.android.signal.SignalState

enum class DeviceFilter(val label: String) { ALL("All"), SAVED("Saved only"), HEARD("Heard now") }

data class DeviceRow(
    val device: KnownDevice,
    val name: String,
    val pinned: Boolean,
    val signal: SignalState?,
    val fresh: Boolean,
)

fun Source.label(): String = when (this) {
    Source.ADV -> "BLE"
    Source.GATT -> "GATT"
    Source.CLASSIC -> "Classic"
}

object DeviceRows {
    fun subtitle(row: DeviceRow, nowNanos: Long): String {
        val s = row.signal
        return when {
            s != null && row.fresh ->
                "${SignalMath.formatDistance(s.distanceMetres)} · ${s.lastSource.label()} · ${SignalMath.formatAge(s.ageSeconds(nowNanos))}"
            row.device.classicAudioOnly -> "Connected for audio · no signal reading"
            s != null -> "Last heard ${SignalMath.formatAge(s.ageSeconds(nowNanos))}"
            row.device.connected.isNotEmpty() -> "Connected · not heard yet"
            else -> "Not heard"
        }
    }

    /**
     * Pinned first, then devices heard now, then the rest. Within a group, heard devices sort by
     * signal bars rather than raw dBm so rows do not jump on every reading, then by name.
     */
    fun build(devices: List<KnownDevice>, saved: Saved, signals: Map<String, SignalState>, nowNanos: Long, filter: DeviceFilter): List<DeviceRow> =
        devices
            .map { d ->
                val s = signals[d.address]
                DeviceRow(d, saved.nameFor(d.address, d.name), d.address in saved.pinned, s, s?.isFresh(nowNanos) == true)
            }
            .filter {
                when (filter) {
                    DeviceFilter.ALL -> true
                    DeviceFilter.SAVED -> it.pinned
                    DeviceFilter.HEARD -> it.fresh
                }
            }
            .sortedWith(
                compareByDescending<DeviceRow> { it.pinned }
                    .thenByDescending { it.fresh }
                    .thenByDescending { if (it.fresh) SignalMath.bars(it.signal!!.filtered) else 0 }
                    .thenBy { it.name.lowercase() },
            )
}
