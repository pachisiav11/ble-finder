package com.blefinder.android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The devices this phone already knows: everything paired, plus anything currently connected.
 * Kept live by system broadcasts while started. Callers must hold the Bluetooth permissions.
 */
@SuppressLint("MissingPermission")
class KnownDevices(private val context: Context, private val manager: BluetoothManager?) {
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _devices = MutableStateFlow<List<KnownDevice>>(emptyList())
    val devices: StateFlow<List<KnownDevice>> = _devices.asStateFlow()

    private val _adapterOn = MutableStateFlow(adapter?.isEnabled == true)
    val adapterOn: StateFlow<Boolean> = _adapterOn.asStateFlow()

    private val proxies = HashMap<Int, BluetoothProfile>()
    private val aclConnected = HashSet<String>()
    private var started = false

    private val profiles = buildList {
        add(BluetoothProfile.A2DP)
        add(BluetoothProfile.HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            refresh()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) openProxies()
                    if (state == BluetoothAdapter.STATE_OFF) {
                        aclConnected.clear()
                        closeProxies()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> device(intent)?.let { aclConnected += it.address }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> device(intent)?.let { aclConnected -= it.address }
            }
            refresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        // Exported on purpose: these broadcasts come from the Bluetooth stack, which runs as its own
        // app, and every action here is a protected broadcast that other apps cannot send.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        openProxies()
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(receiver)
        closeProxies()
        aclConnected.clear()
    }

    fun device(address: String): BluetoothDevice? =
        adapter?.takeIf { BluetoothAdapter.checkBluetoothAddress(address) }?.getRemoteDevice(address)

    fun refresh() {
        val a = adapter
        _adapterOn.value = a?.isEnabled == true
        if (a == null || !a.isEnabled) {
            _devices.value = emptyList()
            return
        }
        val connected = HashMap<String, MutableSet<ConnectedVia>>()
        fun mark(address: String, via: ConnectedVia) {
            connected.getOrPut(address) { mutableSetOf() } += via
        }
        aclConnected.forEach { mark(it, ConnectedVia.ACL) }
        proxies.forEach { (profile, proxy) ->
            val via = when (profile) {
                BluetoothProfile.A2DP -> ConnectedVia.A2DP
                BluetoothProfile.HEADSET -> ConnectedVia.HEADSET
                else -> ConnectedVia.LE_AUDIO
            }
            runCatching { proxy.connectedDevices }.getOrDefault(emptyList()).forEach { mark(it.address, via) }
        }
        runCatching { manager?.getConnectedDevices(BluetoothProfile.GATT) }.getOrNull().orEmpty()
            .forEach { mark(it.address, ConnectedVia.GATT) }

        val byAddress = LinkedHashMap<String, BluetoothDevice>()
        runCatching { a.bondedDevices }.getOrNull().orEmpty().forEach { byAddress[it.address] = it }
        connected.keys.filter { it !in byAddress }.forEach { address -> device(address)?.let { byAddress[address] = it } }

        _devices.value = byAddress.values.map { d ->
            val cls = runCatching { d.bluetoothClass }.getOrNull()
            KnownDevice(
                address = d.address,
                name = displayName(d),
                type = DeviceClassification.linkType(d.type),
                kind = DeviceClassification.kind(cls?.majorDeviceClass, cls?.deviceClass),
                bonded = d.bondState == BluetoothDevice.BOND_BONDED,
                connected = connected[d.address].orEmpty(),
            )
        }
    }

    private fun displayName(d: BluetoothDevice): String {
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { d.alias }.getOrNull() else null
        return alias?.takeIf { it.isNotBlank() } ?: d.name?.takeIf { it.isNotBlank() } ?: d.address
    }

    private fun openProxies() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        profiles.filter { it !in proxies }.forEach { a.getProfileProxy(context, listener, it) }
    }

    private fun closeProxies() {
        val a = adapter
        proxies.forEach { (profile, proxy) -> runCatching { a?.closeProfileProxy(profile, proxy) } }
        proxies.clear()
    }

    private fun device(intent: Intent): BluetoothDevice? =
        IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
}
