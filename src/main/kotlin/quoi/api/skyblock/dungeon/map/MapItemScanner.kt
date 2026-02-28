package quoi.api.skyblock.dungeon.map

import quoi.api.skyblock.dungeon.*
import quoi.api.skyblock.dungeon.components.DiscoveredRoom
import quoi.api.skyblock.dungeon.components.Door
import quoi.api.skyblock.dungeon.components.Room
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_BLOOD
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_EMPTY
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_ENTRANCE
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_EXPLORED
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_UNEXPLORED
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.COLOUR_WHITE
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.MAP_LIMIT
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.MAP_WIDTH
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.mapX
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.mapZ
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils.yaw
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import quoi.api.skyblock.dungeon.map.utils.ScanUtils.rooms
import quoi.mixins.accessors.MapStateAccessor
import quoi.utils.TimeUtils
import quoi.utils.WorldUtils.worldToMap
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import kotlin.time.Duration

/**
 * from Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/map/MapScanner.kt
 */
object MapItemScanner {

    data class RoomClearInfo(
        val time: Duration,
        val room: Room,
        val solo: Boolean
    )

    fun scan(state: MapItemSavedData) {
        val colors = state.colors

        val startX = MapItemUtils.mapCorners.first + MapItemUtils.mapRoomSize / 2
        val startZ = MapItemUtils.mapCorners.second + MapItemUtils.mapRoomSize / 2 + 1
        val stepSize = MapItemUtils.mapGapSize / 2

        var cx = -1
        for (x in startX until MAP_LIMIT step stepSize) {
            cx++
            var cz = -1
            for (z in startZ until MAP_LIMIT step stepSize) {
                cz++
                val idx = x + z * MAP_WIDTH

                val centre = colors.getOrNull(idx - 1) ?: continue
                val rcolor = colors.getOrNull(idx + 5 + MAP_WIDTH * 4) ?: continue

                if (cx % 2 == 0 && cz % 2 == 0 && rcolor != COLOUR_EMPTY) {
                    handleRoom(cx, cz, x, z, centre, rcolor, colors)
                } else if ((cx % 2 != 0 || cz % 2 != 0) && centre != COLOUR_EMPTY) {
                    handleDoor(cx, cz, idx, centre, colors)
                }
            }
        }
    }

    private fun handleRoom(
        cx: Int, cz: Int, x: Int, z: Int,
        centre: Byte, rcolor: Byte, colors: ByteArray
    ) {
        val rmx = cx / 2
        val rmz = cz / 2
        val roomIdx = ScanUtils.getRoomIdx(rmx to rmz)

        val room = rooms[roomIdx] ?: Room(rmx to rmz).also {
            rooms[roomIdx] = it
            ScanUtils.uniqueRooms.add(it)
        }

        // adjacency check
        checkRoomAdjacency(room, cx, cz, x, z, colors)

        // type/height
        if (room.type == RoomType.UNKNOWN && room.height == null) {
            room.loadFromMapColor(rcolor)
        }

        // exploration state
        if (rcolor == COLOUR_EMPTY) {
            room.explored = false
            return
        }

        if (centre == COLOUR_EXPLORED || rcolor == COLOUR_UNEXPLORED) {
            room.explored = false
            room.checkmark = Checkmark.UNEXPLORED
            ScanUtils.discoveredRooms["$rmx/$rmz"] = DiscoveredRoom(rmx, rmz, room)
            return
        }

        // checkmark logic
        updateRoomCheckmark(room, centre, rcolor)

        room.explored = true
        ScanUtils.discoveredRooms.remove("$rmx/$rmz")
    }

    private fun handleDoor(cx: Int, cz: Int, idx: Int, centre: Byte, colors: ByteArray) {
        if (!isDoorPixel(idx, colors)) return

        val comp = cx to cz
        val doorIdx = ScanUtils.getDoorIdx(comp)
        val door = ScanUtils.getDoorAtIdx(doorIdx)

        val rx = ScanUtils.cornerStart.first + ScanUtils.HALF_ROOM + cx * ScanUtils.HALF_COMBINED
        val rz = ScanUtils.cornerStart.second + ScanUtils.HALF_ROOM + cz * ScanUtils.HALF_COMBINED

        if (door == null) {
            val newDoor = Door(rx to rz, comp).apply {
                rotation = if (cz % 2 == 1) 0 else 1
                type = when (centre) {
                    COLOUR_EXPLORED -> DoorType.WITHER
                    COLOUR_BLOOD -> DoorType.BLOOD
                    else -> DoorType.NORMAL
                }
                state = DoorState.DISCOVERED
            }
            ScanUtils.addDoor(newDoor)
        } else {
            door.state = DoorState.DISCOVERED
            door.opened = centre != COLOUR_EXPLORED && centre != COLOUR_BLOOD
        }
    }


    private fun checkRoomAdjacency(room: Room, cx: Int, cz: Int, x: Int, z: Int, colors: ByteArray) {
        for ((dx, dz) in ScanUtils.mapDirections) {
            val doorCx = cx + dx
            val doorCz = cz + dz
            if (doorCx % 2 == 0 && doorCz % 2 == 0) continue

            val doorX = x + dx * MapItemUtils.mapGapSize / 2
            val doorZ = z + dz * MapItemUtils.mapGapSize / 2
            val doorIdx = doorX + doorZ * MAP_WIDTH
            val centre = colors.getOrNull(doorIdx)

            val isGap = centre == null || centre == COLOUR_EMPTY

            if (isGap || isDoorPixel(doorIdx, colors)) continue

            val neighborCx = cx + dx * 2
            val neighborCz = cz + dz * 2
            val neighborComp = neighborCx / 2 to neighborCz / 2
            val neighborIdx = ScanUtils.getRoomIdx(neighborComp)
            if (neighborIdx !in rooms.indices) continue

            val neighborRoom = rooms[neighborIdx]
            if (neighborRoom == null) {
                room.addComponent(neighborComp)
                rooms[neighborIdx] = room
            } else if (neighborRoom != room && neighborRoom.type != RoomType.ENTRANCE) {
                ScanUtils.mergeRooms(neighborRoom, room)
            }
        }
    }

    private fun updateRoomCheckmark(room: Room, center: Byte, rcolor: Byte) {
        when {
            center == COLOUR_ENTRANCE && rcolor != COLOUR_ENTRANCE -> {
                if (room.checkmark != Checkmark.GREEN) roomCleared(room, Checkmark.GREEN)
                room.checkmark = Checkmark.GREEN
            }
            center == COLOUR_WHITE -> {
                if (room.checkmark != Checkmark.WHITE) roomCleared(room, Checkmark.WHITE)
                room.checkmark = Checkmark.WHITE
            }
            rcolor == COLOUR_BLOOD && Dungeon.bloodDone -> {
                if (room.checkmark != Checkmark.WHITE) roomCleared(room, Checkmark.WHITE)
                room.checkmark = Checkmark.WHITE
            }
            center == COLOUR_BLOOD && rcolor != COLOUR_BLOOD -> {
                room.checkmark = Checkmark.FAILED
            }
            room.checkmark == Checkmark.UNEXPLORED -> {
                room.checkmark = Checkmark.NONE
                room.clearTime = TimeUtils.now
            }
        }
    }

    fun updatePlayers(state: MapItemSavedData) {
        val decorations = (state as MapStateAccessor).decorations.values
        val firstPlayer = Dungeon.dungeonTeammates.firstOrNull()

        val otherPlayers = Dungeon.dungeonTeammates
            .drop(1)
            .filter { !it.isDead && it.uuid != null }
            .iterator()

        for (mapDecoration in decorations) {
            if (mapDecoration == null) continue

            val dplayer: DungeonPlayer = if (mapDecoration.type.value() == MapDecorationTypes.FRAME.value()) {
                firstPlayer ?: continue
            } else {
                if (otherPlayers.hasNext()) otherPlayers.next() else continue
            }

            if (dplayer.inRender) continue

            val iconX = worldToMap(mapDecoration.mapX.toDouble() - MapItemUtils.mapCorners.first, 0.0, MapItemUtils.mapRoomSize * 6.0 + 20.0, 0.0, ScanUtils.defaultMapSize.first.toDouble())
            val iconZ = worldToMap(mapDecoration.mapZ.toDouble() - MapItemUtils.mapCorners.second, 0.0, MapItemUtils.mapRoomSize * 6.0 + 20.0, 0.0, ScanUtils.defaultMapSize.second.toDouble())

            val realX = worldToMap(iconX, 0.0, 125.0, -200.0, -10.0)
            val realZ = worldToMap(iconZ, 0.0, 125.0, -200.0, -10.0)
            val realYaw = mapDecoration.yaw + 180f

            dplayer.pos.updatePosition(realX, realZ, realYaw, iconX, iconZ)

            dplayer.currRoom = ScanUtils.getRoomAt(realX.toInt(), realZ.toInt())?.also {
                it.players.add(dplayer)
            }
        }
    }

    private fun roomCleared(room: Room, check: Checkmark) {
        val players = room.players

        players.forEach { player ->
            val alreadyCleared = player.getWhiteChecks().containsKey(room.name) || player.getGreenChecks().containsKey(room.name)

            if (!alreadyCleared) {
                if (players.size == 1) player.minRooms++
                player.maxRooms++
            }

            val colorKey = if (check == Checkmark.GREEN) "GREEN" else "WHITE"
            val clearedMap = player.clearedRooms[colorKey]

            clearedMap?.putIfAbsent(
                room.name,
                RoomClearInfo(
                    time = room.clearTime.since,
                    room = room,
                    solo = players.size == 1
                )
            )
        }
    }

    private fun isDoorPixel(idx: Int, colors: ByteArray): Boolean {
        val h1 = colors.getOrNull(idx - MAP_WIDTH - 4) ?: 0
        val h2 = colors.getOrNull(idx - MAP_WIDTH + 4) ?: 0
        if (h1 == COLOUR_EMPTY && h2 == COLOUR_EMPTY) return true

        val v1 = colors.getOrNull(idx - MAP_WIDTH * 5) ?: 0
        val v2 = colors.getOrNull(idx + MAP_WIDTH * 3) ?: 0
        return v1 == COLOUR_EMPTY && v2 == COLOUR_EMPTY
    }
}