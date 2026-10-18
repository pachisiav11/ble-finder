package com.blefinder.android.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blefinder.android.motion.MotionTracker
import com.blefinder.android.motion.Pose
import com.blefinder.android.signal.SignalTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

data class MapFrame(val raster: HeatRaster?, val snapshot: HeatmapSnapshot?, val path: List<Pair<Double, Double>>, val pose: Pose)

/**
 * One finder visit's map. Lives with the finder's back-stack entry, so it survives rotation and is
 * dropped when the user leaves the finder.
 */
class MapSession(
    private val address: String,
    private val tracker: SignalTracker,
    private val motion: MotionTracker,
) : ViewModel() {
    private val grid = HeatmapGrid()
    private val path = ArrayList<Pair<Double, Double>>()
    private var lastSteps = 0
    private var snapshot: HeatmapSnapshot? = null
    private var raster: HeatRaster? = null

    private val _frame = MutableStateFlow(MapFrame(null, null, listOf(0.0 to 0.0), Pose()))
    val frame: StateFlow<MapFrame> = _frame.asStateFlow()

    init {
        motion.reset()
        path += 0.0 to 0.0
        viewModelScope.launch {
            tracker.filtered.filter { it.address == address }.collect { s ->
                val p = motion.pose.value
                grid.add(p.x, p.y, s.filtered)
                snapshot = grid.snapshot()
                raster = snapshot?.let(HeatRasterizer::render)
                publish(p)
            }
        }
        viewModelScope.launch {
            motion.pose.collect { p ->
                if (p.steps != lastSteps) {
                    lastSteps = p.steps
                    path += p.x to p.y
                    if (path.size > MAX_PATH) path.removeAt(0)
                }
                publish(p)
            }
        }
    }

    fun reset() {
        grid.clear()
        snapshot = null
        raster = null
        path.clear()
        path += 0.0 to 0.0
        lastSteps = 0
        motion.reset()
        publish(motion.pose.value)
    }

    private fun publish(p: Pose) {
        _frame.value = MapFrame(raster, snapshot, path.toList(), p)
    }

    private companion object {
        const val MAX_PATH = 4000
    }
}
