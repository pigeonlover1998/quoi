package quoi.api.skyblock.dungeon.map

import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.components.Room
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
// https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/utils/RoomMetadata.kt
data class RoomMetadata(
    val name: String,
    val type: String,
    val shape: String? = null,
    val cores: List<Int>,
    val secretDetails: SecretDetails? = null,
    val secretCoords: SecretCoords? = null,
    val secrets: Int = 0,
    val crypts: Int = 0,
    val trappedChests: Int = 0,
    val reviveStones: Int = 0
) {
    data class SecretDetails(
        val redstoneKey: Int = 0,
        val wither: Int = 0,
        val bat: Int = 0,
        val item: Int = 0,
        val chest: Int = 0
    )

    data class SecretCoords(
        val redstoneKey: List<Coord> = emptyList(),
        val wither: List<Coord> = emptyList(),
        val bat: List<Coord> = emptyList(),
        val item: List<Coord> = emptyList(),
        val chest: List<Coord> = emptyList()
    ) {
//        fun toWaypoints(config: Config, room: Room): List<SecretWaypoint> {
//            val waypoints = mutableListOf<SecretWaypoint>()
//
//            fun addWaypoints(type: String, coords: List<Coord>, state: BlockState? = null) {
//                val colorKey = "secretWaypointColor.${type.lowercase().replace(" ", "")}"
//                val color by config.property<Color>(colorKey)
//
//                coords.forEach { coord ->
//                    waypoints += SecretWaypoint(
//                        label = type,
//                        color = color,
//                        position = coord,
//                        room = room,
//                        state = state,
//                    )
//                }
//            }
//
//            addWaypoints("Redstone Key", redstoneKey, Blocks.SKELETON_SKULL.defaultBlockState())
//            addWaypoints("Wither", wither, Blocks.SKELETON_SKULL.defaultBlockState())
//            addWaypoints("Chest", chest, Blocks.CHEST.defaultBlockState())
//            addWaypoints("Item", item, Blocks.SKELETON_SKULL.defaultBlockState())
//            addWaypoints("Bat", bat)
//
//            return waypoints
//        }
    }

    data class Coord(
        val x: Int,
        val y: Int,
        val z: Int,
        var collected: Boolean = false
    ) {
        fun toBlockPos(): BlockPos = BlockPos(x, y, z)
    }

    data class SecretWaypoint(
        val label: String,
        val colour: Colour,
        val position: Coord,
        val room: Room,
        val state: BlockState? = null
    ) {
//        fun render(context: RenderContext) {
//            if (position.collected) return
//            val pos = room.getRealCoord(position.toBlockPos())
//            val lineWidth = 3.0
//            Render3D.outlineBlock(context, pos, color, lineWidth, true, state)
//            Render3D.renderString(label, pos.center.x, pos.center.y, pos.center.z, phase = true, bgBox = true)
//        }
    }

}