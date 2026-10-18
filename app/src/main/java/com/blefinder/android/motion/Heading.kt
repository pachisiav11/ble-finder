package com.blefinder.android.motion

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Pose(val x: Double = 0.0, val y: Double = 0.0, val heading: Double = 0.0, val steps: Int = 0) {
    /** One step of [length] metres along [heading] (radians, 0 = forward/up, clockwise positive). */
    fun step(length: Double): Pose = copy(x = x + length * sin(heading), y = y + length * cos(heading), steps = steps + 1)
}

object Heading {
    /**
     * Walking direction from a row-major 3x3 rotation matrix (device → world, world = east, north,
     * up). Uses the phone's top edge when it is held flat and its back when it is held upright —
     * whichever axis lies closer to horizontal — so both grips give a usable heading. Returns null
     * when neither axis has a horizontal component worth trusting.
     */
    fun fromRotationMatrix(r: FloatArray): Double? {
        val topEast = r[1].toDouble()
        val topNorth = r[4].toDouble()
        val backEast = -r[2].toDouble()
        val backNorth = -r[5].toDouble()
        val top = hypot(topEast, topNorth)
        val back = hypot(backEast, backNorth)
        if (maxOf(top, back) < 0.2) return null
        return if (top >= back) atan2(topEast, topNorth) else atan2(backEast, backNorth)
    }

    fun normalize(angle: Double): Double {
        var a = angle % (2 * PI)
        if (a > PI) a -= 2 * PI
        if (a < -PI) a += 2 * PI
        return a
    }

    /** Rotation about the vertical axis by [yaw] (clockwise from north), as a device-flat matrix. Test helper and reference. */
    fun flatMatrix(yaw: Double): FloatArray {
        val c = cos(yaw).toFloat()
        val s = sin(yaw).toFloat()
        // Device Y (top edge) points along (sin yaw, cos yaw) in east/north.
        return floatArrayOf(
            c, s, 0f,
            -s, c, 0f,
            0f, 0f, 1f,
        )
    }
}
