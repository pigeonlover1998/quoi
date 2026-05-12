package quoi.api.pathfinding.impl

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import quoi.api.pathfinding.AbstractPathfinder
import quoi.api.pathfinding.TransmissionPathNode
import quoi.api.pathfinding.context.TransmissionContext
import quoi.utils.ChatUtils.modMessage
import quoi.utils.distanceTo
import quoi.utils.dot
import quoi.utils.getEyeHeight
import quoi.utils.getLook
import quoi.utils.rad
import quoi.utils.skyblock.item.TeleportUtils.predictTransmission
import quoi.utils.sq
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

// assuming the player is not sneaking
object TransmissionPathfinder : AbstractPathfinder<TransmissionPathNode, TransmissionContext>() { // todo clean up dupe code

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
    ): List<TransmissionPathNode>? {
        val raycasts = getRaycasts(pitchStep, yawStep)
        val ctx = TransmissionContext(goal, dist, hWeight, raycasts, timeout)

        ctx.addNode(TransmissionPathNode(start, 0.0, start.distanceTo(goal) / dist, null, 0f, 0f))

        val path = find(ctx, threads)

        return if (path != null) {
            modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). ${path.size}")
            path
        } else {
            modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    override fun expand(ctx: TransmissionContext, current: TransmissionPathNode) {

        current.forEachTransmissionHit(ctx) { pos, yaw, pitch ->
            val hCost = (pos.distanceTo(ctx.goal) / ctx.dist) * ctx.hWeight
            val node = TransmissionPathNode(pos, current.g + 1.0, hCost, current, yaw, pitch)
            ctx.addNode(node)
        }
    }

    private inline fun TransmissionPathNode.forEachTransmissionHit(ctx: TransmissionContext, block: (BlockPos, Float, Float) -> Unit) {
        val eyeX = pos.x + 0.5
        val eyeY = pos.y + getEyeHeight(false).toDouble()
        val eyeZ = pos.z + 0.5

        val vx = ctx.goal.x - pos.x
        val vy = ctx.goal.y - pos.y
        val vz = ctx.goal.z - pos.z.toDouble()
        val dist = sqrt(vx.sq + vy.sq + vz.sq)
        val invDist = if (dist > 0) 1.0 / dist else 0.0

        val dirX = vx * invDist
        val dirY = vy * invDist
        val dirZ = vz * invDist

        var pDirX = 0.0
        var pDirY = 0.0
        var pDirZ = 0.0

        val parent = this.parent?.pos

        if (parent != null) {
            val px = parent.x - pos.x
            val py = parent.y - pos.y
            val pz = parent.z - pos.z.toDouble()
            val pDist = sqrt(px.sq + py.sq + pz.sq)
            if (pDist > 0) {
                pDirX = px / pDist
                pDirY = py / pDist
                pDirZ = pz / pDist
            }
        }

        val hitCache = LongOpenHashSet()

        for (i in 0 until ctx.raycasts.dx.size) {
            if (ctx.solved) return

            val dx = ctx.raycasts.dx[i]
            val dy = ctx.raycasts.dy[i]
            val dz = ctx.raycasts.dz[i]

            val gDot = dot(dx, dy, dz, dirX, dirY, dirZ)

            if (gDot <= 0.5) { // if not looking towards the goal
                if (gDot > 0.0 && i % 2 != 0) continue // if sideways only cast 50% of the time
                else if (gDot <= 0.0 && i % 4 != 0) continue // if backwards only cast 25% of the time
            }

            if (parent != null) {
                val pDot = dot(dx, dy, dz, pDirX, pDirY, pDirZ) / ctx.dist
                if (pDot > 0.65) continue // if pointing too strongly towards the parent
            }

            val result = predictTransmission(eyeX, eyeY, eyeZ, dx, dy, dz, ctx.dist)

            if (result.succeeded && result.pos != null) {
                if (hitCache.add(result.pos.asLong())) {
                    block(result.pos, ctx.raycasts.yaws[i], ctx.raycasts.pitches[i])
                }
            }
        }
    }

    private fun getRaycasts(pitchStep: Float, yawStep: Float): Raycasts {
        if (pitchStep == lastPitchStep && yawStep == lastYawStep) {
            cachedRaycasts?.let { return it }
        }

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

                dx.add(vec.x)
                dy.add(vec.y)
                dz.add(vec.z)

                yaws.add(yaw)
                pitches.add(pitch)

                yaw += actualYawStep
            }
            pitch += pitchStep
        }

        val raycasts = Raycasts(dx.toDoubleArray(), dy.toDoubleArray(), dz.toDoubleArray(), yaws.toFloatArray(), pitches.toFloatArray())

        lastPitchStep = pitchStep
        lastYawStep = yawStep
        cachedRaycasts = raycasts

        return raycasts
    }

    class Raycasts(val dx: DoubleArray, val dy: DoubleArray, val dz: DoubleArray, val yaws: FloatArray, val pitches: FloatArray)
}