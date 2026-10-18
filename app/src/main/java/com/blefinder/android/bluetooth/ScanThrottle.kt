package com.blefinder.android.bluetooth

/**
 * Android lets an app start a BLE scan about five times per 30 s; past that, `startScan` succeeds
 * but silently delivers nothing. This keeps the app under the limit with one start to spare.
 */
class ScanThrottle(private val maxStarts: Int = 4, private val windowNanos: Long = 30_000_000_000L) {
    private val starts = ArrayDeque<Long>()

    /** Nanoseconds to wait before a start is allowed at [now]; 0 if it is allowed now. */
    fun waitNanos(now: Long): Long {
        while (starts.isNotEmpty() && now - starts.first() >= windowNanos) starts.removeFirst()
        return if (starts.size < maxStarts) 0 else windowNanos - (now - starts.first())
    }

    fun record(now: Long) {
        starts.addLast(now)
    }
}
