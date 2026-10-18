package com.blefinder.android

import android.app.Application
import android.content.Context
import com.blefinder.android.bluetooth.BluetoothRepository
import com.blefinder.android.data.Saved
import com.blefinder.android.data.SavedDevices
import com.blefinder.android.motion.MotionTracker
import com.blefinder.android.signal.SignalTracker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleFinderApp : Application() {
    val scope = MainScope()
    val tracker = SignalTracker()
    val bluetooth by lazy { BluetoothRepository(this, tracker, scope) }
    val savedDevices by lazy { SavedDevices(this) }
    val motion by lazy { MotionTracker(this) }
    val saved: StateFlow<Saved> by lazy { savedDevices.state.stateIn(scope, SharingStarted.Eagerly, Saved()) }
}

val Context.app: BleFinderApp get() = applicationContext as BleFinderApp
