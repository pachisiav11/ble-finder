package com.blefinder.android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import com.blefinder.android.Permissions
import com.blefinder.android.signal.SignalTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Progress of Diagnostics' "Test all sources" for one device. */
data class SourceTest(val address: String, val step: Step) {
    enum class Step { BLE, GATT, CLASSIC, DONE }
}

/**
 * Owns every signal source and decides which run when. The BLE scan runs whenever the app is in
 * the foreground; GATT and classic inquiry only ever run for one device at a time — the finder's
 * target or the device under test — and classic inquiry pauses the other two while it runs.
 */
@SuppressLint("MissingPermission")
class BluetoothRepository(
    private val context: Context,
    private val tracker: SignalTracker,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private fun adapter() = manager?.adapter

    val known = KnownDevices(context, manager)
    private var knownAddresses: Set<String> = emptySet()

    private val scanner = LeScanner(::adapter, ::onScanResult)
    val gatt = GattRssiPoller(context, tracker::onSample)
    val classic = ClassicInquiry(context, ::adapter) { address, rssi, time ->
        if (address in knownAddresses) tracker.onSample(RssiSample(address, rssi, null, Source.CLASSIC, time))
    }

    val scanStatus: StateFlow<ScanStatus> = scanner.status

    private val _unmatched = MutableStateFlow<Map<String, UnmatchedAdvert>>(emptyMap())
    val unmatched: StateFlow<Map<String, UnmatchedAdvert>> = _unmatched.asStateFlow()

    private val _test = MutableStateFlow<SourceTest?>(null)
    val test: StateFlow<SourceTest?> = _test.asStateFlow()

    private var started = false
    private var target: String? = null
    private var targetJob: Job? = null
    private var testJob: Job? = null

    init {
        scope.launch { known.devices.collect { list -> knownAddresses = list.mapTo(HashSet()) { it.address } } }
        scope.launch {
            known.adapterOn.collect { on ->
                if (!started) return@collect
                if (on) {
                    if (!classic.running.value) scanner.start()
                } else {
                    scanner.stop()
                    gatt.stop()
                    classic.stop()
                }
            }
        }
    }

    /** Called when the activity becomes visible. Nothing runs in the background. */
    fun start() {
        if (started || !Permissions.hasBluetooth(context)) return
        started = true
        known.start()
        scanner.start()
        target?.let { setTarget(it) }
    }

    fun stop() {
        if (!started) return
        started = false
        targetJob?.cancel()
        testJob?.cancel()
        _test.value = null
        scanner.stop()
        gatt.stop()
        classic.stop()
        known.stop()
    }

    fun refresh() {
        known.refresh()
        if (started && !classic.running.value) scanner.restart()
    }

    /**
     * The finder's device. LE and dual-mode devices that are not heard advertising within 3 s get
     * a GATT connection as a second source.
     */
    fun setTarget(address: String?) {
        target = address
        targetJob?.cancel()
        stopClassicSearch()
        gatt.stop()
        if (address == null || !started) return
        targetJob = scope.launch {
            delay(FALLBACK_AFTER_MS)
            while (true) {
                val device = known.devices.value.firstOrNull { it.address == address }
                val lastAdv = tracker.states.value[address]?.lastSeenBySource?.get(Source.ADV)
                val advQuiet = lastAdv == null || SystemClock.elapsedRealtimeNanos() - lastAdv > FALLBACK_AFTER_MS * 1_000_000
                val gattCapable = device == null || device.type != LinkType.CLASSIC
                if (advQuiet && gattCapable && !classic.running.value && gatt.target != address) {
                    known.device(address)?.let { gatt.start(it) }
                }
                delay(2000)
            }
        }
    }

    /** Classic inquiry for the finder's target. Pauses BLE scanning and GATT until stopped. */
    fun startClassicSearch() {
        if (!started) return
        scanner.stop()
        gatt.stop()
        classic.start()
    }

    fun stopClassicSearch() {
        if (!classic.running.value) return
        classic.stop()
        if (started) scanner.start()
    }

    /** Diagnostics: 10 s of BLE, 10 s of GATT, then one classic inquiry cycle, for one device. */
    fun testSources(address: String) {
        if (!started || testJob?.isActive == true) return
        val savedTarget = target
        targetJob?.cancel()
        testJob = scope.launch {
            try {
                _test.value = SourceTest(address, SourceTest.Step.BLE)
                delay(10_000)
                _test.value = SourceTest(address, SourceTest.Step.GATT)
                known.device(address)?.let { gatt.start(it) }
                delay(10_000)
                gatt.stop()
                _test.value = SourceTest(address, SourceTest.Step.CLASSIC)
                startClassicSearch()
                val startCycles = classic.cycles.value
                val deadline = SystemClock.elapsedRealtime() + 16_000
                while (classic.cycles.value == startCycles && SystemClock.elapsedRealtime() < deadline) delay(250)
                stopClassicSearch()
                _test.value = SourceTest(address, SourceTest.Step.DONE)
            } finally {
                gatt.stop()
                stopClassicSearch()
                if (_test.value?.step != SourceTest.Step.DONE) _test.value = null
                if (savedTarget != null && started) setTarget(savedTarget)
            }
        }
    }

    private fun onScanResult(result: ScanResult) {
        val address = result.device.address
        if (address in knownAddresses) {
            val tx = result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
                ?: result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
            tracker.onSample(RssiSample(address, result.rssi, tx, Source.ADV, result.timestampNanos))
            return
        }
        val name = result.scanRecord?.deviceName ?: return
        val current = _unmatched.value
        if (address !in current && current.size >= MAX_UNMATCHED) return
        _unmatched.value = current + (address to UnmatchedAdvert(address, name, result.rssi, result.timestampNanos))
    }

    private companion object {
        const val FALLBACK_AFTER_MS = 3000L
        const val MAX_UNMATCHED = 60
    }
}
