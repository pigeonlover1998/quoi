package quoi.api.pathfinding

import net.minecraft.core.BlockPos

data class PathNode(
    val pos: BlockPos,
    var g: Double,
    var h: Double,
    var parent: PathNode?
) : Comparable<PathNode> {
    val f: Double get() = g + h

    override fun compareTo(other: PathNode): Int = this.f.compareTo(other.f)
}