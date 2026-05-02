package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.RoomNode
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import java.util.IdentityHashMap
import java.util.PriorityQueue
import kotlin.math.abs

// ty rice skyblock addons
object DungeonMapPathfinder {

    // door x, door z, next room x, next room z
    private val directions = arrayOf(
        intArrayOf(0, -1, 0, -2), // n
        intArrayOf(0, 1, 0, 2), // s
        intArrayOf(1, 0, 2, 0), // e
        intArrayOf(-1, 0, -2, 0) // w
    )

    fun findPath(start: OdonRoom, goal: OdonRoom, ignoreLocked: Boolean = false): List<RoomPath>? {

        val openSet = PriorityQueue<RoomNode>()
        val nodeMap = IdentityHashMap<OdonRoom, Int>()

        openSet.add(RoomNode(start, null, 0, getHeuristic(start, goal), null))
        nodeMap[start] = 0

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            val best = nodeMap[current.room]
            if (best != null && current.g > best) continue

            if (current.room === goal) {
                return reconstructPath(current)
            }

            current.room.forEachNeighbour(ignoreLocked) { room, door ->
                val gCost = current.g + 1
                val neighbourBest = nodeMap[room]

                if (neighbourBest == null || gCost < neighbourBest) {
                    nodeMap[room] = gCost
                    val hCost = getHeuristic(room, goal)
                    openSet.add(RoomNode(room, door, gCost, hCost, current))
                }
            }
        }

        return null
    }

    fun getDistToDoor(start: OdonRoom, door: OdonDoor, ignoreLocked: Boolean = false): Int =
        stupid(start, door, ignoreLocked)?.third ?: Int.MAX_VALUE

    fun getDoorPos(start: OdonRoom, door: OdonDoor): BlockPos? {
        val pos = BlockPos(door.pos.x, 68, door.pos.z)
        if (!door.locked) return pos // no need to offset

        val (i, parent, _) = stupid(start, door, false) ?: return null

        val dx = (parent % 11) - (i % 11) // -1 0 or 1
        val dz = (parent / 11) - (i / 11)

        return BlockPos(door.pos.x + dx * 2, 68, door.pos.z + dz * 2)
    }

    // returns
    // door grid index
    // parent grid index (room from which you can et to the door)
    // distance to door (how many rooms/doors away)
    private fun stupid(start: OdonRoom, door: OdonDoor, inoreLocked: Boolean): Triple<Int, Int, Int>? {
        val i = ScanUtils.grid.indexOf(door)
        if (i == -1) return null

        val doorX = i % 11
        val doorZ = i / 11

        val horizontal = doorX % 2 != 0

        // rooms surrounding the door
        val i1 = if (horizontal) (doorZ * 11 + (doorX - 1)) else ((doorZ - 1) * 11 + doorX)
        val i2 = if (horizontal) (doorZ * 11 + (doorX + 1)) else ((doorZ + 1) * 11 + doorX)

        val room1 = ScanUtils.grid.getOrNull(i1) as? OdonRoom
        val room2 = ScanUtils.grid.getOrNull(i2) as? OdonRoom

        // how many rooms away
        val dist1 = if (room1 === start) 0 else room1?.let { findPath(start, it, inoreLocked)?.size } ?: Int.MAX_VALUE
        val dist2 = if (room2 === start) 0 else room2?.let { findPath(start, it, inoreLocked)?.size } ?: Int.MAX_VALUE

        if (dist1 == Int.MAX_VALUE && dist2 == Int.MAX_VALUE) return null

        return if (dist1 <= dist2) Triple(i, i1, dist1) else Triple(i, i2, dist2)
    }

    private inline fun OdonRoom.forEachNeighbour(ignoreLocked: Boolean, block: (OdonRoom, OdonDoor) -> Unit) {
        val grid = ScanUtils.grid

        for (z in 0..10 step 2) {
            for (x in 0..10 step 2) {
                if (grid[z * 11 + x] === this) { // belogns to this room

                    for (i in directions.indices) {
                        val dir = directions[i]
                        val doorX = x + dir[0]
                        val doorZ = z + dir[1]
                        val nextRoomX = x + dir[2]
                        val nextRoomZ = z + dir[3]

                        if (nextRoomX in 0..10 && nextRoomZ in 0..10) {
                            val door = grid[doorZ * 11 + doorX]
                            val nextRoomTile = grid[nextRoomZ * 11 + nextRoomX]

                            if (door is OdonDoor && nextRoomTile is OdonRoom && nextRoomTile !== this) { // check if it's actually a door and there's a room next to it. jic check if it's not het same room
                                if (!door.locked || ignoreLocked) { // wither/blood doors is a big no no
                                    block(nextRoomTile, door)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getHeuristic(current: OdonRoom, goal: OdonRoom): Int {
        val from = current.textPlacement
        val to = goal.textPlacement
        return (abs(from.x - to.x) + abs(from.z - to.z)) / 20
    }

    private fun reconstructPath(node: RoomNode): List<RoomPath> {
        val path = mutableListOf<RoomPath>()
        var current: RoomNode? = node
        var nextDoor: OdonDoor? = null

        while (current != null) {
            path.add(0, RoomPath(current.room, nextDoor))
            nextDoor = current.doorFromParent
            current = current.parent
        }

        return path
    }
}

data class RoomPath(val room: OdonRoom, val door: OdonDoor?) { // door is null in goal room
    override fun toString(): String {
        return "${room.name} -> ${door?.type}"
    }
}