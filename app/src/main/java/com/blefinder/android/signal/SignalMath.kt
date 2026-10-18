package com.blefinder.android.signal

import kotlin.math.pow
import kotlin.math.roundToInt

object SignalMath {
    const val DEFAULT_RSSI_AT_1M = -59

    /** Free-space loss at 2.4 GHz over the first metre; turns an advertised TX power into RSSI at 1 m. */
    private const val LOSS_AT_1M_DB = 41

    const val INDOOR_PATH_LOSS_EXPONENT = 2.5

    /**
     * Expected RSSI at 1 m. Advertisements carry TX power at the antenna (typically -20..+10 dBm);
     * values that would put 1 m outside a plausible range are ignored.
     */
    fun rssiAt1m(advertisedTxPower: Int?): Int {
        if (advertisedTxPower == null) return DEFAULT_RSSI_AT_1M
        val at1m = advertisedTxPower - LOSS_AT_1M_DB
        return if (at1m in -80..-30) at1m else DEFAULT_RSSI_AT_1M
    }

    /** Log-distance path-loss model. Rough by nature: walls and bodies move it by a factor of two or more. */
    fun distanceMetres(rssi: Double, rssiAt1m: Int = DEFAULT_RSSI_AT_1M, exponent: Double = INDOOR_PATH_LOSS_EXPONENT): Double =
        10.0.pow((rssiAt1m - rssi) / (10 * exponent))

    fun formatDistance(metres: Double): String = when {
        metres < 0.3 -> "<0.3 m"
        metres < 3 -> "~%.1f m".format(metres)
        metres <= 30 -> "~${metres.roundToInt()} m"
        else -> ">30 m"
    }

    /** 1..5 phone-style bars. */
    fun bars(rssi: Double): Int = when {
        rssi >= -55 -> 5
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }

    /** 0..1 position of [rssi] between [cold] and [hot] dBm. */
    fun strength(rssi: Double, cold: Int = -100, hot: Int = -40): Double =
        ((rssi - cold) / (hot - cold)).coerceIn(0.0, 1.0)

    fun formatAge(seconds: Double): String = when {
        seconds < 10 -> "%.1f s ago".format(seconds)
        seconds < 90 -> "${seconds.toInt()} s ago"
        seconds < 5400 -> "${(seconds / 60).toInt()} min ago"
        else -> "${(seconds / 3600).toInt()} h ago"
    }
}
