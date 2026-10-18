package com.blefinder.android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repeated classic inquiry (about 12 s per cycle). Only devices in discoverable mode answer, and
 * inquiry slows BLE scanning and GATT, so the repository pauses those while this runs.
 */
@SuppressLint("MissingPermission")
class ClassicInquiry(
    private val context: Context,
    private val adapter: () -> BluetoothAdapter?,
    private val onFound: (address: String, rssi: Int, timeNanos: Long) -> Unit,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _cycles = MutableStateFlow(0)
    val cycles: StateFlow<Int> = _cycles.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    if (device != null && rssi != Short.MIN_VALUE) {
                        onFound(device.address, rssi.toInt(), SystemClock.elapsedRealtimeNanos())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _cycles.value++
                    if (_running.value) runCatching { adapter()?.startDiscovery() }
                }
            }
        }
    }

    fun start() {
        if (_running.value) return
        val a = adapter()?.takeIf { it.isEnabled } ?: return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // Exported for the same reason as in KnownDevices: protected broadcasts from the Bluetooth stack.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        _running.value = true
        _cycles.value = 0
        runCatching {
            a.cancelDiscovery()
            a.startDiscovery()
        }
    }

    fun stop() {
        if (!_running.value) return
        _running.value = false
        runCatching { adapter()?.cancelDiscovery() }
        context.unregisterReceiver(receiver)
    }
}
