package quoi.api.pathfinding

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import quoi.api.pathfinding.context.TeleportContext
import quoi.utils.distanceTo
import quoi.utils.dot
import quoi.utils.sq
import kotlin.math.sqrt

abstract class AbstractTeleportPathfinder<T : TeleportContext> : AbstractPathfinder<TeleportPathNode, T>() {

    abstract fun getEyeY(ctx: T, node: TeleportPathNode): Double

    open fun getNodeY(ctx: T, hit: BlockPos): Double = hit.y.toDouble()

    abstract fun getHit(ctx: T, eyeX: Double, eyeY: Double, eyeZ: Double, dx: Double, dy: Double, dz: Double): BlockPos?

    override fun expand(ctx: T, current: TeleportPathNode) {
        val eyeX = current.x
        val eyeY = getEyeY(ctx, current)
        val eyeZ = current.z

        val goalX = ctx.goal.x + 0.5
        val goalY = ctx.goal.y.toDouble()
        val goalZ = ctx.goal.z + 0.5

        val vx = goalX - current.x
        val vy = goalY - current.y
        val vz = goalZ - current.z
        val dist = sqrt(vx.sq + vy.sq + vz.sq)
        val invDist = if (dist > 0) 1.0 / dist else 0.0

        val dirX = vx * invDist
        val dirY = vy * invDist
        val dirZ = vz * invDist

        var pDirX = 0.0
        var pDirY = 0.0
        var pDirZ = 0.0

        val parent = current.parent?.pos

        if (parent != null) {
            val px = parent.x - current.pos.x
            val py = parent.y - current.pos.y
            val pz = parent.z - current.pos.z.toDouble()
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

            val gDot = dot(dx, dy, dz, dirX, dirY, dirZ) / ctx.raycasts.scale

            if (gDot <= 0.5) { // if not looking towards the goal
                if (gDot > 0.0 && i % 2 != 0) continue // if sideways only cast 50% of the time
                else if (gDot <= 0.0 && i % 4 != 0) continue // if backwards only cast 25% of the time
            }

            if (parent != null) {
                val pDot = dot(dx, dy, dz, pDirX, pDirY, pDirZ) / ctx.raycasts.scale
                if (pDot > 0.65) continue // if pointing too strongly towards the parent
            }

            val result = getHit(ctx, eyeX, eyeY, eyeZ, dx, dy, dz)

            if (result != null) {
                if (hitCache.add(result.asLong())) {
                    val hCost = (result.distanceTo(ctx.goal) / ctx.dist) * ctx.hWeight

                    val nx = result.x + 0.5
                    val ny = getNodeY(ctx, result)
                    val nz = result.z + 0.5

                    val node = TeleportPathNode(nx, ny, nz, result, current.g + 1.0, hCost, current, ctx.raycasts.yaws[i], ctx.raycasts.pitches[i])
                    ctx.addNode(node)
                }
            }
        }
    }
}