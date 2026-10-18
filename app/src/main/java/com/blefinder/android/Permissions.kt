package com.blefinder.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /** What scanning and reading paired devices needs on this Android version. */
    fun bluetooth(sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdk >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** The step detector needs this; without it only the map is lost. */
    const val MOTION = Manifest.permission.ACTIVITY_RECOGNITION

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasBluetooth(context: Context): Boolean = bluetooth().all { granted(context, it) }

    fun hasMotion(context: Context): Boolean = granted(context, MOTION)
}
