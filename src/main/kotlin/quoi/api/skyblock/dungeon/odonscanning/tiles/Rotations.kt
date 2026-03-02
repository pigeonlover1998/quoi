package quoi.api.skyblock.dungeon.odonscanning.tiles

enum class Rotations(
    val deg: Int,
    val x: Int,
    val z: Int
) {
    NORTH(0, 15, 15),
    SOUTH(180, -15, -15),
    WEST(90, 15, -15),
    EAST(270, -15, 15),
    NONE(0, 0, 0);
}