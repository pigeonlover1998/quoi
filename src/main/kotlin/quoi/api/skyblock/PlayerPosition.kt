package quoi.api.skyblock

import quoi.utils.lerp
import quoi.utils.lerpAngle

class PlayerPosition {
    val raw = PosData()

    private var last = PosData()
    private var curr = PosData()
    private var lastTime: Double? = null
    private var currTime: Double? = null

    fun updatePosition(realX: Double, realZ: Double, yaw: Float, iconX: Double, iconZ: Double) {
        val now = System.nanoTime() * 1e-6

        last = curr.copy()
        lastTime = currTime

        curr.x = realX
        curr.z = realZ
        curr.yaw = yaw.toDouble()

        curr.iconX = iconX
        curr.iconZ = iconZ
        currTime = now

        raw.x = realX
        raw.z = realZ
        raw.yaw = yaw.toDouble()

        raw.iconX = iconX
        raw.iconZ = iconZ
    }

    fun getLerped(): PosData? {
        val lt = lastTime ?: return null
        val ct = currTime ?: return null

        val now = System.nanoTime() * 1e-6
        val f = ((now - ct) / (ct - lt)).coerceIn(0.0, 1.0)

        return PosData(
            x = f.lerp(last.x!!, curr.x!!),
            z = f.lerp(last.z!!, curr.z!!),
            iconX = f.lerp(last.iconX!!, curr.iconX!!),
            iconZ = f.lerp(last.iconZ!!, curr.iconZ!!),
            yaw = f.lerpAngle(last.yaw!!, curr.yaw!!)
        )
    }

    data class PosData(
        var x: Double? = null,
        var z: Double? = null,
        var iconX: Double? = null,
        var iconZ: Double? = null,
        var yaw: Double? = null
    )
}