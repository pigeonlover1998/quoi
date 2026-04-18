package quoi.api.pathfinding.impl

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.CauldronBlock
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import quoi.utils.ChatUtils
import quoi.utils.distanceTo
import quoi.utils.dot
import quoi.utils.getEtherwarpDirection
import quoi.utils.getEyeHeight
import quoi.utils.getLook
import quoi.api.pathfinding.AbstractPathfinder
import quoi.api.pathfinding.EtherPathNode
import quoi.api.pathfinding.context.EtherwarpContext
import quoi.utils.Vec3
import quoi.utils.rad
import quoi.utils.sq
import quoi.utils.traverseVoxels
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

object EtherwarpPathfinder : AbstractPathfinder<EtherPathNode, EtherwarpContext>() {

    private var lastDist = -1.0
    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    fun findPath(
        start: BlockPos,
        goal: BlockPos,
        dist: Double = 61.0,
        pitchStep: Float = 15f,
        yawStep: Float = 15f,
        hWeight: Double = 1.1,
        threads: Int = 2,
        timeout: Long = 1000L,
        offset: Boolean = true
    ): List<EtherPathNode>? {
        val raycasts = getRaycasts(dist, pitchStep, yawStep)
        val ctx = EtherwarpContext(goal, dist, hWeight, raycasts, timeout, offset)

        ctx.addNode(EtherPathNode(start, 0.0, start.distanceTo(goal) / dist, null, 0f, 0f))

        val path = find(ctx, threads)

        return if (path != null) {
            val smoothed = smoothPath(path, dist, offset)
            ChatUtils.modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). ${path.size} || ${smoothed.size}")
            smoothed
        } else {
            ChatUtils.modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    override fun expand(ctx: EtherwarpContext, current: EtherPathNode) {

        current.forEachEtherwarpHit(ctx) { pos, yaw, pitch ->
            val hCost = (pos.distanceTo(ctx.goal) / ctx.dist) * ctx.hWeight
            val node = EtherPathNode(pos, current.g + 1.0, hCost, current, yaw, pitch)
            ctx.addNode(node)
        }
    }

    private inline fun EtherPathNode.forEachEtherwarpHit(ctx: EtherwarpContext, block: (BlockPos, Float, Float) -> Unit) {
        val off = if (ctx.offset) 1.05 else 1.0
        val eyeX = pos.x + 0.5
        val eyeY = pos.y + off + getEyeHeight(true)
        val eyeZ = pos.z + 0.5

//        if (getEtherwarpDirection(eyePos, ctx.goal, ctx.dist) != null) {
//            block(ctx.goal)
//            return
//        }

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

            val gDot = dot(dx, dy, dz, dirX, dirY, dirZ) / ctx.dist

            if (gDot <= 0.5) { // if not looking towards the goal
                if (gDot > 0.0 && i % 2 != 0) continue // if sideways only cast 50% of the time
                else if (gDot <= 0.0 && i % 4 != 0) continue // if backwards only cast 25% of the time
            }

            if (parent != null) {
                val pDot = dot(dx, dy, dz, pDirX, pDirY, pDirZ) / ctx.dist
                if (pDot > 0.65) continue // if pointing too strongly towards the parent
            }

            val result = traverseVoxels(
                eyeX, eyeY, eyeZ,
                eyeX + dx, eyeY + dy, eyeZ + dz,
                etherwarp = true
            )

            if (result.succeeded && result.pos != null && !result.state.blackListed) {
                if (hitCache.add(result.pos.asLong())) {
                    block(result.pos, ctx.raycasts.yaws[i], ctx.raycasts.pitches[i])
                }
            }
        }
    }

    private fun getRaycasts(maxDist: Double, pitchStep: Float, yawStep: Float): Raycasts {
        if (maxDist == lastDist && pitchStep == lastPitchStep && yawStep == lastYawStep) {
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

                dx.add(vec.x * maxDist)
                dy.add(vec.y * maxDist)
                dz.add(vec.z * maxDist)

                yaws.add(yaw)
                pitches.add(pitch)

                yaw += actualYawStep
            }
            pitch += pitchStep
        }

        val raycasts = Raycasts(dx.toDoubleArray(), dy.toDoubleArray(), dz.toDoubleArray(), yaws.toFloatArray(), pitches.toFloatArray())

        lastDist = maxDist
        lastPitchStep = pitchStep
        lastYawStep = yawStep
        cachedRaycasts = raycasts


        return raycasts
    }

    private fun smoothPath(path: List<EtherPathNode>, dist: Double, offset: Boolean): List<EtherPathNode> {
        if (path.size <= 2) return path

        val smoothed = mutableListOf<EtherPathNode>()
        var i = 0

        while (i < path.size - 1) {
            var next = i + 1

            val current = path[i]
            val off = if (offset) 1.05 else 1.0
            val from = Vec3(current.pos.x + 0.5, current.pos.y + off + getEyeHeight(true), current.pos.z + 0.5)

            var yaw = path[next].yaw
            var pitch = path[next].pitch

            for (j in path.size - 1 downTo i + 2) {
                val dir = getEtherwarpDirection(from, path[j].pos, dist)
                if (dir != null) {
                    next = j
                    yaw = dir.yaw
                    pitch = dir.pitch
                    break
                }
            }

            smoothed.add(EtherPathNode(current.pos, current.g, current.h, current.parent, yaw, pitch))

            i = next
        }

        val goal = path.last()
        smoothed.add(EtherPathNode(goal.pos, goal.g, goal.h, goal.parent, 0f, 0f))

        return smoothed
    }

    inline val BlockState?.blackListed: Boolean
        get() {
            if (this == null) return true
            val isBottomSlab = block is SlabBlock && hasProperty(SlabBlock.TYPE) && getValue(SlabBlock.TYPE) == SlabType.BOTTOM

            return isBottomSlab ||
                    block is CarpetBlock ||
                    block is WallBlock ||
                    block is FenceBlock ||
                    block is FenceGateBlock ||
                    block is HopperBlock ||
                    block is CauldronBlock
        }

    class Raycasts(val dx: DoubleArray, val dy: DoubleArray, val dz: DoubleArray, val yaws: FloatArray, val pitches: FloatArray)
}