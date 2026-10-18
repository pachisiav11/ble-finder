package com.blefinder.android.diagnostics

import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.bluetooth.UnmatchedAdvert
import com.blefinder.android.signal.SignalState

data class DiagnosticsInput(
    val appVersion: String,
    val phone: String,
    val android: String,
    val bluetoothOn: Boolean,
    /** Only meaningful on Android 10–11, where scans need location services on. */
    val locationOn: Boolean?,
    val permissions: List<Pair<String, Boolean>>,
    val sensors: List<Pair<String, Boolean>>,
    val scanStatus: String,
    val gattState: String,
    val gattLastError: Int,
    val classicRunning: Boolean,
    val devices: List<KnownDevice>,
    val signals: Map<String, SignalState>,
    val unmatched: Collection<UnmatchedAdvert>,
    val nowNanos: Long,
)

/** Plain-text report the user pastes into chat; it is the only feedback channel from their phone. */
object DiagnosticsReport {
    /** Adverts carrying a known device's name from a different address: the address-matching failure mode. */
    fun nameMatches(devices: List<KnownDevice>, unmatched: Collection<UnmatchedAdvert>): List<UnmatchedAdvert> {
        val names = devices.mapTo(HashSet()) { it.name.lowercase() }
        return unmatched.filter { it.name.lowercase() in names }.sortedBy { it.name }
    }

    fun build(input: DiagnosticsInput): String = buildString {
        fun yn(b: Boolean) = if (b) "yes" else "no"
        appendLine("BLE Finder diagnostics — app ${input.appVersion}")
        appendLine("Phone: ${input.phone}, ${input.android}")
        append("Bluetooth: ${if (input.bluetoothOn) "on" else "off"}")
        input.locationOn?.let { append(" · Location services: ${if (it) "on" else "off"}") }
        appendLine()
        appendLine("Permissions: " + input.permissions.joinToString { (p, g) -> "${p.substringAfterLast('.')}=${yn(g)}" })
        appendLine("Sensors: " + input.sensors.joinToString { (s, g) -> "$s=${yn(g)}" })
        appendLine(
            "BLE scan: ${input.scanStatus} · GATT: ${input.gattState}" +
                (if (input.gattLastError != 0) " (last error ${input.gattLastError})" else "") +
                " · Classic inquiry: ${if (input.classicRunning) "running" else "off"}",
        )
        appendLine()
        appendLine("Known devices (${input.devices.size}):")
        input.devices.forEach { d ->
            val conn = if (d.connected.isEmpty()) "not connected" else "connected: " + d.connected.joinToString("+")
            appendLine("- ${d.name} [${d.address}] ${d.type}, ${d.kind}, ${if (d.bonded) "bonded" else "not bonded"}, $conn")
            val s = input.signals[d.address]
            if (s == null) {
                appendLine("  signal: never heard")
            } else {
                val counts = Source.entries.joinToString(" ") { "$it=${s.countBySource[it] ?: 0}" }
                appendLine(
                    "  signal: last ${s.last} dBm, filtered %.1f, %d samples, %.1f s ago via %s; %s".format(
                        s.filtered, s.samples, s.ageSeconds(input.nowNanos), s.lastSource, counts,
                    ),
                )
            }
        }
        val matches = nameMatches(input.devices, input.unmatched)
        appendLine()
        appendLine("Known names heard from a different address (${matches.size}):")
        matches.forEach { u ->
            appendLine("- \"${u.name}\" at ${u.address}, ${u.rssi} dBm, %.1f s ago".format((input.nowNanos - u.timeNanos) / 1e9))
        }
        appendLine()
        append("Other named adverts heard: ${input.unmatched.size - matches.size}")
    }
}
