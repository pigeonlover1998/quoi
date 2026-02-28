package quoi.api.skyblock.dungeon.map.utils

import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.components.DiscoveredRoom
import quoi.api.skyblock.dungeon.components.Door
import quoi.api.skyblock.dungeon.components.Room

/**
 * from Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/utils/ScanUtils.kt
 */
object ScanUtils {

    val rooms = Array<Room?>(36) { null }
    val doors = Array<Door?>(60) { null }
    val uniqueRooms: MutableSet<Room> = mutableSetOf()
    val uniqueDoors: MutableSet<Door> = mutableSetOf()
    val discoveredRooms: MutableMap<String, DiscoveredRoom> = mutableMapOf()

    var currentRoom: Room? = null

    // Dungeon grid constants
    val cornerStart = Pair(-200, -200)
    val cornerEnd   = Pair(-10, -10)

    const val ROOM_SIZE = 31
    const val DOOR_SIZE = 1
    const val COMBINED_SIZE = ROOM_SIZE + DOOR_SIZE
    const val HALF_ROOM = ROOM_SIZE / 2
    const val HALF_COMBINED = COMBINED_SIZE / 2

    val defaultMapSize = Pair(125, 125)

    val directions = listOf(
        listOf(HALF_COMBINED, 0, 1, 0),
        listOf(-HALF_COMBINED, 0, -1, 0),
        listOf(0, HALF_COMBINED, 0, 1),
        listOf(0, -HALF_COMBINED, 0, -1)
    )

    val mapDirections = listOf(
        1 to 0,  // East
        -1 to 0, // West
        0 to 1,  // South
        0 to -1  // North
    )

    val roomTypeMap = mapOf(
        "normal" to RoomType.NORMAL,
        "puzzle" to RoomType.PUZZLE,
        "trap" to RoomType.TRAP,
        "champion" to RoomType.YELLOW,
        "blood" to RoomType.BLOOD,
        "fairy" to RoomType.FAIRY,
        "rare" to RoomType.RARE,
        "entrance" to RoomType.ENTRANCE
    )

    val mapColorToRoomType = mapOf(
        18 to RoomType.BLOOD,
        30 to RoomType.ENTRANCE,
        63 to RoomType.NORMAL,
        82 to RoomType.FAIRY,
        62 to RoomType.TRAP,
        74 to RoomType.YELLOW,
        66 to RoomType.PUZZLE
    )

    fun getScanCoords(): List<Triple<Int, Int, Pair<Int, Int>>> {
        val coords = mutableListOf<Triple<Int, Int, Pair<Int, Int>>>()

        for (z in 0..<11) {
            for (x in 0..<11) {
                if (x % 2 == 1 && z % 2 == 1) continue

                val rx = cornerStart.first + HALF_ROOM + x * HALF_COMBINED
                val rz = cornerStart.second + HALF_ROOM + z * HALF_COMBINED
                coords += Triple(x, z, Pair(rx, rz))
            }
        }

        return coords
    }

    // Room accessors
    fun getRoomIdx(comp: Pair<Int, Int>) = 6 * comp.second + comp.first
    fun getRoomAtIdx(idx: Int) = rooms.getOrNull(idx)
    fun getRoomAtComp(comp: Pair<Int, Int>) = getRoomAtIdx(getRoomIdx(comp))
    fun getRoomAt(x: Int, z: Int) = getRoomAtComp(WorldScanUtils.realCoordToComponent(x, z))

    // Door accessors
    fun getDoorIdx(comp: Pair<Int, Int>): Int {
        val base = ((comp.first - 1) shr 1) + 6 * comp.second
        return base - (base / 12)
    }

    fun getDoorAtIdx(idx: Int) = doors.getOrNull(idx)
    fun getDoorAtComp(comp: Pair<Int, Int>) = getDoorAtIdx(getDoorIdx(comp))
    fun getDoorAt(x: Int, z: Int) = getDoorAtComp(WorldScanUtils.realCoordToComponent(x, z))

    /** Adds a door to the map and tracks it as unique */
    fun addDoor(door: Door) {
        val idx = getDoorIdx(door.componentPos)
        if (idx in doors.indices) {
            doors[idx] = door
            uniqueDoors += door
        }
    }

    /** Merges two rooms into one unified instance */
    fun mergeRooms(room1: Room, room2: Room) {
        uniqueRooms.remove(room2)
        for (comp in room2.components) {
            if (!room1.hasComponent(comp.first, comp.second)) {
                room1.addComponent(comp, update = false)
            }
            val idx = getRoomIdx(comp)
            if (idx in rooms.indices) rooms[idx] = room1
        }
        uniqueRooms += room1
        room1.update()
    }

    fun reset() {
        rooms.fill(null)
        doors.fill(null)
        uniqueRooms.clear()
        uniqueDoors.clear()
        discoveredRooms.clear()
    }
}