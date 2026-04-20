package quoi.api.skyblock.dungeon.odonscanning.tiles

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.mix
import quoi.api.colour.multiply
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.module.impl.dungeon.DungeonMap
import quoi.utils.Vec2i
import quoi.utils.equalsOneOf
import quoi.utils.rotateAroundNorth
import quoi.utils.rotateToNorth
import java.lang.reflect.Type

data class OdonRoom(
    var rotation: Rotations = Rotations.NONE,
    var data: RoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<RoomComponent>,
) {
    val name get() = data.name

    fun getRelativeCoords(pos: BlockPos) = pos.subtract(clayPos.atY(0)).rotateToNorth(rotation)
    fun getRelativeCoords(vec: Vec3) = vec.subtract(clayPos.x.toDouble(), 0.0, clayPos.z.toDouble()).rotateToNorth(rotation)

    fun getRealCoords(pos: BlockPos) = pos.rotateAroundNorth(rotation).offset(clayPos.x, 0, clayPos.z)
    fun getRealCoords(vec: Vec3) = vec.rotateAroundNorth(rotation).add(clayPos.x.toDouble(), 0.0, clayPos.z.toDouble())

    fun getRelativeYaw(yaw: Float) = yaw + rotation.deg
    fun getRealYaw(yaw: Float) = wrapDegrees(yaw - rotation.deg)

    val textPlacement: Vec2i get() {
        if (roomComponents.isEmpty()) return Vec2i(0, 0)
        val placements = roomComponents.map { it.placement }

        if (placements.size == 3) {
            val horiz = placements.groupBy { it.z }.values.find { it.size == 2 }

            if (horiz != null) {
                val x = (horiz[0].x + horiz[1].x) / 2
                val z = horiz[0].z
                return Vec2i(x, z)
            }
        }
        val minX = placements.minOf { it.x }
        val maxX = placements.maxOf { it.x }
        val minZ = placements.minOf { it.z }
        val maxZ = placements.maxOf { it.z }

        return Vec2i((minX + maxX) / 2, (minZ + maxZ) / 2)
    }

    val textColour: Colour get() = when(data.state) {
        RoomState.CLEARED -> Colour.WHITE
        RoomState.GREEN -> Colour.MINECRAFT_GREEN
        RoomState.FAILED -> Colour.MINECRAFT_RED
        else -> Colour.MINECRAFT_GRAY
    }


    fun updateState(col: Int) {
        if (data.state == RoomState.GREEN && data.name == "Golden Oasis") return

        val new = when (col) {
            34 -> RoomState.CLEARED
            18 -> when (data.type) {
                RoomType.BLOOD -> RoomState.DISCOVERED
                RoomType.PUZZLE -> RoomState.FAILED
                else -> data.state
            }
            30 -> when (data.type) {
                RoomType.ENTRANCE -> RoomState.DISCOVERED
                else -> RoomState.GREEN
            }
            85, 119 -> RoomState.UNOPENED
            0 -> RoomState.UNDISCOVERED
            else -> RoomState.DISCOVERED
        }

        if (data.state != new) {
            data.state = new
        }
    }
}

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    var vec2 = Vec2i(0, 0)
    val blockPos = BlockPos(x, 70, z)

    val placement: Vec2i get() {
        val gridX = (x + 185) shr 5
        val gridZ = (z + 185) shr 5
        return Vec2i(gridX * 20, gridZ * 20)
    }

}

data class RoomData(
    val name: String, val type: RoomType, val cores: List<Int>,
    val crypts: Int, val secrets: Int, val trappedChests: Int, var state: RoomState = RoomState.UNDISCOVERED
) {
    val colour: Colour get() {
        val base = when (type) {
            RoomType.ENTRANCE -> DungeonMap.entranceRoom
            RoomType.BLOOD    -> DungeonMap.bloodRoom
            RoomType.FAIRY    -> DungeonMap.fairyRoom
            RoomType.PUZZLE   -> DungeonMap.puzzleRoom
            RoomType.TRAP     -> DungeonMap.trapRoom
            RoomType.CHAMPION -> DungeonMap.miniRoom
            RoomType.RARE     -> DungeonMap.rareRoom
            else              -> DungeonMap.normalRoom
        }

        val withMimic = if (this == ScanUtils.mimicRoom) base.mix(Colour.RED) else base

        return if (state.equalsOneOf(RoomState.UNDISCOVERED, RoomState.UNOPENED)) {
            Colour.RGB(withMimic.rgb.multiply(1f - DungeonMap.darkenMultiplier))
        } else withMimic
    }
}

class RoomDataDeserializer : JsonDeserializer<RoomData> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): RoomData {
        val jsonObject = json?.asJsonObject
        val name = jsonObject?.get("name")?.asString ?: ""
        val type = context?.deserialize(jsonObject?.get("type"), RoomType::class.java) ?: RoomType.NORMAL
        val coresType = object : TypeToken<List<Int>>() {}.type
        val cores = context?.deserialize<List<Int>>(jsonObject?.get("cores"), coresType).orEmpty()
        val crypts = jsonObject?.get("crypts")?.asInt ?: 0
        val secrets = jsonObject?.get("secrets")?.asInt ?: 0
        val trappedChests = jsonObject?.get("trappedChests")?.asInt ?: 0

        return RoomData(name, type, cores, crypts, secrets, trappedChests)
    }
}