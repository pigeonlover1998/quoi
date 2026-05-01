package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.EtherPathNode
import quoi.api.pathfinding.impl.EtherwarpPathfinder.Raycasts
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom

class EtherwarpContext(
    goal: BlockPos,
    val dist: Double,
    val hWeight: Double,
    val raycasts: Raycasts,
    timeout: Long,
    val offset: Boolean,
    val radius: Double = 0.0, // squared
    val nextRoom: OdonRoom? = null
) : PathContext<EtherPathNode>(goal, timeout)