package quoi.utils.skyblock.player.simulation

import net.minecraft.world.phys.Vec3

/**
 * from LiquidBounce (GPL-3.0)
 * copyright (c) 2015-2026 CCBlueX
 * original: https://github.com/CCBlueX/LiquidBounce/blob/0f34808bf6954ff6126dde353ff9e896eb4a2ead/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L183
 */
data class SimulatedSnapshot(
    val pos: Vec3,
    val fallDistance: Double,
    val velocity: Vec3,
    val onGround: Boolean
) {
    constructor(s: SimulatedPlayer) : this(
        s.pos,
        s.fallDistance,
        s.deltaMovement,
        s.onGround
    )
}