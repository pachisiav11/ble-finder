package com.blefinder.android.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.blefinder.android.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MotionAvailability { OK, NO_PERMISSION, NO_SENSOR }

/**
 * Pedestrian dead reckoning: the step detector moves the pose one step along the current
 * heading. Heading comes from the game rotation vector (no magnetometer, so steel and wiring
 * indoors do not bend it; it drifts slowly instead) and is relative to the direction faced at the
 * last reset, so "forward" starts as up on the map.
 */
class MotionTracker(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val stepSensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val headingSensor: Sensor? =
        sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _pose = MutableStateFlow(Pose())
    val pose: StateFlow<Pose> = _pose.asStateFlow()

    var stepLength: Double = DEFAULT_STEP_LENGTH

    private val matrix = FloatArray(9)
    private var absolute = Double.NaN
    private var reference = Double.NaN
    private var running = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> _pose.value = _pose.value.step(stepLength)
                else -> {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val h = Heading.fromRotationMatrix(matrix) ?: return
                    absolute = h
                    if (reference.isNaN()) reference = h
                    _pose.value = _pose.value.copy(heading = Heading.normalize(h - reference))
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    fun availability(): MotionAvailability = when {
        stepSensor == null || headingSensor == null -> MotionAvailability.NO_SENSOR
        !Permissions.hasMotion(context) -> MotionAvailability.NO_PERMISSION
        else -> MotionAvailability.OK
    }

    fun start() {
        if (running || availability() != MotionAvailability.OK) return
        running = true
        sensors?.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        sensors?.registerListener(listener, headingSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        sensors?.unregisterListener(listener)
    }

    /** Back to the origin, facing up, from the current direction. */
    fun reset() {
        reference = absolute
        _pose.value = Pose()
    }

    companion object {
        const val DEFAULT_STEP_LENGTH = 0.7
    }
}
