package quoi.api.skyblock.dungeon.components

import quoi.api.skyblock.dungeon.DoorState
import quoi.api.skyblock.dungeon.DoorType
import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import quoi.api.skyblock.dungeon.map.utils.WorldScanUtils
import quoi.utils.WorldUtils

/**
 * from Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/map/Door.kt
 */
class Door(val worldPos: Pair<Int, Int>, val componentPos: Pair<Int, Int>) {

    var opened: Boolean = false
    var fairy: Boolean = false
    var rotation: Int? = null
    var type: DoorType = DoorType.NORMAL
    var state = DoorState.UNDISCOVERED

    fun getPos(): Triple<Int, Int, Int> {
        return Triple(worldPos.first, 69, worldPos.second)
    }

    init {
        if (worldPos.first != 0 && worldPos.second != 0) {
            checkType()
        }
    }

    fun check() {
        if (fairy) return

        val (x, y, z) = getPos()
        if (!WorldScanUtils.isChunkLoaded(x, y, z)) return

        val id = WorldUtils.getBlockNumericId(x, y, z)
        opened = (id == 0)
    }

    fun getCanidates(): List<Int> {
        val (cx, cz) = componentPos
        val candidates: List<Pair<Int, Int>> =
            if (cx % 2 == 1) listOf((cx - 1) / 2 to cz / 2, (cx + 1) / 2 to cz / 2)
            else listOf(cx / 2 to (cz - 1) / 2, cx / 2 to (cz + 1) / 2)

        return candidates
            .map { (rx, rz) -> 6 * rz + rx }
            .filter { it in 0..35 }
    }

    fun checkFairy() {
        fairy = getCanidates().any { idx ->
            val room = ScanUtils.getRoomAtIdx(idx)
            room != null && room.type == RoomType.FAIRY && !room.explored
        }
    }

    private fun checkType() {
        val (x, y, z) = getPos()
        if (!WorldScanUtils.isChunkLoaded(x, y, z)) return

        val id = WorldUtils.getBlockNumericId(x, y, z)

        if (id == 0 || id == 166) return

        type = when (id) {
            97  -> DoorType.ENTRANCE
            173 -> DoorType.WITHER
            159 -> DoorType.BLOOD
            else -> DoorType.NORMAL
        }

        opened = false
    }
}