package quoi.api.skyblock.dungeon.odonscanning.tiles

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.phys.Vec3
import quoi.utils.Vec2
import quoi.utils.rotate
import quoi.utils.rotateAroundNorth
import quoi.utils.rotateToNorth
import quoi.utils.unrotate
import java.lang.reflect.Type

data class OdonRoom(
    var rotation: Rotations = Rotations.NONE,
    var data: RoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<RoomComponent>,
) {
    fun getRelativeCoords(pos: BlockPos) = pos.subtract(clayPos.atY(0)).rotateToNorth(rotation)
    fun getRelativeCoords(vec: Vec3) = vec.subtract(clayPos.x.toDouble(), 0.0, clayPos.z.toDouble()).rotateToNorth(rotation)

    fun getRealCoords(pos: BlockPos) = pos.rotateAroundNorth(rotation).offset(clayPos.x, 0, clayPos.z)
    fun getRealCoords(vec: Vec3) = vec.rotateAroundNorth(rotation).add(clayPos.x.toDouble(), 0.0, clayPos.z.toDouble())

    fun getRelativeYaw(yaw: Float) = yaw + rotation.deg
    fun getRealYaw(yaw: Float) = wrapDegrees(yaw - rotation.deg)
}

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = Vec2(x, z)
    val blockPos = BlockPos(x, 70, z)
}

data class RoomData(
    val name: String, val type: RoomType, val cores: List<Int>,
    val crypts: Int, val secrets: Int, val trappedChests: Int, /*val shape: RoomShape*/
)

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