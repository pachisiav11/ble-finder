# BLE Finder — Implementation Plan

## What this is

A personal, sideloaded Android app for finding your own Bluetooth devices when they go
missing: earbuds, watches, tags, a second phone. It lists the devices Android already
knows (paired or currently connected), shows how strong each one's signal is, and has a
finder screen that turns the signal into a hot/cold gauge, vibration pulses and a 2D map
of signal strength painted as you walk around.

Delivered as an APK that the user copies to the phone and installs by hand. No account,
no cloud, no Play Store.

## Decisions (from the clarify round, 2026-10-18)

| Topic | Decision |
|---|---|
| Platform | Android only, Kotlin + Jetpack Compose, APK sideload |
| Which devices | Only devices Android knows: paired (bonded) or currently connected. Their addresses are known, so matching is by address. No general scanner for strangers' devices. |
| v1 features | Live device list, saved devices (pin + rename), finder screen, 2D signal map |
| Finder feedback | Visual gauge, vibration, 2D map |
| Map positioning | Pedestrian dead reckoning: step detector + phone heading. No GPS, no camera. |
| Map output | Colour only — red cells strong, blue cells weak. No "probably here" marker. |
| Testing | User installs each APK by hand and reports back. The app has a Diagnostics screen with a "Copy report" button so results can be pasted into chat. |

## UI decisions (from /ui-prefs, 2026-10-18)

Chosen: **1A 2A 3C 4A 5A 6A 7B**.

| # | Area | Choice |
|---|---|---|
| 1A | Visual style | Material You: dynamic colour from the wallpaper on Android 12+, a fixed Material 3 fallback palette on Android 10–11, light/dark follows the system. The signal ramp (blue → cyan → green → yellow → red) is fixed and never themed. |
| 2A | Device row | Device-type icon, name, `~N m · source · Xs ago` subtitle; phone-style signal bars coloured by strength and the dBm number on the right. |
| 3C | Saved devices | Filter chips above one list: **All**, **Saved only**, **Heard now**. Pinned devices show a pin and sort first. |
| 4A | Navigation | ⋮ overflow menu in the top bar: Diagnostics, Settings, Refresh. |
| 5A | Finder layout | Arc gauge on top, map filling the rest, hint text at the bottom. Top bar: back, device name, vibration toggle, Reset map. |
| 6A | Gauge | Half-circle arc that fills and changes colour, dBm and `~N m` in the middle, source and freshness below. |
| 7B | Map look | Smooth heat blur instead of hard squares; walked path drawn as a dashed line; position arrow. |

## Non-goals (decided)

- No scanning for unknown nearby devices
- No GPS, no "last seen place"
- No best-guess location marker on the map
- No beeps (vibration only)
- No background tracking, no "left behind" alerts — the app works only while it is open
- No ARCore / camera tracking
- No Play Store, no Windows port, no built-in update check (for now)
- Tags that connect only through their own vendor app and never show up in Android's
  Bluetooth settings are not covered, because they are not in the paired list

---

## How finding works

### 1. The device list

The known set is the union of:

- `BluetoothAdapter.getBondedDevices()` — every paired device, with address, name/alias,
  type (`CLASSIC`, `LE`, `DUAL`, `UNKNOWN`) and `BluetoothClass` (used for an icon:
  audio, wearable, phone, computer, other).
- Currently connected devices:
  - LE links: `BluetoothManager.getConnectedDevices(BluetoothProfile.GATT)`
  - Classic audio: profile proxies for `A2DP` and `HEADSET` (and `LE_AUDIO` on API 33+),
    `proxy.connectedDevices`
  - Kept live with `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` and
    `ACTION_BOND_STATE_CHANGED` broadcasts.

### 2. Getting a signal reading (RSSI)

A device can only be measured if it is transmitting something the phone can hear. There
are four cases, tried in this order:

| Case | Source | Notes |
|---|---|---|
| Device is advertising over BLE | `BluetoothLeScanner` scan results, matched by address against the known set | The main path. Fast (several readings per second). Works for watches, most tags, and earbuds that are out of the case and not connected. |
| LE or dual-mode device, connectable but not seen advertising | `connectGatt(TRANSPORT_LE)` then `readRemoteRssi()` every ~500 ms | Also works when the device is already connected to the phone (e.g. a watch) — the app shares the existing link. Only for the finder target, one connection at a time. |
| Classic-only device in discoverable mode | `BluetoothAdapter.startDiscovery()`, `ACTION_FOUND` with `EXTRA_RSSI` | Slow (one reading per ~12 s inquiry cycle) and only works if the device answers inquiries — many earbuds only do in pairing mode. On demand only, never at the same time as BLE scanning or GATT. |
| Classic audio device connected and in use | — | Android has no public API for the RSSI of a connected classic link. The app says so plainly instead of showing a fake number. |

Earbuds inside a closed case are usually switched off; nothing can find them that way.

**Address matching.** Paired LE devices often use rotating private addresses. Android
puts the identity keys it received during pairing into the controller's resolving list,
so scan results for a paired device should report its stable identity address. This must
be confirmed on the user's phone in Phase 1 (see Risks). If it does not hold, fall back to
matching on advertised name, manufacturer data and service UUIDs captured while the
device is connected.

### 3. Smoothing and distance

- Raw RSSI jumps by 5–10 dB between readings. Each device gets a 1D Kalman filter
  (tunable process and measurement noise). The filtered value drives everything on screen.
- A device with no reading for 5 s is shown as "signal lost"; its last value stays greyed.
- Rough distance from the log-distance path-loss model:
  `d = 10 ^ ((txPower1m − rssi) / (10 · n))`, with `txPower1m` from the advertisement when
  present (else −59 dBm) and `n = 2.5` for indoors. Shown as `~N m` and always labelled
  rough — walls and bodies change it by a lot.

### 4. The finder screen

- **Gauge:** an arc from 0–100 %, mapped from filtered RSSI between −100 dBm (cold) and
  −40 dBm (hot). Colour follows the same blue→red ramp as the map. Shows dBm, `~N m`, the
  source (BLE / GATT / classic) and seconds since the last reading.
- **Vibration:** a 30 ms pulse repeated every `T` ms, where `T` goes from 1500 ms (weak)
  down to 120 ms (strong). Toggle on the screen.
- Screen stays on (`FLAG_KEEP_SCREEN_ON`) while the finder is open.
- Hint text: hold the phone in front of you and turn slowly — your body alone can cut the
  signal by 10–20 dB.

### 5. The 2D signal map

**Position (dead reckoning).**

- Steps: `Sensor.TYPE_STEP_DETECTOR`, one event per step.
- Heading: `TYPE_GAME_ROTATION_VECTOR` (no magnetometer, so steel and wiring indoors do not
  bend it; drifts slowly, fine for a few minutes). Fall back to `TYPE_ROTATION_VECTOR` if
  the game sensor is missing. The heading is the direction of the phone's top edge when it
  is held flat, or of its back when it is held upright — take whichever axis has the
  larger horizontal component.
- On each step: `x += L · sin(h)`, `y += L · cos(h)`, with step length `L` from settings
  (default 0.7 m, range 0.4–1.0 m).
- The map is relative to where and which way you were facing when you opened it. A Reset
  button starts it again.

**Grid.**

- Cells of 0.5 m. Every filtered reading is added to the cell you are standing in; a cell
  stores sum and count and shows the mean.
- Colour ramp blue → cyan → green → yellow → red, scaled to the weakest and strongest
  cells seen this session, with a minimum span of 15 dB so noise does not look like a
  hotspot. A legend shows the dBm at each end.
- Cells with few readings are drawn more transparent.
- Smooth look (UI choice 7B): the grid is written into a small bitmap, one pixel per cell
  (alpha from the sample count), and drawn scaled up with bilinear filtering, which blends
  neighbouring cells into a soft heat map on every API level (no `RenderEffect` needed).
- Drawn on a Compose `Canvas`, auto-fitting the visited area, with your path as a dashed
  line and your current position as an arrow showing heading.
- Session only — cleared when you leave the finder.

If the phone has no step detector, the map area says so and the gauge and vibration still
work.

### 6. Saved devices

- Pin a device so it stays at the top of the list; give it your own name.
- Stored in DataStore Preferences: a set of pinned addresses and one string key per
  custom name.
- List order: pinned first, then by filtered RSSI (strongest first), then devices not
  currently heard.
- Filter chips (UI choice 3C): All, Saved only, Heard now.

### 7. Diagnostics

Exists because the user tests on their own phone without adb.

- Phone model, Android version, Bluetooth on/off, location services (Android 10–11),
  each permission's state, which sensors exist.
- For every known device: name, address, type, bond state, connected profiles, which
  sources have produced readings, last RSSI, sample count.
- Scan results whose advertised name matches a known device but whose address does not —
  the tell-tale sign of the address-matching problem.
- A per-device "Test all sources" button (BLE scan 10 s, GATT read, classic inquiry).
- **Copy report** puts all of the above on the clipboard as plain text.

---

## Permissions

| Android | Permissions |
|---|---|
| 12+ (API 31+) | `BLUETOOTH_SCAN` with `usesPermissionFlags="neverForLocation"`, `BLUETOOTH_CONNECT` |
| 10–11 (API 29–30) | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` (all `maxSdkVersion="30"`); location services must be switched on or scans return nothing |
| All | `ACTIVITY_RECOGNITION` (runtime, for the step detector), `VIBRATE` |

`<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>`;
the step detector is declared `required="false"`.

---

## Architecture overview

```
MainActivity (single activity, Compose navigation)
  ├─ PermissionScreen
  ├─ DeviceListScreen ──── tap ──→ FinderScreen (gauge + vibration + map)
  └─ DiagnosticsScreen

BluetoothRepository  (one instance, lives with the app process)
  ├─ KnownDevices      bonded + connected, kept live by broadcasts
  ├─ LeScanner         one continuous scan while the app is in the foreground
  ├─ GattRssiPoller    finder target only, LE / dual devices
  └─ ClassicInquiry    finder target only, classic devices, on demand
        └─ Flow<RssiSample>

SignalTracker   per-address Kalman filter, last seen, sample count
                → StateFlow<Map<address, SignalState>>
MotionTracker   step detector + heading → StateFlow<Pose>
HeatmapGrid     (Pose, filtered RSSI) → cells
SavedDevices    DataStore: pins + custom names
```

The scanner starts on the activity's `ON_START` and stops on `ON_STOP`. Nothing runs in
the background.

## Data model

```
KnownDevice   address, name, type (CLASSIC | LE | DUAL | UNKNOWN), majorClass,
              bonded, connectedProfiles
RssiSample    address, rssi, txPower?, source (ADV | GATT | CLASSIC), timeNanos
SignalState   address, filteredRssi, lastRssi, lastSeenNanos, samples, lastSource
Pose          x, y (metres from start), headingRad, steps
Cell          ix, iy, sum, count
```

---

## Tech stack

Versions copied from `D:\Projects\clipvault-android` so Gradle and the Android plugin are
already in the local cache.

| Need | Choice |
|---|---|
| Language | Kotlin 2.2.21 |
| Build | Gradle 8.14.3 wrapper, AGP 8.13.2, version catalog |
| UI | Jetpack Compose (BOM 2026.04.01), Material 3, Navigation Compose |
| Storage | DataStore Preferences |
| Tests | JUnit 4 + Truth for the pure-Kotlin logic |
| Min SDK | 29 (Android 10) |
| Target / compile SDK | 35 / 36 |
| JDK | 17 bytecode, built with the installed JDK 21 |
| Package | `com.blefinder.android` |

---

## Phases

Riskiest part first: whether the user's real devices can be heard at all.

### Phase 0 — Scaffold

**Goal:** an empty app that builds and asks for permissions.

1. Gradle wrapper, `settings.gradle.kts`, root and app `build.gradle.kts`,
   `gradle/libs.versions.toml` (trimmed ClipVault catalog), `gradle.properties`.
2. Manifest with the permission table above.
3. `MainActivity` + theme + `PermissionScreen` that requests the right set for the
   running Android version and explains each one.
4. `.gitignore`, `git init`, README stub.

**Deliverable:** `./gradlew :app:assembleDebug` passes; the emulator shows the
permission screen.

---

### Phase 1 — Known devices, signal sources, diagnostics

**Goal:** find out which of the user's devices produce readings, and through which source.

1. `KnownDevices` (bonded + connected + broadcasts).
2. `LeScanner` (low-latency, all matches, legacy + extended advertising, matched in
   software against the known set).
3. `GattRssiPoller` and `ClassicInquiry`.
4. `SignalTracker` with the Kalman filter.
5. `DiagnosticsScreen` with "Test all sources" and "Copy report".

**Deliverable:** APK v0.1.0. The user installs it, runs "Test all sources" on each
device, and pastes the report. Decide from the report whether the address-matching
fallback is needed.

---

### Phase 2 — Device list + saved devices

**Goal:** the main screen is useful on its own.

1. `DeviceListScreen`: icon, name, `~N m`, filtered dBm, source, "Xs ago" / "signal lost",
   "connected" badge.
2. Pin and rename (long-press menu), `SavedDevices` in DataStore.
3. Ordering as described above. Empty state when Bluetooth is off or nothing is paired.

**Deliverable:** APK v0.2.0 — open the app and see your devices with live signal.

---

### Phase 3 — Finder: gauge + vibration

**Goal:** walk towards a device and feel it get warmer.

1. `FinderScreen` with the gauge, dBm, distance, source and freshness.
2. Source fallback for the target: BLE adverts → GATT polling after 3 s of silence →
   classic inquiry button for classic-only devices; the "connected classic, no reading"
   message.
3. Vibration loop with toggle; keep-screen-on.

**Deliverable:** APK v0.3.0 — find a device across a room using only the gauge and
vibration.

---

### Phase 4 — 2D signal map

**Goal:** a colour map of where the signal was strong.

1. `MotionTracker` (step detector, heading axis choice, step length setting).
2. `HeatmapGrid` + `ColorRamp`.
3. `HeatmapCanvas` (auto-fit, path, position arrow, legend) below the gauge; Reset button.

**Deliverable:** APK v0.4.0 — walk a loop around a room and see a red patch near the
device.

---

### Phase 5 — Polish

1. Settings: step length, vibration on/off, gauge range.
2. App icon, edge-to-edge insets, dark theme check.
3. README with install steps and the known limits.
4. Release build.

**Deliverable:** APK v1.0.0.

---

## Build, deliver, test

- Release build: `./gradlew :app:assembleRelease`, signed with the debug key (same as
  ClipVault) so each new APK installs over the previous one. The APK is copied to
  `dist/ble-finder-<version>.apk` (git-ignored) and sent to the user.
- `versionCode` goes up with every APK handed over.
- Unit tests cover the pure logic: Kalman filter, distance, colour ramp, grid binning,
  dead-reckoning step, heading axis choice.
- The emulator (`Pixel_Fold_API_35`) is for UI and permission flow only — it cannot hear
  the user's devices. Start it as a background task, not `nohup … &`.
- Real-device testing is done by the user, using Diagnostics → Copy report.

## File / folder structure (target)

```
app/src/main/java/com/blefinder/android/
  MainActivity.kt
  bluetooth/
    BluetoothRepository.kt
    KnownDevices.kt
    LeScanner.kt
    GattRssiPoller.kt
    ClassicInquiry.kt
  signal/
    KalmanFilter.kt
    SignalTracker.kt
    Distance.kt
  motion/
    MotionTracker.kt
    Heading.kt
  map/
    HeatmapGrid.kt
    ColorRamp.kt
  data/
    SavedDevices.kt
  ui/
    PermissionScreen.kt
    DeviceListScreen.kt
    FinderScreen.kt
    DiagnosticsScreen.kt
    components/
      Gauge.kt
      HeatmapCanvas.kt
    theme/
app/src/test/java/com/blefinder/android/
  signal/  motion/  map/
```

---

## Risks and Android gotchas

- **Address matching for paired LE devices.** Expected to work through the controller's
  resolving list, but not guaranteed on every phone. Phase 1 checks it; the fallback is
  matching on name, manufacturer data and service UUIDs.
- **Connected classic earbuds give no reading.** No public API. The app says so.
- **Scan start limit.** Android allows an app about five `startScan` calls per 30 s and
  silently returns nothing after that. One scanner, owned by the repository, restarted only
  on lifecycle changes — never from recomposition.
- **Classic discovery hurts BLE.** Inquiry slows BLE scanning and GATT. Never run them
  together.
- **GATT error 133.** Always `close()` the `BluetoothGatt`, keep one connection attempt at a
  time, retry with backoff.
- **`neverForLocation` filters some beacons** out of scan results. Paired devices are not
  beacons, so this should not matter; Diagnostics will show it if it does.
- **Location services on Android 10–11.** Must be on for scan results; the permission
  screen checks it.
- **Body blocking and orientation.** RSSI drops sharply when your body is between the phone
  and the device. The hint text and the map's averaging per cell soften this.
- **Heading drift.** The game rotation vector drifts slowly; Reset fixes it.
- **Edge-to-edge on target SDK 35.** Handle window insets on every screen.

## Ideas for later

- "Play a sound" on connected earbuds, for the case where no RSSI is available
- An accelerometer step detector for phones without the step sensor
- Built-in update check (see the long-term task list)
- ARCore positioning as a more accurate map option

## Build order summary

| Phase | What | When it's done |
|---|---|---|
| 0 | Scaffold | App builds, asks for permissions |
| 1 | Signal sources + diagnostics | User's report shows which devices can be heard |
| 2 | Device list + saved devices | Live list of your devices |
| 3 | Finder gauge + vibration | Can find a device across a room |
| 4 | 2D map | Red patch appears near the device |
| 5 | Polish | v1.0.0 APK |
