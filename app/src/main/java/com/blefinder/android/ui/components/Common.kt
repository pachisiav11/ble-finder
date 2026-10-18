package com.blefinder.android.ui.components

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.blefinder.android.bluetooth.DeviceKind
import com.blefinder.android.signal.ColorRamp
import kotlinx.coroutines.delay

/** Elapsed-realtime clock that ticks while composed, for "Xs ago" labels and freshness. */
@Composable
fun rememberNowNanos(periodMs: Long = 500): State<Long> = produceState(SystemClock.elapsedRealtimeNanos()) {
    while (true) {
        delay(periodMs)
        value = SystemClock.elapsedRealtimeNanos()
    }
}

fun DeviceKind.icon(): ImageVector = when (this) {
    DeviceKind.AUDIO -> Icons.Outlined.Headphones
    DeviceKind.CAR_AUDIO -> Icons.Outlined.DirectionsCar
    DeviceKind.WATCH -> Icons.Outlined.Watch
    DeviceKind.PHONE -> Icons.Outlined.Smartphone
    DeviceKind.COMPUTER -> Icons.Outlined.Computer
    DeviceKind.PERIPHERAL -> Icons.Outlined.Keyboard
    DeviceKind.OTHER -> Icons.Outlined.DevicesOther
}

fun signalColor(strength: Double): Color = Color(ColorRamp.argb(strength))

fun formatAge(seconds: Double): String = when {
    seconds < 10 -> "%.1f s ago".format(seconds)
    seconds < 90 -> "${seconds.toInt()} s ago"
    seconds < 5400 -> "${(seconds / 60).toInt()} min ago"
    else -> "${(seconds / 3600).toInt()} h ago"
}
