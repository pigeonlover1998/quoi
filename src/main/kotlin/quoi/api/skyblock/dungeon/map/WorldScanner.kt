package quoi.api.skyblock.dungeon.map

import quoi.QuoiMod.mc
import quoi.api.events.DungeonEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.components.Door
import quoi.api.skyblock.dungeon.components.Room
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import quoi.api.skyblock.dungeon.map.utils.WorldScanUtils
import quoi.utils.WorldUtils
import quoi.utils.WorldUtils.worldToMap
import java.util.*

/**
 * from Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/map/WorldScanner.kt
 */
object WorldScanner {
    val availableComponents = ScanUtils.getScanCoords().toMutableList()
    var lastIdx: Int? = null

    fun init() {
        EventBus.on<TickEvent.Start> {
            if (!inDungeons) return@on
            val player = mc.player ?: return@on

            // checking player states
            checkPlayerState()

            val (x, z) = WorldScanUtils.realCoordToComponent(player.x.toInt(), player.z.toInt())
            val idx = 6 * z + x

            // Bounds check
            if (idx < 35) {
                // Scan dungeon
                scan()

                // Rotation & door state updates
                checkRoomState()
                checkDoorState()

                val prevRoom = ScanUtils.rooms.getOrNull(lastIdx ?: -1)
                val currRoom = ScanUtils.rooms.getOrNull(idx)

                if (prevRoom != null && currRoom != null && prevRoom != currRoom) {
                    DungeonEvent.Room.Enter(currRoom, prevRoom)
                }

//                if (lastIdx == idx) return@on

                lastIdx = idx
                ScanUtils.currentRoom = ScanUtils.getRoomAt(player.x.toInt(), player.z.toInt())
                val (rmx, rmz) = ScanUtils.currentRoom?.components?.firstOrNull() ?: return@on
                ScanUtils.discoveredRooms.remove("$rmx/$rmz")
            }
        }
    }

    fun reset() {
        availableComponents.clear()
        availableComponents += ScanUtils.getScanCoords()
        lastIdx = null
    }

    fun scan() {
        if (availableComponents.isEmpty()) return

        for (idx in availableComponents.indices.reversed()) {
            val (cx, cz, rxz) = availableComponents[idx]
            val (rx, rz) = rxz
            if (!WorldScanUtils.isChunkLoaded(rx,0,rz)) continue
            val roofHeight = WorldScanUtils.getHighestY(rx, rz) ?: continue
            availableComponents.removeAt(idx)

            // Door detection
            if (cx % 2 == 1 || cz % 2 == 1) {
                if (roofHeight < 85) {
                    val comp = cx to cz
                    val doorIdx = ScanUtils.getDoorIdx(comp)
                    val existingDoor = ScanUtils.getDoorAtIdx(doorIdx)

                    if (existingDoor == null) {
                        val door = Door(rx to rz, comp).apply {
                            rotation = if (cz % 2 == 1) 0 else 1
                        }
                        ScanUtils.addDoor(door)
                    }
                }
                continue
            }

            val x = cx / 2
            val z = cz / 2
            val idx = ScanUtils.getRoomIdx(x to z)

            var room = ScanUtils.rooms[idx]

            if (room != null) {
                if (room.height == null) room.height = roofHeight
                room.scan()
            } else {
                room = Room(x to z, roofHeight).scan()
                ScanUtils.rooms[idx] = room
                ScanUtils.uniqueRooms.add(room)
                DungeonEvent.Room.Scan(room).post()
            }

            // Scan neighbors *before* claiming this room index
            for ((dx, dz, cxoff, zoff) in ScanUtils.directions.map { it }) {
                val nx = rx + dx
                val nz = rz + dz
                val blockBelow = WorldUtils.getBlockNumericId(nx, roofHeight, nz)
                val blockAbove = WorldUtils.getBlockNumericId(nx, roofHeight + 1, nz)

                if (room.type == RoomType.ENTRANCE && blockBelow != 0) {
                    continue
                }
                if (blockBelow == 0 || blockAbove != 0) continue

                val neighborComp = Pair(x + cxoff, z + zoff)
                val neighborIdx = ScanUtils.getRoomIdx(neighborComp)
                if (neighborIdx !in ScanUtils.rooms.indices) continue

                val neighborRoom = ScanUtils.rooms[neighborIdx]

                if (neighborRoom == null) {
                    room.addComponent(neighborComp)
                    ScanUtils.rooms[neighborIdx] = room
                } else if (neighborRoom != room && neighborRoom.type != RoomType.ENTRANCE) {
                    ScanUtils.mergeRooms(neighborRoom, room)
                }
            }
        }
    }

    fun checkPlayerState() {
        val world = mc.level ?: return

        Dungeon.dungeonTeammates.forEach { player ->

            val entity = world.players().find { it.name.string == player.name }

            val entry = mc.connection?.getPlayerInfo(entity?.uuid ?: UUID(0, 0))
            val ping = entry?.latency ?: -1

            if (ping != -1 && entity != null) {
                player.inRender = true
                onPlayerMove(player, entity.x, entity.z, entity.yRot)
            } else {
                player.inRender = false
            }

            if (ping == -1) return@forEach
            val currRoom = player.currRoom ?: return@forEach

            if (currRoom != player.lastRoom) {
                player.lastRoom?.players?.remove(player)
                currRoom.players.add(player)
            }

            player.lastRoom = currRoom
        }
    }

    fun checkRoomState() {
        for (room in ScanUtils.rooms) {
            if (room == null || room.rotation != null) continue
            room.findRotation()
        }
    }

    fun checkDoorState() {
        for (door in ScanUtils.uniqueDoors) {
            if (door.opened) continue
            door.checkFairy()
            door.check()
        }
    }

    fun onPlayerMove(entity: DungeonPlayer?, x: Double, z: Double, yaw: Float) {
        if (entity == null) return
        entity.inRender = true


        val iconX = worldToMap(x, -200.0, -10.0, 0.0, ScanUtils.defaultMapSize.first.toDouble())
        val iconZ = worldToMap(z, -200.0, -10.0, 0.0, ScanUtils.defaultMapSize.second.toDouble())
        entity.pos.updatePosition(x, z, yaw + 180f, iconX, iconZ)

        if ( x in -200.0..-10.0 || z in -200.0..-10.0){
            val currRoom = ScanUtils.getRoomAt(x.toInt(), z.toInt())
            entity.currRoom = currRoom
        }
    }
}