package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.pathfinding.AbstractTeleportPathfinder
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.context.TransmissionContext
import quoi.api.pathfinding.util.Raycasts
import quoi.api.pathfinding.util.generateRaycasts
import quoi.api.world.Direction
import quoi.utils.ChatUtils.modMessage
import quoi.utils.blockPos
import quoi.utils.distanceTo
import quoi.utils.skyblock.item.TeleportUtils.getTransmissionDirection
import quoi.utils.skyblock.item.TeleportUtils.predictTransmission

/**
 * A* pathfinder using transmission ability (aotv, aote).
 * assumes the player is not sneakign
 */
object TransmissionPathfinder : AbstractTeleportPathfinder<TransmissionContext>() {

    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    /**
     * Finds a path of teleports from [from] to [to]
     * @param ground if true prioritises teleports on ground
     * @param withLast if true includes the goal node in the path
     */
    fun findPath(
        from: Vec3,
        to: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 12.0,
        ground: Boolean = true,
        withLast: Boolean = false,
    ): List<TeleportPathNode>? {
        val raycasts = getRaycasts(config.pitchStep, config.yawStep)
        val actualGoal = to.above()
        val ctx = TransmissionContext(actualGoal, dist, config.hWeight, raycasts, config.timeout, ground)
        val startPos = from.blockPos

        ctx.addNode(TeleportPathNode(from.x, from.y, from.z, startPos, 0.0, startPos.distanceTo(actualGoal) / dist, null, 0f, 0f))

        val path = find(ctx, config.threads)

        return if (path != null) {
            val smoothed = smoothPath(path, dist, withLast)
            val size = if (withLast) path.size else path.size - 1
            if (config.feedback) modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). $size || ${smoothed.size}")
            smoothed
        } else {
            if (config.feedback) modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    override fun getDirection(from: Vec3, to: BlockPos, dist: Double): Direction? =
        getTransmissionDirection(from, to, dist)

    override fun getHit(ctx: TransmissionContext, eyeX: Double, eyeY: Double, eyeZ: Double, dx: Double, dy: Double, dz: Double): BlockPos? {
        val result = predictTransmission(eyeX, eyeY, eyeZ, dx, dy, dz, ctx.dist)

        return if (result.succeeded) result.pos else null
    }

    private fun getRaycasts(pitchStep: Float, yawStep: Float): Raycasts {
        if (pitchStep == lastPitchStep && yawStep == lastYawStep) {
            cachedRaycasts?.let { return it }
        }

        val raycasts = generateRaycasts(pitchStep, yawStep)

        lastPitchStep = pitchStep
        lastYawStep = yawStep
        cachedRaycasts = raycasts

        return raycasts
    }
}