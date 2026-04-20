package quoi.api.skyblock.dungeon.odonscanning

import com.google.common.reflect.TypeToken
import com.google.gson.GsonBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.DoorType
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomComponent
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomData
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomDataDeserializer
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Vec2i
import quoi.utils.WorldUtils.getBlockEntityList
import quoi.utils.WorldUtils.state
import quoi.utils.equalsOneOf
import kotlin.math.round

// https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/com/github/noamm9/utils/dungeons/map/handlers/DungeonScanner.kt
// https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/ScanUtils.kt
object ScanUtils {
    private const val START = -185

    private val roomList: Set<RoomData> = loadRoomData()
    val coreToRoomData: Map<Int, RoomData> =
        roomList.flatMap { room -> room.cores.map { core -> core to room } }.toMap()

    private val horizontals = Direction.entries.filter { it.axis.isHorizontal }
    private val mutableBlockPos = BlockPos.MutableBlockPos()
    private var lastRoomPos: Vec2i = Vec2i(0, 0)
    private var lastScanTime = 0L

    val grid = Array<Any?>(121) { null }
    private val uniqueRooms = mutableMapOf<String, OdonRoom>()

    var currentRoom: OdonRoom? = null
        private set
    val passedRooms: MutableSet<OdonRoom> = mutableSetOf()
    val scannedRooms: MutableSet<OdonRoom> = mutableSetOf()
    val scannedDoors: MutableSet<OdonDoor> = mutableSetOf()

    var mimicRoom: OdonRoom? = null
        private set

    private fun loadRoomData(): Set<RoomData> {
        return try {
            GsonBuilder()
                .registerTypeAdapter(RoomData::class.java, RoomDataDeserializer())
                .create().fromJson(
                    (ScanUtils::class.java.getResourceAsStream("/assets/quoi/odon_rooms.json")
                        ?: throw kotlinx.io.files.FileNotFoundException()).bufferedReader(),
                    object : TypeToken<Set<RoomData>>() {}.type
                )
        } catch (e: Exception) {
            logger.error("Error reading room data", e)
            setOf()
        }
    }

    fun init() {
        on<TickEvent.End> {
            if ((!Dungeon.inDungeons && !Location.currentArea.isArea(Island.SinglePlayer)) || Dungeon.inBoss) {
                currentRoom?.let { DungeonEvent.Room.Enter(null).post() }
                return@on
            } // We want the current room to register as null if we are not in a dungeon

            scanDungeon()

            scannedRooms.filter { it.rotation == Rotations.NONE }.forEach { room -> // suboptimal
                val comp = room.roomComponents.firstOrNull() ?: return@forEach
                val level = mc.level ?: return@forEach

                if (level.hasChunk(comp.x shr 4, comp.z shr 4)) {
                    val chunk = level.getChunk(comp.x shr 4, comp.z shr 4)
                    val height = getTopLayerOfRoom(Vec2i(comp.x, comp.z), chunk)

                    if (height > 0) {
                        updateRotation(room, height)
                    }
                }
            }

            if (mimicRoom == null && (Dungeon.floor?.floorNumber ?: -1) > 5) {
                scanMimic()
            }

            val pX = mc.player?.x?.toInt() ?: return@on
            val pZ = mc.player?.z?.toInt() ?: return@on

            val room = getRoomFromPos(pX, pZ)

            val gx = (round((pX - START) / 32.0).toInt() * 2).coerceIn(0, 10)
            val gz = (round((pZ - START) / 32.0).toInt() * 2).coerceIn(0, 10)

            if (gx == lastRoomPos.x && gz == lastRoomPos.z && !Location.currentArea.isArea(Island.SinglePlayer)) return@on
            lastRoomPos = Vec2i(gx, gz)

            if (room != currentRoom) {
                DungeonEvent.Room.Enter(room).post()
            }
        }

        on<DungeonEvent.Room.Enter> {
            currentRoom = room
            if (passedRooms.none { it.data.name == currentRoom?.data?.name }) passedRooms.add(currentRoom ?: return@on)
        }

        on<DungeonEvent.Room.Scan> {
            MapRenderer.refresh()
        }

        on<PacketEvent.Received> {
            if (packet is ClientboundMapItemDataPacket) mc.execute { MapRenderer.update(packet) }
        }

        on<WorldEvent.Chunk.Load> {
            MapRenderer.onChunkLoad()
        }

        on<WorldEvent.Change> {
            passedRooms.clear()
            scannedRooms.clear()
            scannedDoors.clear()
            uniqueRooms.clear()
            grid.fill(null)
            currentRoom = null
            mimicRoom = null
            lastRoomPos = Vec2i(0, 0)
            MapRenderer.reset()
        }
    }

    private fun scanDungeon() {
        if (System.currentTimeMillis() - lastScanTime < 250) return
        lastScanTime = System.currentTimeMillis()

        val level = mc.level ?: return

        for (x in 0..10) {
            for (z in 0..10) {
                val i = z * 11 + x
                if (grid[i] != null) continue

                val wx = START + x * 16
                val wz = START + z * 16

                if (!level.hasChunk(wx shr 4, wz shr 4)) continue

                val chunk = level.getChunk(wx shr 4, wz shr 4)
                val height = getTopLayerOfRoom(Vec2i(wx, wz), chunk)
                if (height <= 0) continue

                val tile = scanTile(wx, wz, x, z, height, chunk)
                if (tile != null) {
                    grid[i] = tile
                }
            }
        }
    }

    private fun scanTile(x: Int, z: Int, col: Int, row: Int, height: Int, chunk: LevelChunk): Any? {
        val rowEven = row % 2 == 0
        val colEven = col % 2 == 0

        return when {
            rowEven && colEven -> { // rooms
                if (height <= 0) return null
                val core = getCoreAtHeight(Vec2i(x, z), height, chunk)
                val data = coreToRoomData[core] ?: return null
                addToUnique(x, z, data, core, height, centre = true)
            }

            !rowEven && !colEven -> { // 2x2 centres
                val tile = grid[(row - 1) * 11 + col - 1] as? OdonRoom
                if (tile != null) {
                    addToUnique(x, z, tile.data, 0, height, centre = false)
                } else null
            }

            height in intArrayOf(73, 74, 81, 82) -> { // dors
                val block = chunk.level.getBlockState(mutableBlockPos.set(x, 69, z)).block
                val type = when (block) {
                    Blocks.COAL_BLOCK -> DoorType.WITHER
                    Blocks.RED_TERRACOTTA -> DoorType.BLOOD
                    Blocks.INFESTED_CHISELED_STONE_BRICKS -> DoorType.ENTRANCE
                    else -> DoorType.NORMAL
                }
                val door = OdonDoor(Vec2i(x, z), type)
                scannedDoors.add(door)
                door
            }

            else -> { // connection between big rooms
                val i = if (rowEven) row * 11 + (col - 1) else (row - 1) * 11 + col
                if (i in 0..120) {
                    val tile = grid[i]
                    if (tile is OdonRoom) {
                        if (tile.data.type == RoomType.ENTRANCE) {
                            val door = OdonDoor(Vec2i(x, z), DoorType.NORMAL)
                            scannedDoors.add(door)
                            door
                        } else {
                            addToUnique(x, z, tile.data, 0, height, centre = false)
                        }
                    } else null
                } else null
            }
        }
    }

    private fun addToUnique(x: Int, z: Int, data: RoomData, core: Int, height: Int, centre: Boolean): OdonRoom {
        val roomName = data.name
        var room = uniqueRooms[roomName]

        if (room == null) {
            room = OdonRoom(data = data, roomComponents = mutableSetOf())
            uniqueRooms[roomName] = room
            scannedRooms.add(room)
        }

        if (centre) {
            room.roomComponents.add(RoomComponent(x, z, core))
        }

        if (room.rotation == Rotations.NONE) {
            updateRotation(room, height)
        }

        DungeonEvent.Room.Scan(room).post()
        return room
    }

    fun scanMimic() { // untested
        val chest = getBlockEntityList().find { pos ->
            if (!pos.state.`is`(Blocks.TRAPPED_CHEST)) return@find false

            val room = getRoomFromPos(pos.x, pos.z)
            room != null && room.data.type != RoomType.TRAP
        } ?: return

        mimicRoom = getRoomFromPos(chest.x, chest.z)
    }

    fun getRoomFromPos(x: Int, z: Int): OdonRoom? {
        val gx = (round((x - START) / 32.0).toInt() * 2).coerceIn(0, 10)
        val gz = (round((z - START) / 32.0).toInt() * 2).coerceIn(0, 10)

        return grid[gz * 11 + gx] as? OdonRoom
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

//        if (room.rotation == Rotations.NONE) modMessage("${room.name} ROT NONE")
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
}