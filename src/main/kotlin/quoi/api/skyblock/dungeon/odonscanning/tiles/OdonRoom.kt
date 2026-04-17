package quoi.api.skyblock.dungeon.odonscanning.tiles

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.multiply
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
        if (roomComponents.size == 3) return roomComponents.elementAt(1).placement

        if (rotation == Rotations.NONE) return roomComponents.minBy { it.x * 1000 + it.z }.placement
        val placements = roomComponents.map { it.placement }

        val x = (placements.minOf { it.x } + placements.maxOf { it.x }) / 2
        val z = (placements.minOf { it.z } + placements.maxOf { it.z }) / 2

        return Vec2i(x, z)
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
            else -> RoomState.DISCOVERED
        }

        if (new.ordinal < data.state.ordinal) {
            data.state = new
        }
    }
}

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = Vec2i(x, z)
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
        val col = when (type) {
            RoomType.ENTRANCE -> Colour.MINECRAFT_DARK_GREEN
            RoomType.BLOOD    -> Colour.MINECRAFT_RED
            RoomType.FAIRY    -> Colour.MINECRAFT_LIGHT_PURPLE
            RoomType.PUZZLE   -> Colour.MINECRAFT_DARK_PURPLE
            RoomType.TRAP     -> Colour.MINECRAFT_GOLD
            RoomType.CHAMPION -> Colour.MINECRAFT_YELLOW
            else              -> Colour.RGB(107, 58, 17)
        }

        return if (state.equalsOneOf(RoomState.UNDISCOVERED, RoomState.UNOPENED)) {
            Colour.RGB(col.rgb.multiply(0.5f))
        } else col
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