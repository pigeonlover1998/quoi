package quoi.api.pathfinding.impl

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

    fun findPath(start: OdonRoom, goal: OdonRoom): List<RoomPath>? {

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

            current.room.forEachNeighbour { room, door ->
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

    private inline fun OdonRoom.forEachNeighbour(block: (OdonRoom, OdonDoor) -> Unit) {
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
                                if (!door.actuallyLocked) { // wither/blood doors is a big no no
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