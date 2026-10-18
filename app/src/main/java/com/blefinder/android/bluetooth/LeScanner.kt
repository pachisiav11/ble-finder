package com.blefinder.android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
    data object Throttled : ScanStatus
    data object BluetoothOff : ScanStatus
    data class Failed(val code: Int) : ScanStatus
}

/**
 * One continuous low-latency scan. It is unfiltered on purpose: hardware address filters can miss
 * paired devices whose controller does not resolve private addresses, so matching happens in
 * software against the known set.
 */
@SuppressLint("MissingPermission")
class LeScanner(private val adapter: () -> BluetoothAdapter?, private val onResult: (ScanResult) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val throttle = ScanThrottle()
    private var wanted = false
    private var active = false

    private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(onResult)

        override fun onScanFailed(errorCode: Int) {
            active = false
            _status.value = ScanStatus.Failed(errorCode)
        }
    }

    // Android quietly downgrades unfiltered scans that run longer than 30 minutes.
    private val refresh = Runnable { restart() }

    fun start() {
        wanted = true
        tryStart()
    }

    fun stop() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        _status.value = ScanStatus.Idle
    }

    fun restart() {
        stopScan()
        tryStart()
    }

    private fun tryStart() {
        if (!wanted || active) return
        val a = adapter()
        val scanner = a?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (a == null || scanner == null) {
            _status.value = ScanStatus.BluetoothOff
            return
        }
        val now = SystemClock.elapsedRealtimeNanos()
        val wait = throttle.waitNanos(now)
        if (wait > 0) {
            _status.value = ScanStatus.Throttled
            handler.postDelayed({ tryStart() }, wait / 1_000_000 + 50)
            return
        }
        throttle.record(now)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .apply {
                if (a.isLeExtendedAdvertisingSupported) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }
            .build()
        runCatching { scanner.startScan(null, settings, callback) }
            .onSuccess {
                active = true
                _status.value = ScanStatus.Scanning
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, 25 * 60 * 1000L)
            }
            .onFailure { _status.value = ScanStatus.Failed(-1) }
    }

    private fun stopScan() {
        if (!active) return
        active = false
        // Throws if Bluetooth was switched off in the meantime; the scan is gone either way.
        runCatching { adapter()?.bluetoothLeScanner?.stopScan(callback) }
    }
}
