package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.AbstractTeleportPathfinder
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.context.TeleportContext
import quoi.api.pathfinding.util.Raycasts
import quoi.api.pathfinding.util.generateRaycasts
import quoi.utils.ChatUtils.modMessage
import quoi.utils.distanceTo
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.item.TeleportUtils.predictTransmission

// assuming the player is not sneaking
object TransmissionPathfinder : AbstractTeleportPathfinder<TeleportContext>() {

    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    fun findPath(
        start: BlockPos,
        goal: BlockPos,
        dist: Double = 12.0,
        pitchStep: Float = 15f,
        yawStep: Float = 15f,
        hWeight: Double = 1.1,
        threads: Int = 2,
        timeout: Long = 1000L
    ): List<TeleportPathNode>? {
        val raycasts = getRaycasts(pitchStep, yawStep)
        val ctx = TeleportContext(goal, dist, hWeight, raycasts, timeout)

        ctx.addNode(TeleportPathNode(start, 0.0, start.distanceTo(goal) / dist, null, 0f, 0f))

        val path = find(ctx, threads)

        return if (path != null) {
            modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). ${path.size}")
            path
        } else {
            modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    override fun getEyeY(ctx: TeleportContext, pos: BlockPos): Double =
        pos.y + getEyeHeight(false).toDouble()

    override fun getHit(ctx: TeleportContext, eyeX: Double, eyeY: Double, eyeZ: Double, dx: Double, dy: Double, dz: Double): BlockPos? {
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