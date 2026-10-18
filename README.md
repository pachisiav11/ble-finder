# BLE Finder

An Android app for finding your own Bluetooth devices — earbuds, a watch, a tag, a second
phone — by their signal strength. It lists the devices your phone already knows (paired or
connected) and turns their signal into a hot/cold gauge, vibration pulses and a 2D map of
where the signal was strong as you walk around.

No account, no cloud, no Play Store. Nothing leaves the phone.

## What it does

- **Lists your known devices.** Everything paired in Android's Bluetooth settings, plus
  anything currently connected, with an icon per device type, signal bars, dBm, a rough
  distance, the source of the reading and how long ago it was heard. Filter chips switch
  between **All**, **Saved only** and **Heard now**; long-press a device to pin it or give it
  your own name.
- **Finds one of them.** Tap a device to open the finder:
  - a half-circle gauge that fills and turns from blue to red as you get closer;
  - vibration pulses that speed up from 1.5 s to 120 ms apart;
  - a **2D signal map** painted as you walk: the step sensor and the phone's heading track your
    path, every reading is dropped into the 0.5 m square you are standing on, and the result is
    drawn as a smooth heat map — red where the signal was strong, blue where it was weak.
- **Uses whatever signal it can get**, in this order:
  1. BLE advertisements (fast, several readings a second);
  2. a GATT connection reading RSSI every 500 ms, for LE devices that are connectable but quiet
     (started automatically after 3 s of silence);
  3. classic Bluetooth inquiry (slow, about one reading every 12 s), on request, for classic-only
     devices in discoverable mode.
- **Explains itself.** Diagnostics shows what the phone supports, which sources produced
  readings for each device, and whether a known device's name is being heard from a different
  address. **Copy report** puts all of it on the clipboard as plain text.

## Limits worth knowing

- **Earbuds in a closed case** are usually switched off. Nothing can hear them.
- **Earbuds connected and playing audio over classic Bluetooth** give no reading: Android has no
  public API for the signal strength of that link. The finder says so. Disconnecting them in
  Bluetooth settings often makes them start advertising again.
- **Only devices Android knows.** Tags that connect only through their own vendor app and never
  show up in Android's Bluetooth settings are not listed.
- **Distance is rough.** Walls, pockets and your own body change the signal by 10–20 dB. Hold the
  phone in front of you and turn slowly; trust the trend more than the number.
- **The map drifts.** Step counting and heading drift a little over a few minutes. Reset the map
  when it stops matching the room. Set your step length in Settings for a correct scale.
- **The app only works while it is open.** There is no background scanning.

## Install

Requires Android 10 (API 29) or newer.

Download the APK from the [releases page](https://github.com/pachisiav11/ble-finder/releases), or
build it yourself (below). Copy it to the phone and open it to install; Android will ask you to
allow installs from that source once.

The release APK is signed with a debug key so that each new version installs over the last one.
That is fine for sideloading onto your own phone; it is not meant for distribution through a
store.

### First run

1. Allow **Nearby devices** (Android 12+) or **Location** (Android 10–11 — the app does not use
   your location; Android files Bluetooth scanning under it on those versions).
2. Allow **Physical activity** if you want the map. Without it, the gauge and vibration still
   work.
3. Your paired devices appear. Tap one to find it.

### If a device never shows a signal

Open **⋮ → Diagnostics**, tap **Test all sources** on that device (about 35 s), then **Copy
report** and share the text in an issue. It shows which sources worked and whether the device
is being heard under another address.

## How it works

```
KnownDevices      bonded + connected devices, kept live by Bluetooth broadcasts
LeScanner         one continuous low-latency scan, matched by address in software
GattRssiPoller    readRemoteRssi() for the device being looked at
ClassicInquiry    repeated discovery, on request; pauses the other two
SignalTracker     per-device Kalman filter (time-aware, so fast and slow sources both work)
MotionTracker     step detector + game rotation vector → position relative to the start
HeatmapGrid       0.5 m cells, mean filtered RSSI per cell → smooth heat raster
```

The design, decisions and phase plan are in [PLAN.md](PLAN.md).

## Build

Requires the Android SDK (platform 36) and JDK 17 or newer.

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## Licence

MIT — see [LICENSE](LICENSE).
