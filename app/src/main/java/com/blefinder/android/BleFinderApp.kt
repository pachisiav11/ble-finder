package com.blefinder.android

import android.app.Application
import android.content.Context
import com.blefinder.android.bluetooth.BluetoothRepository
import com.blefinder.android.data.AppSettings
import com.blefinder.android.data.Saved
import com.blefinder.android.data.SavedDevices
import com.blefinder.android.data.SettingsStore
import com.blefinder.android.motion.MotionTracker
import com.blefinder.android.signal.SignalTracker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleFinderApp : Application() {
    val scope = MainScope()
    val tracker = SignalTracker()
    val bluetooth by lazy { BluetoothRepository(this, tracker, scope) }
    val savedDevices by lazy { SavedDevices(this) }
    val saved: StateFlow<Saved> by lazy { savedDevices.state.stateIn(scope, SharingStarted.Eagerly, Saved()) }
    val settingsStore by lazy { SettingsStore(this) }
    val settings: StateFlow<AppSettings> by lazy { settingsStore.state.stateIn(scope, SharingStarted.Eagerly, AppSettings()) }
    val motion by lazy {
        MotionTracker(this).also { m -> scope.launch { settings.collect { m.stepLength = it.stepLength } } }
    }
}

val Context.app: BleFinderApp get() = applicationContext as BleFinderApp
