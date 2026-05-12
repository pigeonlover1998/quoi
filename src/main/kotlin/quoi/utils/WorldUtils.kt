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
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import quoi.utils.skyblock.item.TeleportUtils.BLOCKS_FEET
import quoi.utils.skyblock.item.TeleportUtils.PASSABLE
import quoi.utils.skyblock.item.TeleportUtils.blockFlags
import net.minecraft.core.Direction as McDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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
//        get() = mc.level?.getBlockCollisions(mc.player!!, this.aabb)?.none() == true

    inline val BlockPos.etherwarpable: Boolean
        get() {
            val level = mc.level ?: return false
            val state = level.getBlockState(this)
            if ((blockFlags[Block.getId(state)] and PASSABLE) != 0) return false

            val collisionTop = state.getCollisionShape(level, this).max(McDirection.Axis.Y)
            val feetY = this.y + max(1.0, ceil(collisionTop)).toInt()

            val feetFlags = blockFlags[Block.getId(level.getBlockState(BlockPos(this.x, feetY, this.z)))]
            if ((feetFlags and PASSABLE) == 0 || (feetFlags and BLOCKS_FEET) != 0) return false

            val headFlags = blockFlags[Block.getId(level.getBlockState(BlockPos(this.x, feetY + 1, this.z)))]
            return !((headFlags and PASSABLE) == 0 || (headFlags and BLOCKS_FEET) != 0)
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

    fun getBlockEntityList(): List<BlockPos> {
        val player = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        val renderDistance = mc.options.renderDistance().get()
        val pX = player.chunkPosition().x
        val pZ = player.chunkPosition().z

        return buildList {
            for (x in (pX - renderDistance) .. (pX + renderDistance)) {
                for (z in (pZ - renderDistance) .. (pZ + renderDistance)) {
                    val chunk = level.getChunk(x, z, ChunkStatus.FULL, false) ?: continue
                    addAll(chunk.blockEntitiesPos)
                }
            }
        }
    }

    inline fun Vec3.nearbyBlocks(radius: Float, predicate: (BlockPos) -> Boolean = { true }): List<BlockPos> {
        val res = mutableListOf<BlockPos>()
        val mut = BlockPos.MutableBlockPos()

        CachedSphere.forEachInRadius(
            floor(this.x).toInt(),
            floor(this.y).toInt(),
            floor(this.z).toInt(),
            radius
        ) { x, y, z ->
            mut.set(x, y, z)
            if (predicate(mut)) {
                res.add(mut.immutable())
            }
        }
        return res
    }

    inline fun BlockPos.nearbyBlocks(radius: Float, predicate: (BlockPos) -> Boolean = { true }): List<BlockPos> {
        val res = mutableListOf<BlockPos>()
        val mut = BlockPos.MutableBlockPos()

        CachedSphere.forEachInRadius(this.x, this.y, this.z, radius) { x, y, z ->
            mut.set(x, y, z)
            if (predicate(mut)) {
                res.add(mut.immutable())
            }
        }
        return res
    }
}