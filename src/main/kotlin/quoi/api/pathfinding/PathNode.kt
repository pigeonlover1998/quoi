package quoi.api.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom

open class PathNode(
    val x: Double,
    val y: Double,
    val z: Double,
    val pos: BlockPos,
    var g: Double,
    var h: Double,
    var parent: PathNode?
) : Comparable<PathNode> {
    val vec by lazy { Vec3(x, y, z) }

    val f: Double get() = g + h

    override fun compareTo(other: PathNode): Int = this.f.compareTo(other.f)
}

class TeleportPathNode(
    x: Double,
    y: Double,
    z: Double,
    pos: BlockPos,
    g: Double,
    h: Double,
    parent: PathNode?,
    val yaw: Float,
    val pitch: Float
) : PathNode(x, y, z, pos, g, h, parent)

class RoomNode(
    val room: OdonRoom,
    val doorFromParent: OdonDoor?,
    val g: Int,
    val h: Int,
    val parent: RoomNode?
) : Comparable<RoomNode> {
    val f: Int get() = g + h

    override fun compareTo(other: RoomNode): Int = this.f.compareTo(other.f)
}