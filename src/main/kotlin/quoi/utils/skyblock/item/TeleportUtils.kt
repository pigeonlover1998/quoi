package quoi.utils.skyblock.item

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BigDripleafStemBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BubbleColumnBlock
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.ComparatorBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.DryVegetationBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.GrowingPlantBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LanternBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.SeagrassBlock
import net.minecraft.world.level.block.ShortDryGrassBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.SmallDripleafBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StemBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.TallGrassBlock
import net.minecraft.world.level.block.TallSeagrassBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.TripWireBlock
import net.minecraft.world.level.block.TripWireHookBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallSkullBlock
import net.minecraft.world.level.block.WebBlock
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.utils.BlockPos
import quoi.utils.Direction
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.getDirection
import quoi.utils.getLook
import quoi.utils.getVisiblePoint
import quoi.utils.vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// todo cleanup
object TeleportUtils {
    fun Vec3.getEtherPos(yaw: Float, pitch: Float, distance: Double = 61.0): EtherPos {
        val to = getLook(wrapDegrees(yaw), wrapDegrees(pitch)).scale(distance).add(this)
        return traverseVoxels(this, to, true)
    }

    /**
     * Returns a triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible.
     *
     * @param to The position to rotate to.
     * @return A triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible
     * @see quoi.utils.getDirection
     * @author Aton
     */
    fun getEtherwarpDirection(from: Vec3, to: BlockPos, dist: Double = 61.0): Direction? {
        if (from.distanceToSqr(to.vec3) > (dist + 2) * (dist + 2)) return null

        val visibleVec = getVisiblePoint(from, to) ?: return null

        return getDirection(from, visibleVec)
    }

    fun getEtherwarpDirection(to: BlockPos, dist: Double = 61.0) = getEtherwarpDirection(mc.player!!.eyePosition(true), to, dist)

    /**
     * based on noammaddons' InstantTransmissionHelper which is based on rsm's EtherUtils which is based on soshimee's zph algorithm
     * https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/com/github/noamm9/utils/items/InstantTransmissionHelper.kt#L99
     * https://github.com/rs-mod/rsm/blob/69e992bebc2840d1f49166685e393ec53a8e0312/src/main/java/com/ricedotwho/rsm/utils/EtherUtils.java#L438
     * https://discord.com/channels/1180667916560109588/1232500078585974874 // discord.gg/sby
     */
    fun predictTransmission(x0: Double, y0: Double, z0: Double, dx: Double, dy: Double, dz: Double, distance: Double): TeleportPos {
        var x = floor(x0)
        var y = floor(y0)
        var z = floor(z0)

        val rayX = dx * distance
        val rayY = dy * distance
        val rayZ = dz * distance

        val x1 = x0 + rayX
        val y1 = y0 + rayY
        val z1 = z0 + rayZ

        val endX = floor(x1)
        val endY = floor(y1)
        val endZ = floor(z1)

        val dirX = x1 - x0
        val dirY = y1 - y0
        val dirZ = z1 - z0

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val invRayX = if (rayX != 0.0) 1.0 / rayX else Double.MAX_VALUE
        val invRayY = if (rayY != 0.0) 1.0 / rayY else Double.MAX_VALUE
        val invRayZ = if (rayZ != 0.0) 1.0 / rayZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        var tMaxX = abs((x + max(stepX, 0) - x0) * invDirX)
        var tMaxY = abs((y + max(stepY, 0) - y0) * invDirY)
        var tMaxZ = abs((z + max(stepZ, 0) - z0) * invDirZ)

        val level = mc.level ?: return TeleportPos.NONE

        val mut = BlockPos.MutableBlockPos()

        var lastChunkX = Int.MIN_VALUE
        var lastChunkZ = Int.MIN_VALUE
        var chunk: LevelChunk? = null

        var lastX = x
        var lastY = y
        var lastZ = z
        var lastState: BlockState? = null
        var stepCount = 0

        repeat(1000) {
            mut.set(x, y, z)

            val cx = x.toInt() shr 4
            val cz = z.toInt() shr 4

            if (cx != lastChunkX || cz != lastChunkZ) {
                chunk = level.getChunk(cx, cz)
                lastChunkX = cx
                lastChunkZ = cz
            }

            val stateFeet = chunk?.getBlockState(mut) ?: return TeleportPos.NONE
            val hitFeet = checkBlockCollision(level, mut, stateFeet, x0, y0, z0, invRayX, invRayY, invRayZ)

            mut.set(x, y + 1.0, z)
            val stateHead = chunk.getBlockState(mut) ?: return TeleportPos.NONE
            val hitHead = checkBlockCollision(level, mut, stateHead, x0, y0, z0, invRayX, invRayY, invRayZ)

            if (hitFeet || hitHead) { // if hit block go back
                return if (stepCount == 0) TeleportPos(false, mut.immutable(), if (hitFeet) stateFeet else stateHead)
                else TeleportPos(true, BlockPos(lastX, lastY, lastZ), lastState)
            }

            if (x == endX && y == endY && z == endZ) return TeleportPos(true, BlockPos(x, y, z), stateFeet)

            lastX = x
            lastY = y
            lastZ = z
            lastState = stateFeet
            stepCount++

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    tMaxX += tDeltaX
                    x += stepX
                }
                tMaxY <= tMaxZ -> {
                    tMaxY += tDeltaY
                    y += stepY
                }
                else -> {
                    tMaxZ += tDeltaZ
                    z += stepZ
                }
            }
        }

        return TeleportPos.NONE
    }

    /**
     * Traverses voxels from start to end and returns the first non-air block it hits.
     * @author unclambomb6
     */
    fun traverseVoxels(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, etherwarp: Boolean): EtherPos {
        var x = floor(x0)
        var y = floor(y0)
        var z = floor(z0)

        val endX = floor(x1)
        val endY = floor(y1)
        val endZ = floor(z1)

        val dirX = x1 - x0
        val dirY = y1 - y0
        val dirZ = z1 - z0

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        var tMaxX = abs((x + max(stepX, 0) - x0) * invDirX)
        var tMaxY = abs((y + max(stepY, 0) - y0) * invDirY)
        var tMaxZ = abs((z + max(stepZ, 0) - z0) * invDirZ)

        val level = mc.level ?: return EtherPos.NONE

        val mut = BlockPos.MutableBlockPos()

        var lastChunkX = Int.MIN_VALUE
        var lastChunkZ = Int.MIN_VALUE
        var chunk: LevelChunk? = null

        repeat(1000) {
            mut.set(x, y, z)

            val cx = x.toInt() shr 4
            val cz = z.toInt() shr 4

            if (cx != lastChunkX || cz != lastChunkZ) {
                chunk = level.getChunk(cx, cz)
                lastChunkX = cx
                lastChunkZ = cz
            }

            val state = chunk?.getBlockState(mut) ?: return EtherPos.NONE
            val id = Block.getId(state)

            val isPassable = (blockFlags[id] and PASSABLE) != 0
            val isSolid = !isPassable

            if ((etherwarp && isSolid) || (!etherwarp && id != 0)) {
                val hitPos = mut.immutable()

                if (!etherwarp && isPassable) return EtherPos(false, hitPos, state)

                val collisionTop = state.getCollisionShape(level, hitPos).max(net.minecraft.core.Direction.Axis.Y)
                val clearanceBaseY = hitPos.y + max(1.0, ceil(collisionTop))

                mut.set(x, clearanceBaseY, z)

                val feetFlags = blockFlags[Block.getId(level.getBlockState(mut))]
                if ((feetFlags and PASSABLE) == 0 || (feetFlags and BLOCKS_FEET) != 0)
                    return EtherPos(false, hitPos, state)

                mut.set(x, clearanceBaseY + 1, z)

                val headFlags = blockFlags[Block.getId(level.getBlockState(mut))]
                if ((headFlags and PASSABLE) == 0 || (headFlags and BLOCKS_FEET) != 0)
                    return EtherPos(false, hitPos, state)

                return EtherPos(true, hitPos, state)
            }

            if (x == endX && y == endY && z == endZ) return EtherPos.NONE

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    tMaxX += tDeltaX
                    x += stepX
                }
                tMaxY <= tMaxZ -> {
                    tMaxY += tDeltaY
                    y += stepY
                }
                else -> {
                    tMaxZ += tDeltaZ
                    z += stepZ
                }
            }
        }

        return EtherPos.NONE
    }

    fun traverseVoxels(from: Vec3, to: Vec3, etherwarp: Boolean): EtherPos {
        val (x0, y0, z0) = from
        val (x1, y1, z1) = to
        return traverseVoxels(x0, y0, z0, x1, y1, z1, etherwarp)
    }

    data class TeleportPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
        val vec3: Vec3 by lazy { Vec3(pos ?: BlockPos.ZERO) }

        companion object {
            val NONE = TeleportPos(false, null, null)
        }
    }

    private fun checkBlockCollision(
        level: ClientLevel,
        mut: BlockPos.MutableBlockPos,
        state: BlockState,
        x0: Double, y0: Double, z0: Double,
        invRayX: Double, invRayY: Double, invRayZ: Double
    ): Boolean {
        // known passable
        if (PASSABLE_BLOCKS.contains(state.block)) return false

        // emty shape
        val shape = state.getCollisionShape(level, mut)
        if (shape.isEmpty) return false

        val bounds = shape.bounds()
        val isFullBlock = (bounds.maxX - bounds.minX >= 1.0) &&
                (bounds.maxY - bounds.minY >= 1.0) &&
                (bounds.maxZ - bounds.minZ >= 1.0)

        // full block
        if (isFullBlock) return true

        // partial block. check if ray intersects bb
        val minX = mut.x + bounds.minX
        val minY = mut.y + bounds.minY
        val minZ = mut.z + bounds.minZ
        val maxX = mut.x + bounds.maxX
        val maxY = mut.y + bounds.maxY
        val maxZ = mut.z + bounds.maxZ

        val t1X = (minX - x0) * invRayX
        val t2X = (maxX - x0) * invRayX
        var tMin = min(t1X, t2X)
        var tMax = max(t1X, t2X)

        val t1Y = (minY - y0) * invRayY
        val t2Y = (maxY - y0) * invRayY
        tMin = max(tMin, min(t1Y, t2Y))
        tMax = min(tMax, max(t1Y, t2Y))

        val t1Z = (minZ - z0) * invRayZ
        val t2Z = (maxZ - z0) * invRayZ
        tMin = max(tMin, min(t1Z, t2Z))
        tMax = min(tMax, max(t1Z, t2Z))

        // tru if ray passes through bb
        return tMax >= max(0.0, tMin) && tMin <= 1.0
    }


    /**
     * modified OdinFabric (BSD 3-Clause)
     * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/render/Etherwarp.kt
     */
    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
        val vec3: Vec3 by lazy { Vec3(pos ?: BlockPos.ZERO) }

        companion object {
            val NONE = EtherPos(false, null, null)
        }
    }

    const val PASSABLE = 1        // ray passes through // todo move
    const val BLOCKS_FEET = 2     // cannot stand inside

    val blockFlags: IntArray = IntArray(Block.BLOCK_STATE_REGISTRY.size()).apply {
        Block.BLOCK_STATE_REGISTRY.forEach { state ->
            val block = state.block
            val id = Block.getId(state)

            val passable = when (block) {
                is AirBlock -> true

                is FlowerBlock, is TallGrassBlock, is BushBlock, is TallFlowerBlock, is ShortDryGrassBlock -> true
                is TorchBlock, is RedstoneTorchBlock -> true
                is TripWireBlock, is TripWireHookBlock -> true
                is RailBlock -> true
                is FireBlock -> true
                is VineBlock -> true
                is LiquidBlock -> true
                is SaplingBlock -> true
                is CropBlock, is StemBlock -> true
                is SeagrassBlock, is TallSeagrassBlock -> true
                is SugarCaneBlock -> true
                is MushroomBlock -> true
                is NetherWartBlock -> true
                is RedStoneWireBlock, is ComparatorBlock, is RepeaterBlock -> true
                is SmallDripleafBlock, is BigDripleafStemBlock -> true
                is DoublePlantBlock -> true
                is LeverBlock -> true
                is SnowLayerBlock -> true
                is BubbleColumnBlock -> true
                is GrowingPlantBlock -> true
                is PistonHeadBlock -> true
                is DryVegetationBlock -> true
                is ButtonBlock -> true
                is LanternBlock -> true
                is SkullBlock, is WallSkullBlock -> true
                is LadderBlock -> true
                is FlowerPotBlock -> true
                is WebBlock -> true
                is NetherPortalBlock -> true

                else -> false
            }

            val blocksFeet = when (block) {
                is SkullBlock, is WallSkullBlock -> true
                is FlowerPotBlock -> true
                is LadderBlock -> true
//            is VineBlock -> true
                else -> false
            }

            var flags = 0
            if (passable) flags = flags or PASSABLE
            if (blocksFeet) flags = flags or BLOCKS_FEET

            this[id] = flags
        }
    }


    /**
     * from NoammAddons (CC0-1.0)
     * original: https://github.com/Noamm9/NoammAddons/blob/8f313252fb40e118281255e6915792d4fd85eda0/src/main/kotlin/com/github/noamm9/utils/items/InstantTransmissionHelper.kt#L99
     */
    private val PASSABLE_BLOCKS = setOf(
        Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR,
        Blocks.WATER, Blocks.LAVA, Blocks.TALL_GRASS, Blocks.SHORT_GRASS,
        Blocks.FERN, Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY,
        Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
        Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
        Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.WITHER_ROSE, Blocks.SUNFLOWER,
        Blocks.TORCH, Blocks.WALL_TORCH, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
        Blocks.SNOW, Blocks.VINE, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
        Blocks.SUGAR_CANE, Blocks.KELP, Blocks.LILY_PAD, Blocks.CARROTS, Blocks.POTATOES, Blocks.WHEAT,
        Blocks.BEETROOTS, Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH
    )
}