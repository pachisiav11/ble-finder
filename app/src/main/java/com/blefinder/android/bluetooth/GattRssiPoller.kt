package com.blefinder.android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

enum class GattState { IDLE, CONNECTING, CONNECTED, RETRYING }

/**
 * Reads RSSI over a GATT connection for one device at a time. Works for LE devices that are
 * connectable but rarely advertise, and for devices the phone is already connected to (the app
 * shares the existing link, and disconnecting only drops the app's client, not the system's).
 */
@SuppressLint("MissingPermission")
class GattRssiPoller(private val context: Context, private val onSample: (RssiSample) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var attempts = 0

    private val _state = MutableStateFlow(GattState.IDLE)
    val state: StateFlow<GattState> = _state.asStateFlow()

    /** Last non-zero status from a dropped connection to the current target, for Diagnostics (133 is the usual one). */
    var lastError: Int = 0
        private set

    val target: String? get() = device?.address

    private val poll = object : Runnable {
        override fun run() {
            gatt?.readRemoteRssi()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val retry = Runnable { connect() }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (g !== gatt) {
                    g.close()
                    return@post
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    attempts = 0
                    _state.value = GattState.CONNECTED
                    handler.removeCallbacks(poll)
                    handler.post(poll)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.removeCallbacks(poll)
                    if (status != BluetoothGatt.GATT_SUCCESS) lastError = status
                    g.close()
                    gatt = null
                    scheduleRetry()
                }
            }
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            val address = g.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onSample(RssiSample(address, rssi, null, Source.GATT, SystemClock.elapsedRealtimeNanos()))
            }
        }
    }

    fun start(target: BluetoothDevice) {
        if (device?.address == target.address) return
        stop()
        device = target
        attempts = 0
        lastError = 0
        connect()
    }

    fun stop() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(retry)
        device = null
        gatt?.let {
            runCatching { it.disconnect() }
            it.close()
        }
        gatt = null
        _state.value = GattState.IDLE
    }

    private fun connect() {
        val d = device ?: return
        _state.value = GattState.CONNECTING
        gatt = runCatching { d.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        if (gatt == null) scheduleRetry()
    }

    private fun scheduleRetry() {
        if (device == null) return
        attempts++
        _state.value = GattState.RETRYING
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, min(MAX_BACKOFF_MS, 1000L shl min(attempts - 1, 3)))
    }

    private companion object {
        const val POLL_MS = 500L
        const val MAX_BACKOFF_MS = 8000L
    }
}
