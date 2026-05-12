package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.TransmissionPathNode
import quoi.api.pathfinding.impl.TransmissionPathfinder.Raycasts

class TransmissionContext(
    goal: BlockPos,
    val dist: Double,
    val hWeight: Double,
    val raycasts: Raycasts,
    timeout: Long
) : PathContext<TransmissionPathNode>(goal, timeout)