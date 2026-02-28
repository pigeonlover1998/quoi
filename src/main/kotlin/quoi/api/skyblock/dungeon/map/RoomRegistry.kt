package quoi.api.skyblock.dungeon.map

import quoi.QuoiMod.logger
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.io.FileNotFoundException

/**
 * modified Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/skyblock/dungeons/utils/RoomRegistry.kt
 */
object RoomRegistry {
    private val byCore = mutableMapOf<Int, RoomMetadata>()
    private val allRooms = mutableListOf<RoomMetadata>()

    fun loadRooms() {
        runCatching {
            val rooms: List<RoomMetadata> = Gson().fromJson(
                (ScanUtils::class.java.getResourceAsStream("/assets/quoi/rooms.json")
                    ?: throw FileNotFoundException()).bufferedReader(),
                object : TypeToken<List<RoomMetadata>>() {}.type
            )
            populateRooms(rooms)
            logger.info("RoomRegistry: Loaded ${rooms.size} rooms from local config")
        }.onFailure {
            logger.info("RoomRegistry: Failed to load local room data — ${it.message}")
        }
    }

    private fun populateRooms(rooms: List<RoomMetadata>) {
        allRooms += rooms
        for (room in rooms) {
            for (core in room.cores) {
                byCore[core] = room
            }
        }
    }

    fun getByCore(core: Int): RoomMetadata? = byCore[core]
    fun getAll(): List<RoomMetadata> = allRooms
}