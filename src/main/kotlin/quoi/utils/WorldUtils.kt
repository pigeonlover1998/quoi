package quoi.utils

import quoi.QuoiMod.mc
import quoi.api.skyblock.dungeon.map.utils.LegacyIdMapper
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * modified Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/WorldUtils.kt
 */
object WorldUtils {
    val world: ClientLevel? get() = mc.level

    val BlockPos.state: BlockState? get() = world?.getBlockState(this)

    fun getBlockStateAt(x: Int, y: Int, z: Int) = mc.level?.getBlockState(BlockPos(x, y, z))

    fun getBlockNumericId(x: Int, y: Int, z: Int): Int {
        val state = getBlockStateAt(x, y, z)?: return -1
        return LegacyIdMapper.getId(state)
    }

    fun checkIfAir(x: Int, y: Int, z: Int): Int {
        val state = getBlockStateAt(x, y, z)?: return -1
        if (state.isAir) return 0

        return LegacyIdMapper.getId(state)
    }

    val Block.registryName: String get() {
        val registry = BuiltInRegistries.BLOCK.getKey(this)
        return "${registry.namespace}:${registry.path}"
    }

    fun worldToMap(n: Number, inMin: Number, inMax: Number, outMin: Number, outMax: Number): Double = (n.toDouble() - inMin.toDouble()) * (outMax.toDouble() - outMin.toDouble()) / (inMax.toDouble() - inMin.toDouble()) + outMin.toDouble()

    private val tabListComparator: Comparator<PlayerInfo> = compareBy(
        { it.gameMode == GameType.SPECTATOR },
        { it.team?.name ?: "" },
        { it.profile.name.lowercase() }
    )

    @JvmStatic
    val tablist: List<PlayerInfo>
        get() = mc.connection
            ?.listedOnlinePlayers
            ?.sortedWith(tabListComparator) ?: emptyList()

    @JvmStatic
    val players: List<PlayerInfo>
        get() = tablist.filter { it.profile.id.version() == 4 }

    val ClientLevel.day get() = this.dayTime / 24000
}