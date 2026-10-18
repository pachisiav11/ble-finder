package com.blefinder.android.signal

/**
 * One-dimensional Kalman filter for RSSI.
 *
 * Process noise grows with the time since the last reading rather than per sample, because the
 * sources arrive at very different rates: BLE adverts several times a second, classic inquiry
 * once every ~12 s. Per-sample noise would make the filter sluggish for one and jumpy for the
 * other. At 10 Hz the defaults settle to a gain of about 0.2 (roughly half a second to follow a
 * step change); after a 12 s gap the next reading is taken almost as-is.
 */
class KalmanFilter(
    private val processNoisePerSecond: Double = 9.0,
    private val measurementNoise: Double = 16.0,
    private val resetAfterSeconds: Double = 10.0,
) {
    var estimate: Double = Double.NaN
        private set
    private var variance = 0.0
    private var lastNanos = 0L

    fun update(rssi: Int, timeNanos: Long): Double {
        // Two sources can deliver slightly out of order; a reading from the past adds no process noise.
        val dt = ((timeNanos - lastNanos) / 1e9).coerceAtLeast(0.0)
        if (estimate.isNaN() || dt > resetAfterSeconds) {
            estimate = rssi.toDouble()
            variance = measurementNoise
        } else {
            val predicted = variance + processNoisePerSecond * dt
            val gain = predicted / (predicted + measurementNoise)
            estimate += gain * (rssi - estimate)
            variance = (1 - gain) * predicted
        }
        lastNanos = maxOf(lastNanos, timeNanos)
        return estimate
    }
}
