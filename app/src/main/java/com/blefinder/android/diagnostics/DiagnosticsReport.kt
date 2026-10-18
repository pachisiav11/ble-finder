package com.blefinder.android.diagnostics

import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.bluetooth.SourceTestResult
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
    val tests: Map<String, SourceTestResult>,
    val unmatched: Collection<UnmatchedAdvert>,
    val nowNanos: Long,
)

/** Plain-text report the user pastes into chat; it is the only feedback channel from their phone. */
object DiagnosticsReport {
    /**
     * Adverts carrying a known device's name from a different address: the address-matching
     * failure mode. Checks the device's own name too, since adverts never carry the user's alias.
     */
    fun nameMatches(devices: List<KnownDevice>, unmatched: Collection<UnmatchedAdvert>): List<UnmatchedAdvert> {
        val names = HashSet<String>()
        devices.forEach { d ->
            names += d.name.lowercase()
            d.ownName?.let { names += it.lowercase() }
        }
        return unmatched.filter { it.name.lowercase() in names }.sortedBy { it.name }
    }

    fun testSummary(t: SourceTestResult): String {
        val gatt = (if (t.gattConnected) "connected" else "never connected") + (if (t.gattError != 0) ", error ${t.gattError}" else "")
        return "BLE +${t.adv}, GATT +${t.gatt} ($gatt), classic +${t.classic}"
    }

    fun build(input: DiagnosticsInput): String = buildString {
        fun yn(b: Boolean) = if (b) "yes" else "no"
        fun advert(u: UnmatchedAdvert) =
            "- \"${u.name}\" at ${u.address}, ${u.rssi} dBm, %.1f s ago".format(maxOf(0.0, (input.nowNanos - u.timeNanos) / 1e9))

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
            val own = d.ownName?.let { " (own name \"$it\")" }.orEmpty()
            appendLine("- ${d.name}$own [${d.address}] ${d.type}, ${d.kind}, ${if (d.bonded) "bonded" else "not bonded"}, $conn")
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
            appendLine("  test: " + (input.tests[d.address]?.let(::testSummary) ?: "not run"))
        }
        val matches = nameMatches(input.devices, input.unmatched)
        appendLine()
        appendLine("Known names heard from a different address (${matches.size}):")
        matches.forEach { appendLine(advert(it)) }
        val others = input.unmatched.filter { it !in matches }.sortedByDescending { it.rssi }
        appendLine()
        appendLine("Other named adverts heard (${others.size}), strongest first:")
        others.forEach { appendLine(advert(it)) }
    }.trimEnd()
}
