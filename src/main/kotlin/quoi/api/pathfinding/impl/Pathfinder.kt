package quoi.api.pathfinding.impl

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import quoi.utils.WorldUtils.airLike
import quoi.utils.WorldUtils.collisionShape
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.walkable
import quoi.utils.distanceTo
import quoi.api.pathfinding.PathNode
import quoi.utils.ChatUtils.modMessage
import java.util.PriorityQueue

object Pathfinder { // todo impl abstract pathfinder

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
        start: BlockPos,
        goal: BlockPos,
        maxNodes: Int = 10_000,
        hWeight: Double = 1.1,
        feedback: Boolean = false
    ): List<BlockPos>? {
        val goal = goal.above()

        val startTime = System.currentTimeMillis()

        val openSet = PriorityQueue<PathNode>()
        val closedSet = LongOpenHashSet()
        val nodeMap = Long2ObjectOpenHashMap<PathNode>()
        val penaltyCache = Long2ObjectOpenHashMap<Double>()

        val startNode = PathNode(start, 0.0, start.distanceTo(goal), null)
        openSet.add(startNode)
        nodeMap[start.asLong()] = startNode

        var processed = 0

        while (openSet.isNotEmpty() && processed < maxNodes) {

            val current = openSet.poll()
            val currentLong = current.pos.asLong()

            val best = nodeMap[currentLong]
            if (best != null && current.g > best.g) {
                continue
            }

            if (current.pos == goal) {
                if (feedback) modMessage("Found path in ${System.currentTimeMillis() - startTime}ms ($processed)")
                return current.path
            }

            closedSet.add(currentLong)
            processed++

            current.pos.forEachNeighbour { neighbour ->
                val neighbourLong = neighbour.asLong()

                if (neighbourLong !in closedSet) {
                    val stepCost = current.pos.distanceTo(neighbour)
                    val posPenalty = getPenalty(neighbour, neighbourLong, penaltyCache)
                    val gCost = current.g + stepCost + posPenalty

                    val neighbourNode = nodeMap[neighbourLong]

                    if (neighbourNode == null || gCost < neighbourNode.g) {
                        val hCost = neighbour.distanceTo(goal) * hWeight
                        val node = PathNode(neighbour, gCost, hCost, current)
                        openSet.add(node)
                        nodeMap[neighbourLong] = node
                    }
                }
            }
        }

        if (feedback) modMessage("Failed after ${System.currentTimeMillis() - startTime}ms ($processed)")
        return null
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

    private fun getPenalty(pos: BlockPos, posLong: Long, cache: Long2ObjectOpenHashMap<Double>): Double {
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

    private inline val PathNode.path: List<BlockPos>
        get() {
            val path = mutableListOf<BlockPos>()
            var current: PathNode? = this

            while (current != null) {
                path.add(0, current.pos.below())
                current = current.parent
            }
            return path
        }
}