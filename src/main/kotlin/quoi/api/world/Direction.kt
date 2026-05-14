package quoi.api.world

import quoi.utils.getLook

data class Direction(val yaw: Float, val pitch: Float, val distance: Double = 0.0) {
    fun look() = getLook(yaw, pitch)
}