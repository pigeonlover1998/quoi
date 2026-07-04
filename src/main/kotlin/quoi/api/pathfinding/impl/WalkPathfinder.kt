package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import quoi.api.pathfinding.AbstractPathfinder
import quoi.utils.WorldUtils.airLike
import quoi.utils.WorldUtils.collisionShape
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.walkable
import quoi.utils.distanceTo
import quoi.api.pathfinding.PathNode
import quoi.api.pathfinding.context.WalkContext
import quoi.module.impl.render.clickgui.impl.PathSettings
import quoi.utils.ChatUtils.modMessage
import java.util.concurrent.ConcurrentHashMap

object WalkPathfinder : AbstractPathfinder<PathNode, WalkContext>() { // todo smoothing

    private val directions = arrayOf(
        intArrayOf(1, 0, 0), // n
        intArrayOf(-1, 0, 0), // s
        intArrayOf(0, 0, 1), // e
        intArrayOf(0, 0, -1), // w
        intArrayOf(1, 0, 1), // ne
        intArrayOf(1, 0, -1), // nw
        intArrayOf(-1, 0, 1), // se
        intArrayOf(-1, 0, -1) // sw
    )

    fun findPath(
        start: Vec3,
        goal: BlockPos,
        hWeight: Double = 1.1,
        threads: Int = PathSettings.threads,
        timeout: Long = PathSettings.timeout,
        feedback: Boolean = false
    ): List<Vec3>? {
        val goal = goal.above()
        val startTime = System.currentTimeMillis()

        val ctx = WalkContext(goal, timeout, hWeight)

        val startPos = BlockPos.containing(start)

        val startNode = PathNode(start.x, start.y, start.z, startPos, 0.0, startPos.distanceTo(goal), null)

        ctx.addNode(startNode)

        val path = find(ctx, threads)

        if (feedback) {
            if (path != null) {
                modMessage("Found path in ${System.currentTimeMillis() - startTime}ms (${ctx.processed.get()})")
            } else {
                modMessage("Failed after ${System.currentTimeMillis() - startTime}ms (${ctx.processed.get()})")
            }
        }

        return path?.map { it.vec }
    }

    override fun expand(ctx: WalkContext, current: PathNode) {
        current.pos.forEachNeighbour { neighbour ->
            val stepCost = current.pos.distanceTo(neighbour)
            val posPenalty = getPenalty(neighbour, ctx.penaltyCache)
            val gCost = current.g + stepCost + posPenalty

            val hCost = neighbour.distanceTo(ctx.goal) * ctx.hWeight

            val node = PathNode(neighbour.x + 0.5, neighbour.y.toDouble(), neighbour.z + 0.5, neighbour, gCost, hCost, current)
            ctx.addNode(node)
        }
    }

    private inline fun BlockPos.forEachNeighbour(block: (BlockPos) -> Unit) {
        for (dir in directions) {
            val nx = this.x + dir[0]
            val ny = this.y + dir[1]
            val nz = this.z + dir[2]

            if (dir[0] != 0 && dir[2] != 0) { // diagonal
                if (!BlockPos(nx, this.y, this.z).walkable ||
                    !BlockPos(this.x, this.y, nz).walkable) continue
            }

            val neighbour = BlockPos(nx, ny, nz)

            if (neighbour.walkable) { // same y
                if (!neighbour.below().solid) { // fall down
                    var curr = neighbour
                    while (curr.y > 0) { // -64
                        curr = curr.below()

                        if (!curr.airLike) break

                        if (curr.below().solid && curr.walkable) {
                            block(curr)
                            break
                        }
                    }
                } else block(neighbour)
            } else { // go up
                val above = neighbour.above()
                if (neighbour.solid && above.walkable && this.above(2).airLike) {
                    block(above)
                }
            }
        }
    }

    private fun getPenalty(pos: BlockPos, cache: ConcurrentHashMap<Long, Double>): Double {
        val posLong = pos.asLong()
        val cached = cache[posLong]
        if (cached != null) return cached

        var penalty = 0.0
        var dirs = 0

//        val directions = directions.take(4)

        for (i in directions.indices) {
            val dir = directions[i]
            val neighbour = pos.offset(dir[0], dir[1], dir[2])

            val height = neighbour.collisionShape.max(Direction.Axis.Y) // to not treat slabs/trapdoors/etc as walls

            val wallPenalty =
                if (height > 1.0 && !neighbour.above().airLike) 1.5
                else 0.0

            val edgePenalty =
                if (neighbour.airLike &&
                    neighbour.below().airLike &&
                    neighbour.below(2).airLike
                ) 2.0
                else 0.0

            val pnl = edgePenalty + wallPenalty
            if (pnl > 0) {
                dirs++
                penalty = pnl * if (i > 3) 0.5 else 1.0
            }
        }

        if (dirs == 1) {
            penalty *= 0.5
        }

        cache[posLong] = penalty
        return penalty
    }
}