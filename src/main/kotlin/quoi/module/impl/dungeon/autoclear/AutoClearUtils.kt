package quoi.module.impl.dungeon.autoclear

import net.minecraft.core.BlockPos
import quoi.api.colour.Colour
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.impl.DungeonMapPathfinder
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.DoorType
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomTile
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearAotvNode
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearEtherNode
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearHypeNode
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes.routeNodes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.addVec
import quoi.utils.distanceToSqr
import quoi.utils.equalsOneOf
import quoi.utils.floorPos
import quoi.utils.getLook
import quoi.utils.player
import quoi.utils.rayCastVec
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import kotlin.collections.filter

const val HYPE_AOE = 6.0

private val roomOverrides = mapOf(
    "Creeper Beams" to BlockPos(15, 68, 5),
    "Three Weirdos" to BlockPos(15, 68, 22),
    "Water Board" to BlockPos(15, 58, 9),
    "Ice Path" to BlockPos(10, 67, 8),
    "Tic Tac Toe" to BlockPos(11, 68, 16),
    "Ice Fill" to BlockPos(15, 69, 7),
    "Quiz" to BlockPos(15, 68, 5),
    "Boulder" to BlockPos(15, 68, -2),
    "Teleport Maze" to BlockPos(15, 68, -2),
    "Old Trap" to BlockPos(15, 68, -2),
    "New Trap" to BlockPos(15, 68, -2),
    "Cages" to BlockPos(15, 64, 16)
)

private val coreOverrides = mapOf(
    "Gold" to mapOf(
        35550104 to BlockPos(5, 68, 15),
        992885012 to BlockPos(55, 68, 15)
    ),
    "Layers" to mapOf(
        161195688 to BlockPos(53, 68, 53)
    ),
    "Mage" to mapOf(
        925853313 to BlockPos(15, 75, 15)
    ),
    "Deathmite" to mapOf(
        706341009 to BlockPos(5, 68, 15)
    ),
    "Dragon" to mapOf(
        -1334473473 to BlockPos(15, 68, 18)
    )
)

private val canPath: Boolean
    get() {
        if (!player.onGround()) return false
        val room = Dungeon.currentRoom ?: return true
        if (room.name.containsOneOf("Maze", "Boulder")) return false
        if (room.name.contains("Trap") && room.getRelativeCoords(player.blockPosition()).z >= 0) return false
        return true
    }

/**
 * Searches for the nearest locked Wither or Blood door in the dungeon.
 *
 * @return The closest locked [OdonDoor] based on pathfinding distance, or null if none found.
 */
fun getLockedDoor(): OdonDoor? {
    val room = Dungeon.currentRoom ?: return null
    val door = ScanUtils.scannedDoors
        .filter { it.locked && it.type.equalsOneOf(DoorType.WITHER, DoorType.BLOOD) }
        .minByOrNull { DungeonMapPathfinder.getDistToDoor(room, it, ignoreLocked = true) }
        ?: return null
    return door
}

/**
 * Starts an etherwarp path towards the specified [door]
 *
 * @param door Destination door
 */
fun pathToDoor(door: OdonDoor) {
    if (!canPath) return
    val goal = DungeonMapPathfinder.getDoorPos(Dungeon.currentRoom!!, door)
        ?: return modMessage("Could not find door coordinates")

    ClearExecutor.etherPath(to = goal)
}

/**
 * Navigates to a specific [room] based on target [type]
 *
 * @param room Destination room.
 * @param tile Specific room tile.
 * @param type Navigation type: 0 for a coordinate/core override, 1 for a predefined autoroute start.
 */
fun pathToRoom(room: OdonRoom, tile: RoomTile = room.tiles.first(), type: Int) {
    if (!canPath || (type != 0 && type != 1)) return

    val goal = when(type) { // 0 tile 1 start
        0 -> {
            val overridePos = roomOverrides[room.name] ?: coreOverrides[room.name]?.get(tile.core)

            if (overridePos != null && room.rotation != Rotations.NONE) {
                room.getRealCoords(overridePos)
            } else {
                tile.blockPos.nearbyBlocks(25f) { it.etherwarpable }.firstOrNull()
                    ?: return modMessage("Couldn't find goal position for tile &e${tile.core}&r in ${room.name}")
            }
        }
        1 -> {
            val rings = routeNodes[room.name] ?: return modMessage("No rings found in &e${room.name}")

            val starts = rings
                .filter { it.start == true }
                .map { room.getRealCoords(it.relative.floorPos) }
                .filter { it.etherwarpable }

            val target = starts
                .find { it.distanceToSqr(tile.blockPos) < 225 } ?: starts.firstOrNull()
            ?: return modMessage("&cCouldn't find start ring.")

            target
        }
        else -> return
    }

    ClearExecutor.etherPath(to = goal)
}

fun TeleportPathNode.toEther(): ClearEtherNode {
    val from = vec.addVec(y = getEyeHeight(true))
    val to = getLook(yaw, pitch).scale(60.0)
    val target = rayCastVec(from, to).vec
    return ClearEtherNode(vec, yaw, pitch, Colour.ORANGE, listOf(from, target))
}

fun TeleportPathNode.toAotv(): ClearAotvNode {
    val from = vec.addVec(y = getEyeHeight(false))
    val to = from.add(getLook(yaw, pitch).scale(12.0))
    return ClearAotvNode(vec, yaw, pitch, Colour.BLUE, listOf(from, to))
}

fun TeleportPathNode.toHype(): ClearHypeNode {
    val from = vec.addVec(y = getEyeHeight(false))
    val to = from.add(getLook(yaw, pitch).scale(10.0))
    return ClearHypeNode(vec, yaw, pitch, Colour.CYAN, listOf(from, to))
}