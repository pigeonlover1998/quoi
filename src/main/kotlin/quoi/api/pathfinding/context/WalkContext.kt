package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.PathNode
import quoi.utils.Vec3
import java.util.concurrent.ConcurrentHashMap

class WalkContext(
    goal: BlockPos,
    timeout: Long,
    val hWeight: Double
) : PathContext<PathNode>(goal, timeout) {
    val penaltyCache = ConcurrentHashMap<Long, Double>()
    val goalVec = Vec3(goal.x + 0.5, goal.y.toDouble(), goal.z + 0.5)
}
