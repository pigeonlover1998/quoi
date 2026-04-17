package quoi.api.pathfinding.context

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import quoi.api.pathfinding.PathNode
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger

open class PathContext(
    val goal: BlockPos,
    val timeout: Long
) {
    val startTime = System.currentTimeMillis()
    val endTime = startTime + timeout

    @Volatile var solved = false
    @Volatile var timedOut = false
    @Volatile var finalPath: List<BlockPos>? = null

    val processed = AtomicInteger(0)
    val openSet = PriorityQueue<PathNode>()
    val nodeMap = Long2ObjectOpenHashMap<PathNode>()
    private val activeSet = LongOpenHashSet()

    val isDone: Boolean
        @Synchronized get() = openSet.isEmpty() && activeSet.isEmpty()

    @Synchronized
    fun getNext(): PathNode? {
        while (openSet.isNotEmpty()) {
            val node = openSet.poll()
            val posLong = node.pos.asLong()

            val best = nodeMap[posLong]
            if (best != null && node.g > best.g) {
                continue
            }

            activeSet.add(posLong)
            return node
        }
        return null
    }

    @Synchronized
    fun finishNode(long: Long) {
        activeSet.remove(long)
    }

    @Synchronized
    fun addNode(pos: BlockPos, gCost: Double, hCost: Double, parent: PathNode?) {
        if (solved) return

        val posLong = pos.asLong()

        val existing = nodeMap[posLong]
        if (existing == null || gCost < existing.g) {
            val node = PathNode(pos, gCost, hCost, parent)

            nodeMap[posLong] = node
            openSet.add(node)
        }
    }
}