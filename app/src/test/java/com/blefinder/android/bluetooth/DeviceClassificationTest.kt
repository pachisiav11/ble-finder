package com.blefinder.android.bluetooth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceClassificationTest {
    @Test
    fun mapsMajorClasses() {
        assertThat(DeviceClassification.kind(0x0400, 0x0418)).isEqualTo(DeviceKind.AUDIO)
        assertThat(DeviceClassification.kind(0x0400, 0x0420)).isEqualTo(DeviceKind.CAR_AUDIO)
        assertThat(DeviceClassification.kind(0x0700, 0x0704)).isEqualTo(DeviceKind.WATCH)
        assertThat(DeviceClassification.kind(0x0200, 0x020C)).isEqualTo(DeviceKind.PHONE)
        assertThat(DeviceClassification.kind(0x0100, 0x010C)).isEqualTo(DeviceKind.COMPUTER)
        assertThat(DeviceClassification.kind(0x0500, 0x0540)).isEqualTo(DeviceKind.PERIPHERAL)
        assertThat(DeviceClassification.kind(0x1F00, 0x1F00)).isEqualTo(DeviceKind.OTHER)
        assertThat(DeviceClassification.kind(null, null)).isEqualTo(DeviceKind.OTHER)
    }

    @Test
    fun mapsLinkTypes() {
        assertThat(DeviceClassification.linkType(1)).isEqualTo(LinkType.CLASSIC)
        assertThat(DeviceClassification.linkType(2)).isEqualTo(LinkType.LE)
        assertThat(DeviceClassification.linkType(3)).isEqualTo(LinkType.DUAL)
        assertThat(DeviceClassification.linkType(0)).isEqualTo(LinkType.UNKNOWN)
    }

    @Test
    fun classicAudioOnlyNeedsAClassicLinkAndAnAudioProfile() {
        val base = KnownDevice("AA:BB:CC:DD:EE:FF", "Buds", LinkType.CLASSIC, DeviceKind.AUDIO, true, setOf(ConnectedVia.A2DP))
        assertThat(base.classicAudioOnly).isTrue()
        assertThat(base.copy(type = LinkType.DUAL).classicAudioOnly).isFalse()
        assertThat(base.copy(connected = setOf(ConnectedVia.ACL)).classicAudioOnly).isFalse()
    }
}
