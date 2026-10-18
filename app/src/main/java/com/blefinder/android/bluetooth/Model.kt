package com.blefinder.android.bluetooth

enum class LinkType { CLASSIC, LE, DUAL, UNKNOWN }

enum class DeviceKind { AUDIO, CAR_AUDIO, WATCH, PHONE, COMPUTER, PERIPHERAL, OTHER }

enum class ConnectedVia { ACL, A2DP, HEADSET, LE_AUDIO, GATT }

enum class Source { ADV, GATT, CLASSIC }

data class KnownDevice(
    val address: String,
    val name: String,
    val type: LinkType,
    val kind: DeviceKind,
    val bonded: Boolean,
    val connected: Set<ConnectedVia>,
    /** The name the device reports itself, when it differs from [name] (a user-set alias). Adverts carry this one. */
    val ownName: String? = null,
) {
    /** Connected for audio over classic Bluetooth: Android exposes no RSSI for that link. */
    val classicAudioOnly: Boolean
        get() = type == LinkType.CLASSIC && (ConnectedVia.A2DP in connected || ConnectedVia.HEADSET in connected)
}

data class RssiSample(
    val address: String,
    val rssi: Int,
    val txPower: Int?,
    val source: Source,
    val timeNanos: Long,
)

/** What one Diagnostics "Test all sources" run got from each source (sample counts gained in each step). */
data class SourceTestResult(val adv: Int, val gatt: Int, val gattConnected: Boolean, val gattError: Int, val classic: Int)

/** A BLE advert with a name that did not match any known address. Used by Diagnostics only. */
data class UnmatchedAdvert(val address: String, val name: String, val rssi: Int, val timeNanos: Long)

object DeviceClassification {
    // BluetoothClass.Device.Major / Device constants, inlined so this stays testable on the JVM.
    private const val MAJOR_COMPUTER = 0x0100
    private const val MAJOR_PHONE = 0x0200
    private const val MAJOR_AUDIO_VIDEO = 0x0400
    private const val MAJOR_PERIPHERAL = 0x0500
    private const val MAJOR_WEARABLE = 0x0700
    private const val AUDIO_VIDEO_CAR_AUDIO = 0x0420

    fun kind(majorClass: Int?, deviceClass: Int?): DeviceKind = when (majorClass) {
        MAJOR_AUDIO_VIDEO -> if (deviceClass == AUDIO_VIDEO_CAR_AUDIO) DeviceKind.CAR_AUDIO else DeviceKind.AUDIO
        MAJOR_WEARABLE -> DeviceKind.WATCH
        MAJOR_PHONE -> DeviceKind.PHONE
        MAJOR_COMPUTER -> DeviceKind.COMPUTER
        MAJOR_PERIPHERAL -> DeviceKind.PERIPHERAL
        else -> DeviceKind.OTHER
    }

    // BluetoothDevice.DEVICE_TYPE_* values.
    fun linkType(deviceType: Int): LinkType = when (deviceType) {
        1 -> LinkType.CLASSIC
        2 -> LinkType.LE
        3 -> LinkType.DUAL
        else -> LinkType.UNKNOWN
    }
}
