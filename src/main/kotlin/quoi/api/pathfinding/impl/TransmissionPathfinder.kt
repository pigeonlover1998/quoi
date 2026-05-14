package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.pathfinding.AbstractTeleportPathfinder
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.context.TransmissionContext
import quoi.api.pathfinding.util.Raycasts
import quoi.api.pathfinding.util.generateRaycasts
import quoi.utils.ChatUtils.modMessage
import quoi.utils.blockPos
import quoi.utils.distanceTo
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.item.TeleportUtils.predictTransmission

// assuming the player is not sneaking
object TransmissionPathfinder : AbstractTeleportPathfinder<TransmissionContext>() { // todo smoothing

    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    fun findPath(
        start: Vec3,
        goal: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 12.0,
        ground: Boolean = true,
        withLast: Boolean = false,
    ): List<TeleportPathNode>? {
        val raycasts = getRaycasts(config.pitchStep, config.yawStep)
        val ctx = TransmissionContext(goal, dist, config.hWeight, raycasts, config.timeout, ground)
        val startPos = start.blockPos

        ctx.addNode(TeleportPathNode(start.x, start.y, start.z, startPos, 0.0, startPos.distanceTo(goal) / dist, null, 0f, 0f))

        val path = find(ctx, config.threads)?.shift(trim = !withLast)

        return if (path != null) {
            modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). ${path.size}")
            path
        } else {
            modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    override fun getEyeY(ctx: TransmissionContext, node: TeleportPathNode): Double =
        node.y + getEyeHeight(false)

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

    private fun List<TeleportPathNode>.shift(trim: Boolean = true): List<TeleportPathNode> {
        if (this.size < 2) return if (trim) emptyList() else this

        val shifted = this.dropLast(1).mapIndexed { i, n ->
            val next = this[i + 1]
            TeleportPathNode(n.x, n.y, n.z, n.pos, n.g, n.h, n.parent, next.yaw, next.pitch)
        }

        return if (trim) shifted else shifted + this.last()
    }
}