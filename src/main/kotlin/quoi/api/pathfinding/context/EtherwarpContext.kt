package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.util.Raycasts
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom

class EtherwarpContext(
    goal: BlockPos,
    dist: Double,
    hWeight: Double,
    raycasts: Raycasts,
    timeout: Long,
    val offset: Boolean,
    val radius: Double = 0.0, // squared
    val nextRoom: OdonRoom? = null
) : TeleportContext(goal, dist, hWeight, raycasts, timeout)