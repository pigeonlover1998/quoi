package quoi.api.skyblock.dungeon.components

import quoi.api.skyblock.dungeon.Checkmark
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.map.RoomMetadata
import quoi.api.skyblock.dungeon.map.RoomRegistry
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import quoi.api.skyblock.dungeon.map.utils.WorldScanUtils
import quoi.api.skyblock.dungeon.map.utils.WorldScanUtils.rotate
import quoi.api.skyblock.dungeon.map.utils.WorldScanUtils.unrotate
import quoi.utils.TimeUtils
import quoi.utils.WorldUtils
import quoi.utils.rotate
import quoi.utils.unrotate
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

// https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/map/Room.kt
class Room(
    initialComponent: Pair<Int, Int>,
    var height: Int? = null
) {
    val components = mutableListOf<Pair<Int, Int>>()
    val realComponents = mutableListOf<Pair<Int, Int>>()
    val cores = mutableSetOf<Int>()
    val players = mutableSetOf<DungeonPlayer>()

    var explored = false
    var checkmark = Checkmark.UNDISCOVERED
    var clearTime = TimeUtils.zero

    var roomData: RoomMetadata? = null
    var name: String = "Unknown"
    var type: RoomType = RoomType.UNKNOWN
    var shape: String = "1x1"
    var secrets: Int = 0
    var secretsFound: Int = 0
    var crypts: Int = 0

    var corner: BlockPos? = null
    var rotation: Int? = null

    init {
        addComponents(listOf(initialComponent))
    }

    fun addComponent(comp: Pair<Int, Int>, update: Boolean = true) = apply {
        if (comp !in components) {
            components += comp
            if (update) update()
        }
    }

    fun addComponents(comps: Iterable<Pair<Int, Int>>) = apply {
        var changed = false
        for (comp in comps) {
            if (comp !in components) {
                components += comp
                changed = true
            }
        }
        if (changed) update()
    }

    fun hasComponent(x: Int, z: Int): Boolean {
        return components.any { it.first == x && it.second == z }
    }

    fun update() {
        components.sortWith(compareBy({ it.first }, { it.second }))
        realComponents.clear()
        realComponents += components.map { WorldScanUtils.componentToRealCoords(it.first, it.second) }
        scan()
        shape = WorldScanUtils.getRoomShape(components)
        corner = null
        rotation = null
    }

    fun scan() = apply {
        for ((x, z) in realComponents) {
            if (height == null) height = WorldScanUtils.getHighestY(x, z)
            val core = WorldScanUtils.getCore(x, z)
            if (cores.add(core)) {
                loadFromCore(core)
            }
        }
    }

    private fun loadFromCore(core: Int): Boolean {
        val data = RoomRegistry.getByCore(core) ?: return false
        loadFromData(data)
        return true
    }

    fun loadFromData(data: RoomMetadata) {
        roomData = data
        name = data.name
        type = ScanUtils.roomTypeMap[data.type.lowercase()] ?: RoomType.NORMAL
        secrets = data.secrets
        crypts = data.crypts

        if (type == RoomType.ENTRANCE) explored = true
    }

    fun loadFromMapColor(color: Byte): Room {
        type = ScanUtils.mapColorToRoomType[color.toInt()] ?: RoomType.UNKNOWN
        when (type) {
            RoomType.BLOOD -> RoomRegistry.getAll().find { it.name == "Blood" }?.let(::loadFromData)
            RoomType.ENTRANCE -> RoomRegistry.getAll().find { it.name == "Entrance" }?.let(::loadFromData)
            else -> {}
        }
        return this
    }

    fun findRotation() = apply {
        val currentHeight = height ?: return@apply

        if (type == RoomType.FAIRY) {
            rotation = 0
            val (x, z) = realComponents.first()
            corner = BlockPos(x - ScanUtils.HALF_ROOM, currentHeight, z - ScanUtils.HALF_ROOM)
            return@apply
        }

        for ((x, z) in realComponents) {
            for ((jdx, offset) in ROTATION_OFFSETS.withIndex()) {
                val nx = x + offset.first
                val nz = z + offset.second

                if (!WorldScanUtils.isChunkLoaded(nx, currentHeight, nz)) continue

                val state = WorldUtils.getBlockStateAt(nx, currentHeight, nz) ?: continue
                if (state.`is`(Blocks.BLUE_TERRACOTTA)) {
                    rotation = jdx * 90
                    corner = BlockPos(nx, currentHeight, nz)
                    return@apply
                }
            }
        }
    }

    fun centre(): Pair<Double, Double> {
        if (components.isEmpty()) return Pair(0.0, 0.0)

        val minX = components.minOf { it.first }
        val minZ = components.minOf { it.second }
        val maxX = components.maxOf { it.first }
        val maxZ = components.maxOf { it.second }

        val width = maxX - minX
        val depth = maxZ - minZ

        var centerZ = minZ + depth / 2.0
        if (shape == "L") {
            val topEdgeCount = components.count { it.second == minZ }
            centerZ += if (topEdgeCount == 2) -depth / 2.0 else depth / 2.0
        }

        return Pair(minX + width / 2.0, centerZ)
    }

    private val currentRotation get() = rotation ?: 0
    private val rotationNumber get() = currentRotation / 90

    private val cornerDx get() = corner?.x ?: 0
    private val cornerDz get() = corner?.z ?: 0
    private val cornerDxDouble get() = cornerDx.toDouble()
    private val cornerDzDouble get() = cornerDz.toDouble()

    fun getRelativeCoords(pos: BlockPos) = pos.subtract(Vec3i(cornerDx, 0, cornerDz)).rotate(currentRotation)
    fun getRelativeCoords(vec: Vec3) = vec.subtract(cornerDxDouble, 0.0, cornerDzDouble).rotate(currentRotation)

    fun getRealCoords(local: BlockPos) = local.unrotate(currentRotation).offset(Vec3i(cornerDx, 0, cornerDz))
    fun getRealCoords(vec: Vec3) = vec.unrotate(currentRotation).add(cornerDxDouble, 0.0, cornerDzDouble)

    fun getRelativeYaw(yaw: Float) = wrapDegrees(yaw + rotationNumber * 90f)
    fun getRealYaw(yaw: Float) = wrapDegrees(yaw - rotationNumber * 90f)

    companion object {
        private val ROTATION_OFFSETS = listOf(
            Pair(-ScanUtils.HALF_ROOM, -ScanUtils.HALF_ROOM),
            Pair(ScanUtils.HALF_ROOM, -ScanUtils.HALF_ROOM),
            Pair(ScanUtils.HALF_ROOM, ScanUtils.HALF_ROOM),
            Pair(-ScanUtils.HALF_ROOM, ScanUtils.HALF_ROOM)
        )
    }
}

data class DiscoveredRoom(val x: Int, val z: Int, val room: Room)