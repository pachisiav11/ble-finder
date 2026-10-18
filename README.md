# BLE Finder

An Android app for finding your own Bluetooth devices — earbuds, a watch, a tag, a second
phone — by their signal strength. It lists the devices your phone already knows (paired or
connected) and turns their signal into a hot/cold gauge, vibration pulses and a 2D map of
where the signal was strong as you walk around.

No account, no cloud, no Play Store. Everything stays on the phone.

Work in progress — see [PLAN.md](PLAN.md) for the design and phases.

## Build

Requires the Android SDK (platform 36) and JDK 17 or newer.

```bash
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Copy it to the phone and open it to
install. Android 10 (API 29) or newer.

## Licence

MIT — see [LICENSE](LICENSE).
