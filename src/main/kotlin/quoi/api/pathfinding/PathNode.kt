package quoi.api.pathfinding

import net.minecraft.core.BlockPos

open class PathNode(
    val pos: BlockPos,
    var g: Double,
    var h: Double,
    var parent: PathNode?
) : Comparable<PathNode> {
    val f: Double get() = g + h

    override fun compareTo(other: PathNode): Int = this.f.compareTo(other.f)
}

class EtherPathNode(
    pos: BlockPos,
    g: Double,
    h: Double,
    parent: PathNode?,
    val yaw: Float,
    val pitch: Float
) : PathNode(pos, g, h, parent)