package quoi.utils

import quoi.QuoiMod.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * modified Stella (LGPL-3.0) (c) Eclipse-5214
 * original: https://github.com/Eclipse-5214/stella/blob/main/src/main/kotlin/co/stellarskys/stella/utils/WorldUtils.kt
 */
object WorldUtils {
    inline val BlockPos.state: BlockState
        get() = mc.level?.getBlockState(this) ?: Blocks.AIR.defaultBlockState()

    inline val BlockPos.collisionShape: VoxelShape
        get() = mc.level?.let { this.state.getCollisionShape(it, this) } ?: Shapes.empty()

    inline val BlockPos.shape: VoxelShape
        get() = mc.level?.let { this.state.getShape(it, this) } ?: Shapes.empty()

    inline val BlockPos.solid: Boolean
        get() = !this.collisionShape.isEmpty

    inline val BlockPos.airLike: Boolean
        get() = /*!solid &&*/ state.block in BlockTypes.AirLike

    inline val BlockPos.walkable: Boolean
        get() = this.airLike && this.above().airLike

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