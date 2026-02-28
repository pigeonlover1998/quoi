package quoi.api.skyblock.dungeon.map.utils

import quoi.QuoiMod.mc
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.map.MapItemScanner
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

/**
 * modified Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/utils/MapUtils.kt
 */
object MapItemUtils {
    const val MAP_WIDTH = 128
    const val MAP_LIMIT = 118

    const val COLOUR_EMPTY: Byte = 0
    const val COLOUR_BLOOD: Byte = 18
    const val COLOUR_ENTRANCE: Byte = 30
    const val COLOUR_WHITE: Byte = 34
    const val COLOUR_UNEXPLORED: Byte = 85
    const val COLOUR_EXPLORED: Byte = 119

    private const val ROOM_SIZE = 15
    private const val MAP_ID_MASK = 1000

    val MapDecoration.mapX get() = (this.x() + 128) shr 1
    val MapDecoration.mapZ get() = (this.y() + 128) shr 1
    val MapDecoration.yaw get() = this.rot * 22.5f

    var mapCorners = Pair(5, 5)
    var mapRoomSize = 16
    var mapGapSize = 0
    var coordMultiplier = 0.625
    var calibrated = false

    var mapData: MapItemSavedData? = null
    var guessMapData: MapItemSavedData? = null

    fun init() {
        EventBus.on<PacketEvent.Received> {
            if (!Dungeon.inDungeons || mapData != null) return@on
            val packet = packet as? ClientboundMapItemDataPacket ?: return@on
            val world = mc.level ?: return@on

            if (packet.mapId.id and MAP_ID_MASK != 0) return@on

            val guess = MapItem.getSavedData(packet.mapId, world) ?: return@on

            for (decoration in guess.decorations) {
                if (decoration.type == MapDecorationTypes.FRAME) {
                    guessMapData = guess
                    break
                }
            }
        }

        EventBus.on<TickEvent.Start> {
            if (!Dungeon.inDungeons) return@on
            if (!calibrated) {
                if (mapData == null) {
                    mapData = getCurrentMapState()
                }

                calibrated = calibrateDungeonMap()
            } else if (!Dungeon.inBoss) {
                val dataToScan = mapData ?: guessMapData ?: return@on
                MapItemScanner.updatePlayers(dataToScan)
                MapItemScanner.scan(dataToScan)
            }
        }
    }

    fun getCurrentMapState(): MapItemSavedData? {
        val stack = mc.player?.inventory?.getItem(8) ?: return null
        if (stack.item !is MapItem || !stack.hoverName.string.contains("Magical Map")) return null
        return MapItem.getSavedData(stack, mc.level)
    }

    fun calibrateDungeonMap(): Boolean {
        val mapState = getCurrentMapState() ?: return false
        val entranceInfo = findEntranceCorner(mapState.colors) ?: return false

        val (startIndex, size) = entranceInfo
        mapRoomSize = size
        mapGapSize = mapRoomSize + 4 // compute gap size from room width

        var x = (startIndex % MAP_WIDTH) % mapGapSize
        var z = (startIndex / MAP_WIDTH) % mapGapSize

        val floor = Dungeon.floor?.floorNumber ?: return false
        if (floor == 0 || floor == 1) x += mapGapSize
        if (floor == 0) z += mapGapSize

        mapCorners = x to z
        coordMultiplier = mapGapSize / ScanUtils.COMBINED_SIZE.toDouble()

        return true
    }

    fun findEntranceCorner(colors: ByteArray): Pair<Int, Int>? {
        val verticalOffset = MAP_WIDTH * ROOM_SIZE
        val searchLimit = colors.size - verticalOffset

        for (i in 0 until searchLimit) {
            if (colors[i] != COLOUR_ENTRANCE) continue

            // Check horizontal 15-block chain
            if (colors[i + ROOM_SIZE] == COLOUR_ENTRANCE) {
                // Check vertical 15-block chain
                if (colors[i + verticalOffset] == COLOUR_ENTRANCE) {
                    var length = 0
                    while (i + length < colors.size && colors[i + length] == COLOUR_ENTRANCE) {
                        length++
                    }
                    return Pair(i, length)
                }
            }
        }
        return null
    }

    fun reset() {
        mapCorners = Pair(5, 5)
        mapRoomSize = 16
        mapGapSize = 0
        coordMultiplier = 0.625
        calibrated = false
        mapData = null
        guessMapData = null
    }
}