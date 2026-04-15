package quoi.utils.pathfinding

import net.minecraft.core.BlockPos

data class PathNode(
    val pos: BlockPos,
    var g: Double,
    var h: Double,
    var parent: PathNode?
) : Comparable<PathNode> {
    val f: Double get() = g + h

    override fun compareTo(other: PathNode): Int = this.f.compareTo(other.f)

    val path: List<BlockPos> get() {
        val path = mutableListOf<BlockPos>()
        var current: PathNode? = this

        while (current != null) {
            path.add(0, current.pos.below())
            current = current.parent
        }
        return path
    }
}