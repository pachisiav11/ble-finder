package com.blefinder.android.signal

import com.blefinder.android.bluetooth.RssiSample
import com.blefinder.android.bluetooth.Source
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class SignalState(
    val address: String,
    val filtered: Double,
    val last: Int,
    val lastSeenNanos: Long,
    val samples: Int,
    val lastSource: Source,
    val txPower: Int?,
    val countBySource: Map<Source, Int>,
    val lastSeenBySource: Map<Source, Long>,
) {
    fun ageSeconds(nowNanos: Long): Double = (nowNanos - lastSeenNanos) / 1e9

    fun isFresh(nowNanos: Long): Boolean = ageSeconds(nowNanos) <= FRESH_SECONDS

    val distanceMetres: Double
        get() = SignalMath.distanceMetres(filtered, SignalMath.rssiAt1m(txPower))

    companion object {
        const val FRESH_SECONDS = 5.0
    }
}

/** A reading after filtering; the map bins these by position. */
data class FilteredSample(val address: String, val filtered: Double, val raw: Int, val source: Source, val timeNanos: Long)

/**
 * Per-device smoothing and bookkeeping. Samples arrive from the main thread (scan, inquiry) and
 * from binder threads (GATT), so updates are serialised.
 */
class SignalTracker {
    private val filters = HashMap<String, KalmanFilter>()
    private val _states = MutableStateFlow<Map<String, SignalState>>(emptyMap())
    val states: StateFlow<Map<String, SignalState>> = _states.asStateFlow()

    private val _filtered = MutableSharedFlow<FilteredSample>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val filtered: SharedFlow<FilteredSample> = _filtered.asSharedFlow()

    @Synchronized
    fun onSample(sample: RssiSample) {
        val filter = filters.getOrPut(sample.address) { KalmanFilter() }
        val value = filter.update(sample.rssi, sample.timeNanos)
        val previous = _states.value[sample.address]
        val counts = previous?.countBySource.orEmpty().toMutableMap()
        counts[sample.source] = (counts[sample.source] ?: 0) + 1
        val seen = previous?.lastSeenBySource.orEmpty().toMutableMap()
        seen[sample.source] = sample.timeNanos
        val state = SignalState(
            address = sample.address,
            filtered = value,
            last = sample.rssi,
            lastSeenNanos = sample.timeNanos,
            samples = (previous?.samples ?: 0) + 1,
            lastSource = sample.source,
            txPower = sample.txPower ?: previous?.txPower,
            countBySource = counts,
            lastSeenBySource = seen,
        )
        _states.value = _states.value + (sample.address to state)
        _filtered.tryEmit(FilteredSample(sample.address, value, sample.rssi, sample.source, sample.timeNanos))
    }

    @Synchronized
    fun clear() {
        filters.clear()
        _states.value = emptyMap()
    }
}
