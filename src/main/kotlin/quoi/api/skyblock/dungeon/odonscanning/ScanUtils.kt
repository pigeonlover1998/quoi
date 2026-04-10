package quoi.api.skyblock.dungeon.odonscanning

import com.google.common.reflect.TypeToken
import com.google.gson.GsonBuilder
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.events.DungeonEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomComponent
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomData
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomDataDeserializer
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.utils.Vec2i
import quoi.utils.equalsOneOf

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/ScanUtils.kt
 */
object ScanUtils {
    private const val ROOM_SIZE_SHIFT = 5  // Since ROOM_SIZE = 32 (2^5) so we can perform bitwise operations
    private const val START = -185

    private val roomList: Set<RoomData> = loadRoomData()
    val coreToRoomData: Map<Int, RoomData> =
        roomList.flatMap { room -> room.cores.map { core -> core to room } }.toMap()

    private val horizontals = Direction.entries.filter { it.axis.isHorizontal }
    private val mutableBlockPos = BlockPos.MutableBlockPos()
    private var lastRoomPos: Vec2i = Vec2i(0, 0)

    var currentRoom: OdonRoom? = null
        private set
    var passedRooms: MutableSet<OdonRoom> = mutableSetOf()
        private set
    var scannedRooms: MutableSet<OdonRoom> = mutableSetOf()
        private set

    private fun loadRoomData(): Set<RoomData> {
        return try {
            GsonBuilder()
                .registerTypeAdapter(
                    RoomData::class.java,
                    RoomDataDeserializer()
                )
                .create().fromJson(
                    (ScanUtils::class.java.getResourceAsStream("/assets/quoi/odon_rooms.json")
                        ?: throw kotlinx.io.files.FileNotFoundException()).bufferedReader(),
                    object : TypeToken<Set<RoomData>>() {}.type
                )
        } catch (e: Exception) {
            logger.error("Error reading room data", e)
            println(e.message)
            setOf()
        }
    }

    fun init() {
        on<TickEvent.End> {
            if ((!Dungeon.inDungeons && !Location.currentArea.isArea(Island.SinglePlayer)) || Dungeon.inBoss) {
                currentRoom?.let { DungeonEvent.Room.Enter(null).post() }
                return@on
            } // We want the current room to register as null if we are not in a dungeon

            scanAllRooms()

            val roomCenter = getRoomCenter(mc.player?.x?.toInt() ?: return@on, mc.player?.z?.toInt() ?: return@on)
            if (roomCenter == lastRoomPos && Location.currentArea.isArea(Island.SinglePlayer)) return@on // extra SinglePlayer caching for invalid placed rooms
            lastRoomPos = roomCenter

            passedRooms.find { previousRoom -> previousRoom.roomComponents.any { it.vec2 == roomCenter } }?.let { room ->
                if (currentRoom?.roomComponents?.none { it.vec2 == roomCenter } == true) DungeonEvent.Room.Enter(room).post()
                return@on
            } // We want to use cached rooms instead of scanning it again if we have already passed through it and if we are already in it, we don't want to trigger the event

            scanRoom(roomCenter)?.let { room -> if (room.rotation != Rotations.NONE) DungeonEvent.Room.Enter(room).post() } ?: run {
                if ((!Dungeon.inClear) && !Location.currentArea.isArea(Island.SinglePlayer)) return@on
//                devMessage("Unable to determine room at $roomCenter core: ${getCore(roomCenter)}")
            }
        }

        on<DungeonEvent.Room.Enter> {
            currentRoom = room
            if (passedRooms.none { it.data.name == currentRoom?.data?.name }) passedRooms.add(currentRoom ?: return@on)
//            devMessage("${room?.data?.name} - ${room?.rotation} || clay: ${room?.clayPos}")
        }

        on<WorldEvent.Change> {
            passedRooms.clear()
            scannedRooms.clear()
            currentRoom = null
            lastRoomPos = Vec2i(0, 0)
        }
    }

    fun updateRotation(room: OdonRoom, roomHeight: Int) {
        if (room.data.name == "Fairy") { // Fairy room doesn't have a clay block so we need to set it manually
            room.clayPos = room.roomComponents.firstOrNull()?.let { BlockPos(it.x - 15, roomHeight, it.z - 15) } ?: return
            room.rotation = Rotations.SOUTH
            return
        }

        val level = mc.level ?: return
        room.rotation = Rotations.entries.dropLast(1).find { rotation ->
            room.roomComponents.any { component ->
                BlockPos(component.x + rotation.x, roomHeight, component.z + rotation.z).let { blockPos ->
                    level.getBlockState(blockPos)?.block == Blocks.BLUE_TERRACOTTA && (room.roomComponents.size == 1 || horizontals.all { facing ->
                        level.getBlockState(
                            blockPos.offset((if (facing.axis == Direction.Axis.X) facing.stepX else 0), 0, (if (facing.axis == Direction.Axis.Z) facing.stepZ else 0))
                        )?.block?.equalsOneOf(Blocks.AIR, Blocks.BLUE_TERRACOTTA) == true
                    }).also { isCorrectClay -> if (isCorrectClay) room.clayPos = blockPos }
                }
            }
        } ?: Rotations.NONE // Rotation isn't found if we can't find the clay block
    }

    private fun scanAllRooms() {
        for (x in 0..5) {
            for (z in 0..5) {
                val x = START + (x shl ROOM_SIZE_SHIFT)
                val z = START + (z shl ROOM_SIZE_SHIFT)
                val centre = Vec2i(x, z)

                if (scannedRooms.any { room -> room.roomComponents.any { it.vec2 == centre } }) continue

                val room = scanRoom(centre) ?: continue

                if (room.rotation != Rotations.NONE) {
                    scannedRooms.add(room)
                    DungeonEvent.Room.Scan(room).post()
                }
            }
        }
    }

    fun scanRoom(vec2: Vec2i): OdonRoom? {
        val level = mc.level ?: return null
        val chunk = level.getChunk(vec2.x shr 4, vec2.z shr 4)
        val roomHeight = getTopLayerOfRoom(vec2, chunk)
        return getCoreAtHeight(vec2, roomHeight, chunk).let { core ->
            coreToRoomData[core]?.let { roomData ->
                OdonRoom(data = roomData, roomComponents = findRoomComponentsRecursively(vec2, roomData.cores, roomHeight, level))
            }?.apply { updateRotation(this, roomHeight) }
        }
    }

    private fun findRoomComponentsRecursively(vec2: Vec2i, cores: List<Int>, roomHeight: Int, level: ClientLevel, visited: MutableSet<Vec2i> = mutableSetOf(), tiles: MutableSet<RoomComponent> = mutableSetOf()): MutableSet<RoomComponent> {
        if (vec2 in visited) return tiles else visited.add(vec2)

        val chunk = level.getChunk(vec2.x shr 4, vec2.z shr 4)
        val core = getCoreAtHeight(vec2, roomHeight, chunk)
        if (core !in cores) return tiles

        tiles.add(RoomComponent(vec2.x, vec2.z, core))
        horizontals.forEach { facing ->
            findRoomComponentsRecursively(
                Vec2i(
                    vec2.x + ((if (facing.axis == Direction.Axis.X) facing.stepX else 0) shl ROOM_SIZE_SHIFT),
                    vec2.z + ((if (facing.axis == Direction.Axis.Z) facing.stepZ else 0) shl ROOM_SIZE_SHIFT)
                ), cores, roomHeight, level, visited, tiles
            )
        }
        return tiles
    }

    fun getRoomCenter(posX: Int, posZ: Int): Vec2i {
        val roomX = (posX - START + (1 shl (ROOM_SIZE_SHIFT - 1))) shr ROOM_SIZE_SHIFT
        val roomZ = (posZ - START + (1 shl (ROOM_SIZE_SHIFT - 1))) shr ROOM_SIZE_SHIFT
        return Vec2i(((roomX shl ROOM_SIZE_SHIFT) + START), ((roomZ shl ROOM_SIZE_SHIFT) + START))
    }

    fun getCore(vec2: Vec2i): Int {
        val level = mc.level ?: return 0
        val chunk = level.getChunk(vec2.x shr 4, vec2.z shr 4)
        return getCoreAtHeight(vec2, getTopLayerOfRoom(vec2, chunk), chunk)
    }

    private fun getCoreAtHeight(vec2: Vec2i, roomHeight: Int, chunk: LevelChunk): Int {
        val sb = StringBuilder(150)
        val clampedHeight = roomHeight.coerceIn(11..140)
        sb.append(CharArray(140 - clampedHeight) { '0' })
        var bedrock = 0

        for (y in clampedHeight downTo 12) {
            mutableBlockPos.set(vec2.x, y, vec2.z)
            val block = chunk.getBlockState(mutableBlockPos)?.block
            if (block == Blocks.AIR && bedrock >= 2 && y < 69) {
                sb.append(CharArray(y - 11) { '0' })
                break
            }

            if (block == Blocks.BEDROCK) bedrock++
            else {
                bedrock = 0
                if (block.equalsOneOf(Blocks.OAK_PLANKS, Blocks.TRAPPED_CHEST, Blocks.CHEST)) continue
            }
            sb.append(block)
        }
        return sb.toString().hashCode()
    }

    fun getTopLayerOfRoom(vec2: Vec2i, chunk: LevelChunk): Int {
        for (y in 160 downTo 12) {
            mutableBlockPos.set(vec2.x, y, vec2.z)
            val blockState = chunk.getBlockState(mutableBlockPos)
            if (blockState?.isAir == false) return if (blockState.block == Blocks.GOLD_BLOCK) y - 1 else y
        }
        return 0
    }

    /*
    if (false) HeightMap.getHeight(vec2.x and 15, vec2.z and 15)
    else {
        val chunk = mc.world?.getChunk(ChunkSectionPos.getSectionCoord(vec2.x), ChunkSectionPos.getSectionCoord(vec2.z)) ?: return 0
        chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE).get(vec2.x and 15, vec2.z and 15).coerceIn(11..140) - 1
    }*/
}