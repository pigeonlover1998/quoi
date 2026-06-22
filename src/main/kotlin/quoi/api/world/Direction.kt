package quoi.api.world

import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.utils.getLook
import kotlin.math.roundToInt

data class Direction(val yaw: Float, val pitch: Float, val distance: Double = 0.0) {
    fun look(): Vec3 =
        getLook(yaw, pitch)

    fun normalise(previous: Direction): Direction {
        val f = mc.options.sensitivity().get() * 0.6 + 0.2
        val gcd = f * f * f * 8.0f * 0.15

        val dy = wrapDegrees(yaw - previous.yaw)
        val dp = pitch - previous.pitch

        val gy = ((dy / gcd).roundToInt() * gcd).toFloat()
        val gp = ((dp / gcd).roundToInt() * gcd).toFloat()

        return Direction(wrapDegrees(previous.yaw + gy), (previous.pitch + gp).coerceIn(-90f, 90f), distance)
    }
}