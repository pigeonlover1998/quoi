package quoi.api.skyblock.dungeon.odonscanning.tiles

import quoi.api.colour.Colour
import quoi.utils.Vec2i
import quoi.utils.equalsOneOf

data class OdonDoor(val pos: Vec2i, var type: Type) {
    enum class Type { BLOOD, NORMAL, WITHER }

    var locked = type.equalsOneOf(Type.WITHER, Type.BLOOD)

    val size: Vec2i get() {
        val xOffset = ((pos.x + 185) shr 4) % 2
        val zOffset = ((pos.z + 185) shr 4) % 2
        return Vec2i(
            (xOffset xor 1) * 4 + xOffset * 4,
            (zOffset xor 1) * 4 + zOffset * 4
        )
    }

    val placement: Vec2i get() {
        val x = (pos.x + 185) shr 4
        val z = (pos.z + 185) shr 4
        val xEven = x % 2
        val zEven = z % 2
        val thicknessOffset = (16 - 4) / 2
        val xPos = (x shr 1) * 20 + xEven * 16 + (xEven xor 1) * thicknessOffset
        val zPos = (z shr 1) * 20 + zEven * 16 + (zEven xor 1) * thicknessOffset
        return Vec2i(xPos, zPos)
    }


    val colour: Colour get() = when (type) {
        Type.BLOOD  -> Colour.RED
        Type.WITHER if (locked) -> Colour.GREEN
        else -> Colour.ORANGE
    }

    fun updateState(col: Int) {
        locked = when (col) {
            119 -> {
                type = Type.WITHER
                true
            }
            85 -> true
            82 -> false
            18 -> {
                type = Type.BLOOD
                true
            }
            else -> locked
        }
    }
}