package com.blefinder.android

import android.app.Application
import android.content.Context
import com.blefinder.android.bluetooth.BluetoothRepository
import com.blefinder.android.signal.SignalTracker
import kotlinx.coroutines.MainScope

class BleFinderApp : Application() {
    val scope = MainScope()
    val tracker = SignalTracker()
    val bluetooth by lazy { BluetoothRepository(this, tracker, scope) }
}

val Context.app: BleFinderApp get() = applicationContext as BleFinderApp
