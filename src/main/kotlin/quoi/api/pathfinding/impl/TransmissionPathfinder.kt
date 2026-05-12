package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import quoi.api.pathfinding.AbstractTeleportPathfinder
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.context.TransmissionContext
import quoi.api.pathfinding.util.Raycasts
import quoi.api.pathfinding.util.generateRaycasts
import quoi.utils.ChatUtils.modMessage
import quoi.utils.distanceTo
import quoi.utils.distanceToSqr
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.item.TeleportUtils.predictTransmission

// assuming the player is not sneaking
object TransmissionPathfinder : AbstractTeleportPathfinder<TransmissionContext>() { // todo smoothing

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
        timeout: Long = 1000L,
        ground: Boolean = true
    ): List<TeleportPathNode>? {
        val raycasts = getRaycasts(pitchStep, yawStep)
        val ctx = TransmissionContext(goal, dist, hWeight, raycasts, timeout, ground)

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

    fun findClearPath(
        start: BlockPos,
        mobs: List<LivingEntity>,
        pitchStep: Float = 15f,
        yawStep: Float = 15f,
        hWeight: Double = 1.1,
        threads: Int = 2,
        timeout: Long = 1000L
    ): List<TeleportPathNode>? { // assuming 1 tap and no fels
        if (mobs.isEmpty()) return null

        val path = mutableListOf<TeleportPathNode>()
        var currPos = start

        val remainingMobs = mobs.toMutableList()

        while (remainingMobs.isNotEmpty()) {
            val target = remainingMobs.minByOrNull { mob ->
                currPos.distanceToSqr(mob.blockPosition())
            } ?: break

            remainingMobs.remove(target)

            val goal = target.blockPosition()

            if (currPos.distanceToSqr(goal) <= 36.0) continue

            val segment = findPath(
                start = currPos,
                goal = goal,
                dist = 10.0,
                pitchStep = pitchStep,
                yawStep = yawStep,
                hWeight = hWeight,
                threads = threads,
                timeout = timeout
            ) ?: return null

            val trimmedSeg = mutableListOf<TeleportPathNode>()
            for (node in segment) {
                trimmedSeg.add(node)
                if (node.pos.distanceToSqr(goal) <= 36.0) break
            }

            if (path.isNotEmpty() && trimmedSeg.isNotEmpty()) path.addAll(trimmedSeg.drop(1))
            else path.addAll(trimmedSeg)


            if (trimmedSeg.isNotEmpty()) currPos = trimmedSeg.last().pos
        }

        return path
    }

    override fun getEyeY(ctx: TransmissionContext, pos: BlockPos): Double =
        pos.y + getEyeHeight(false).toDouble()

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