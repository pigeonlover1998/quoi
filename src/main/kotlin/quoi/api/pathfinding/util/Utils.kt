package quoi.api.pathfinding.util

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.floats.FloatArrayList
import quoi.utils.getLook
import quoi.utils.rad
import kotlin.math.cos
import kotlin.math.max

fun generateRaycasts(pitchStep: Float, yawStep: Float, scale: Double = 1.0): Raycasts {
    val dx = DoubleArrayList()
    val dy = DoubleArrayList()
    val dz = DoubleArrayList()

    val yaws = FloatArrayList()
    val pitches = FloatArrayList()

    var pitch = -90f
    while (pitch <= 90f) {
        val actualYawStep = (yawStep / max(0.01f, cos(pitch.rad)))
        var yaw = 0f
        while (yaw < 360f) {
            val vec = getLook(yaw, pitch)

            dx.add(vec.x * scale)
            dy.add(vec.y * scale)
            dz.add(vec.z * scale)

            yaws.add(yaw)
            pitches.add(pitch)

            yaw += actualYawStep
        }
        pitch += pitchStep
    }

    return Raycasts(dx.toDoubleArray(), dy.toDoubleArray(), dz.toDoubleArray(), yaws.toFloatArray(), pitches.toFloatArray(), scale)
}
