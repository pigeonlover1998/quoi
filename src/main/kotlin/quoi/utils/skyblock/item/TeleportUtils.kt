package quoi.utils.skyblock.item

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import quoi.api.world.Direction
import quoi.api.world.RaycastResult
import quoi.utils.*
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import kotlin.math.*
import net.minecraft.core.Direction as McDirection

// todo cleanup
object TeleportUtils : Shortcuts {

    fun Vec3.getTeleportPos(yaw: Float, pitch: Float, distance: Double = 12.0): RaycastResult {
        val (dx, dy, dz) = getLook(wrapDegrees(yaw), wrapDegrees(pitch))
        return predictTransmission(x, y, z, dx, dy, dz, distance)
    }

    fun Vec3.getEtherPos(yaw: Float, pitch: Float, distance: Double = 61.0): RaycastResult {
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

    fun getEtherwarpDirection(to: BlockPos, dist: Double = 61.0) = getEtherwarpDirection(player.eyePosition(true), to, dist)

    fun getTransmissionDirection(from: Vec3, to: BlockPos, dist: Double = 12.0): Direction? {
        if (from.distanceToSqr(to.vec3) > (dist + 2) * (dist + 2)) return null

        val visibleVec = getVisiblePoint(from, to) { vec ->
            val n = vec.normalize()
            predictTransmission(from.x, from.y, from.z, n.x, n.y, n.z, dist).pos
        } ?: return null

        return getDirection(from, visibleVec)
    }

    fun getTransmissionDirection(to: BlockPos, dist: Double = 12.0) = getTransmissionDirection(player.eyePosition(false), to, dist)

    /**
     * based on noammaddons' InstantTransmissionHelper which is based on rsm's EtherUtils which is based on soshimee's zph algorithm
     * https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/com/github/noamm9/utils/items/InstantTransmissionHelper.kt#L99
     * https://github.com/rs-mod/rsm/blob/69e992bebc2840d1f49166685e393ec53a8e0312/src/main/java/com/ricedotwho/rsm/utils/EtherUtils.java#L438
     * https://discord.com/channels/1180667916560109588/1232500078585974874 // discord.gg/sby
     */
    fun predictTransmission(x0: Double, y0: Double, z0: Double, dx: Double, dy: Double, dz: Double, distance: Double): RaycastResult {
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

            val stateFeet = chunk?.getBlockState(mut) ?: return RaycastResult.NONE
            val hitFeet = checkBlockCollision(level, mut, stateFeet, x0, y0, z0, invRayX, invRayY, invRayZ)

            mut.set(x, y + 1.0, z)
            val stateHead = chunk.getBlockState(mut)
            val hitHead = checkBlockCollision(level, mut, stateHead, x0, y0, z0, invRayX, invRayY, invRayZ)

            if (hitFeet || hitHead) { // if hit block go back
                return if (stepCount == 0) RaycastResult(false, mut.immutable(), if (hitFeet) stateFeet else stateHead)
                else RaycastResult(true, BlockPos(lastX, lastY, lastZ), lastState)
            }

            if (x == endX && y == endY && z == endZ) return RaycastResult(true, BlockPos(x, y, z), stateFeet)

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

        return RaycastResult.NONE
    }

    fun predictTransmission(from: Vec3, yaw: Float, pitch: Float, distance: Double = 12.0): RaycastResult {
        val (x0, y0, z0) = from
        val (dx, dy, dz) = getLook(yaw, pitch)
        return predictTransmission(x0, y0, z0, dx, dy, dz, distance)
    }

    fun predictTransmission(from: Vec3, to: Vec3, distance: Double = 12.0): RaycastResult {
        val (x0, y0, z0) = from
        val (dx, dy, dz) = getDirection(from, to).look()
        return predictTransmission(x0, y0, z0, dx, dy, dz, distance)
    }

    /**
     * Traverses voxels from start to end and returns the first non-air block it hits.
     * @author unclambomb6
     */
    fun traverseVoxels(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, etherwarp: Boolean): RaycastResult {
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

            val state = chunk?.getBlockState(mut) ?: return RaycastResult.NONE
            val id = Block.getId(state)

            val isPassable = (blockFlags[id] and PASSABLE) != 0
            val isSolid = !isPassable

            if ((etherwarp && isSolid) || (!etherwarp && id != 0)) {
                val hitPos = mut.immutable()

                if (!etherwarp && isPassable) return RaycastResult(false, hitPos, state)

                val collisionTop = state.getCollisionShape(level, hitPos).max(McDirection.Axis.Y)
                val clearanceBaseY = hitPos.y + max(1.0, ceil(collisionTop))

                mut.set(x, clearanceBaseY, z)

                val feetFlags = blockFlags[Block.getId(chunk.getBlockState(mut))]
                if ((feetFlags and PASSABLE) == 0 || (feetFlags and BLOCKS_FEET) != 0)
                    return RaycastResult(false, hitPos, state)

                mut.set(x, clearanceBaseY + 1, z)

                val headFlags = blockFlags[Block.getId(chunk.getBlockState(mut))]
                if ((headFlags and PASSABLE) == 0 || (headFlags and BLOCKS_FEET) != 0)
                    return RaycastResult(false, hitPos, state)

                return RaycastResult(true, hitPos, state)
            }

            if (x == endX && y == endY && z == endZ) return RaycastResult.NONE

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

        return RaycastResult.NONE
    }

    fun traverseVoxels(from: Vec3, to: Vec3, etherwarp: Boolean): RaycastResult {
        val (x0, y0, z0) = from
        val (x1, y1, z1) = to
        return traverseVoxels(x0, y0, z0, x1, y1, z1, etherwarp)
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

    const val PASSABLE = 1        // ray passes through
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
                is CandleBlock -> true
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
        Blocks.SUGAR_CANE, Blocks.KELP, /*Blocks.LILY_PAD,*/ Blocks.CARROTS, Blocks.POTATOES, Blocks.WHEAT,
        Blocks.BEETROOTS, Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH, Blocks.CANDLE
    )
}