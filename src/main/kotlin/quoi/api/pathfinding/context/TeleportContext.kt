package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.util.Raycasts

open class TeleportContext(
    goal: BlockPos,
    val dist: Double,
    val hWeight: Double,
    val raycasts: Raycasts,
    timeout: Long
) : PathContext<TeleportPathNode>(goal, timeout)