package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.EtherPathNode
import quoi.api.pathfinding.impl.EtherwarpPathfinder.Raycasts

class EtherwarpContext(
    goal: BlockPos,
    val dist: Double,
    val hWeight: Double,
    val raycasts: Raycasts,
    timeout: Long,
    val offset: Boolean,
) : PathContext<EtherPathNode>(goal, timeout)