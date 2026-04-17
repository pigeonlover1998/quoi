package quoi.utils

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth.wrapDegrees
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.core.Direction as McDirection
import quoi.QuoiMod.mc
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.utils.WorldUtils.shape
import kotlin.math.*

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/me/odinmain/utils/VecUtils.kt
 */

data class Vec2i(val x: Int, val z: Int)
data class Direction(val yaw: Float, val pitch: Float, val distance: Double = 0.0) {
    fun getLook() = getLook(yaw, pitch)
}

fun getEyeHeight(sneak: Boolean = false): Float {
    val s = if (Location.onModernIsland) 1.27f else 1.54f
    return if (sneak) s else 1.62f
}

fun LocalPlayer.eyeHeight(forceSneak: Boolean = false): Float =
    getEyeHeight(isCrouching || forceSneak)

fun LocalPlayer.eyePosition(forceSneak: Boolean = false) = Vec3(x, y + eyeHeight(forceSneak), z)

operator fun Vec3.component1(): Double = x
operator fun Vec3.component2(): Double = y
operator fun Vec3.component3(): Double = z

operator fun BlockPos.component1(): Int = x
operator fun BlockPos.component2(): Int = y
operator fun BlockPos.component3(): Int = z


operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

fun Vec3.addVec(x: Number = 0.0, y: Number = 0.0, z: Number = 0.0): Vec3 =
    Vec3(this.x + x.toDouble(), this.y + y.toDouble(), this.z + z.toDouble())

inline val Vec3.aabb: AABB get() = AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0)
fun Vec3.aabb(radius: Double = 0.0) = AABB(
    x - radius,
    y - radius,
    z - radius,
    x + radius,
    y + radius,
    z + radius
)
inline val Vec3.blockPos: BlockPos get() = BlockPos.containing(this)

inline val BlockPos.aabb: AABB get() = AABB(this)
inline val BlockPos.vec3: Vec3 get() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
inline val BlockPos.bounds: AABB? get() {
    return mc.level?.let { level ->
        level.getBlockState(this)?.getShape(level, this)?.singleEncompassing()
            ?.takeIf { !it.isEmpty }?.bounds()
    }
}

fun Vec3(x: Number, y: Number, z: Number) = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
fun BlockPos(x: Number, y: Number, z: Number) =
    BlockPos(
        floor(x.toDouble()).toInt(),
        floor(y.toDouble()).toInt(),
        floor(z.toDouble()).toInt(),
    )

fun AABB.copy(
    minX: Double = this.minX,
    minY: Double = this.minY,
    minZ: Double = this.minZ,
    maxX: Double = this.maxX,
    maxY: Double = this.maxY,
    maxZ: Double = this.maxZ
) = AABB(minX, minY, minZ, maxX, maxY, maxZ)

/**
 * Rotates a Vec3 around the given rotation.
 * @param rotation The rotation to rotate around
 * @return The rotated Vec3
 */
fun BlockPos.rotateAroundNorth(rotation: Rotations): BlockPos =
    when (rotation) {
        Rotations.NORTH -> BlockPos(-this.x, this.y, -this.z)
        Rotations.WEST ->  BlockPos(-this.z, this.y, this.x)
        Rotations.SOUTH -> BlockPos(this.x, this.y, this.z)
        Rotations.EAST ->  BlockPos(this.z, this.y, -this.x)
        else -> this
    }

/**
 * Rotates a Vec3 to the given rotation.
 * @param rotation The rotation to rotate to
 * @return The rotated Vec3
 */
fun BlockPos.rotateToNorth(rotation: Rotations): BlockPos =
    when (rotation) {
        Rotations.NORTH -> BlockPos(-this.x, this.y, -this.z)
        Rotations.WEST ->  BlockPos(this.z, this.y, -this.x)
        Rotations.SOUTH -> BlockPos(this.x, this.y, this.z)
        Rotations.EAST ->  BlockPos(-this.z, this.y, this.x)
        else -> this
    }

//fun Vec3.rotateAroundNorth(rotation: Rotations): Vec3 =
//    when (rotation) {
//        Rotations.NORTH -> Vec3(-x, y, -z)
//        Rotations.WEST  -> Vec3(-z, y, x)
//        Rotations.SOUTH -> Vec3(x, y, z)
//        Rotations.EAST  -> Vec3(z, y, -x)
//        else -> this
//    }
//
//fun Vec3.rotateToNorth(rotation: Rotations): Vec3 =
//    when (rotation) {
//        Rotations.NORTH -> Vec3(-x, y, -z)
//        Rotations.WEST  -> Vec3(z, y, -x)
//        Rotations.SOUTH -> Vec3(x, y, z)
//        Rotations.EAST  -> Vec3(-z, y, x)
//        else -> this
//    }

fun Vec3.rotateAroundNorth(rotation: Rotations): Vec3 { // fixes my stupidity
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val iz = floor(z).toInt()
    val rotatedBlock = BlockPos(ix, iy, iz).rotateAroundNorth(rotation)
    return Vec3(rotatedBlock.x + (x - ix), rotatedBlock.y + (y - iy), rotatedBlock.z + (z - iz))
}

fun Vec3.rotateToNorth(rotation: Rotations): Vec3 {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val iz = floor(z).toInt()
    val rotatedBlock = BlockPos(ix, iy, iz).rotateToNorth(rotation)
    return Vec3(rotatedBlock.x + (x - ix), rotatedBlock.y + (y - iy), rotatedBlock.z + (z - iz))
}

/**
 * Multiplies every coordinate of a Vec3 by the given factor.
 * @param factor The factor to multiply by
 * @return The multiplied Vec3
 */
fun Vec3.multiply(factor: Number): Vec3 =
    Vec3(this.x * factor.toDouble(), this.y * factor.toDouble(), this.z * factor.toDouble())

fun Vec3.multiply(x: Double = 1.0, y: Double = 1.0, z: Double = 1.0): Vec3 =
    Vec3(this.x * x, this.y * y, this.z * z)

fun Vec3.equal(other: Vec3): Boolean =
    this.x == other.x && this.y == other.y && this.z == other.z

fun Vec3.distanceTo2D(to: Vec3): Double {
    val dx = this.x - to.x
    val dz = this.z - to.z
    return sqrt(dx * dx + dz * dz)
}

fun BlockPos.distanceTo(to: BlockPos): Double {
    val dx = (this.x - to.x).toDouble()
    val dy = (this.y - to.y).toDouble()
    val dz = (this.z - to.z).toDouble()
    return sqrt(dx.sq + dy.sq + dz.sq)
}

fun BlockPos.getHitResult(force: Boolean = false): BlockHitResult? {
    val player = mc.player ?: return null

    var shape = this.shape
    if (shape.isEmpty && force) shape = Shapes.block()
    if (shape.isEmpty) return null

    val eyes = player.eyePosition()
    val centre = shape.bounds().center.add(x.toDouble(), y.toDouble(), z.toDouble())
    val dir = centre.subtract(eyes).normalize()
    val end = eyes.add(dir.scale(eyes.distanceTo(centre) + 1.5))
    return shape.clip(eyes, end, this)
}

/**
 * Returns Triple(distance, yaw, pitch) in minecraft coordinate system to get from x0y0z0 to x1y1z1.
 *
 * @param from Vec3 to get direction from.
 *
 * @param to Vec3 to get direction to.
 *
 * @return Triple of distance, yaw, pitch
 * @author Aton
 */
fun getDirection(from: Vec3, to: Vec3): Direction {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z

    val distXZ = sqrt(dx.sq + dz.sq)
    val dist = sqrt(distXZ.sq + dy.sq)

    val yaw = -atan2(dx, dz).deg
    val pitch = -atan2(dy, distXZ).deg

    return Direction(wrapDegrees(yaw), wrapDegrees(pitch), dist)
}

fun getDirection(to: Vec3) = getDirection(mc.player!!.eyePosition(), to)

/**
 * Returns a triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible.
 *
 * @param to The position to rotate to.
 * @return A triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible
 * @see getDirection
 * @author Aton
 */
fun getEtherwarpDirection(from: Vec3, to: BlockPos, dist: Double = 61.0): Direction? {
    if (from.distanceToSqr(to.vec3) > (dist + 2) * (dist + 2)) return null

    val visibleVec = getVisiblePoint(from, to) ?: return null

    return getDirection(from, visibleVec)
}

fun getEtherwarpDirection(to: BlockPos, dist: Double = 61.0) = getEtherwarpDirection(mc.player!!.eyePosition(true), to, dist)

fun getArrowDirection(from: Vec3, to: Vec3, isTerminator: Boolean = false): Direction {

    var yaw = atan2(to.z - from.z, to.x - from.x).deg - 90.0f

    if (!isTerminator) {
        val origin = getArrowOrigin(from, yaw, false)
        yaw = atan2(to.z - origin.z, to.x - origin.x).deg - 90.0f
    }

    val origin = getArrowOrigin(from, yaw, isTerminator)
    val dist = (to.x - origin.x) * (to.x - origin.x) + (to.z - origin.z) * (to.z - origin.z)

    fun simulateHitY(pitch: Float): Double {
        val yawRad = yaw.rad
        val pitchRad = pitch.rad

        var (px, py, pz) = origin
        var mx = -sin(yawRad) * cos(pitchRad) * 3.0
        var my = -sin(pitchRad) * 3.0
        var mz = cos(yawRad) * cos(pitchRad) * 3.0

        for (tick in 0..100) {
            px += mx
            py += my
            pz += mz

            val currDist = (px - origin.x) * (px - origin.x) + (pz - origin.z) * (pz - origin.z)
            if (currDist >= dist) return py

            mx *= 0.99
            my = my * 0.99 - 0.05
            mz *= 0.99
        }
        return py
    }

    var minPitch = -90.0f
    var maxPitch = 90.0f
    repeat(20) {
        val midPitch = (minPitch + maxPitch) / 2f
        if (simulateHitY(midPitch) > to.y) minPitch = midPitch else maxPitch = midPitch
    }

    return Direction(yaw, (minPitch + maxPitch) / 2f)
}

fun getArrowDirection(to: Vec3, isTerminator: Boolean = false): Direction {
    val player = mc.player ?: return Direction(0f, 0f)
    return getArrowDirection(player.eyePosition(), to, isTerminator)
}

fun getArrowDirection(from: Vec3, to: BlockPos, isTerminator: Boolean = false): Direction? {
    val c = to.vec3.add(0.5, 0.5, 0.5)
    val o = 0.25

    val testPoints = listOf(
        c,                                        // centxe
        c.add(0.0, o, 0.0),  c.add(0.0, -o, 0.0), // top bot
        c.add(o, 0.0, 0.0),  c.add(-o, 0.0, 0.0), // left right
        c.add(0.0, 0.0, o),  c.add(0.0, 0.0, -o)  // front back
    )

    for (point in testPoints) {
        val dir = getArrowDirection(from, point, isTerminator)
        val yawRad = dir.yaw.rad
        val pitchRad = dir.pitch.rad
        val origin = getArrowOrigin(from, dir.yaw, isTerminator)
        val dist = (to.x - origin.x) * (to.x - origin.x) + (to.z - origin.z) * (to.z - origin.z)

        var px = origin.x
        var py = origin.y
        var pz = origin.z

        var mx = -sin(yawRad) * cos(pitchRad) * 3.0
        var my = -sin(pitchRad) * 3.0
        var mz = cos(yawRad) * cos(pitchRad) * 3.0

        var good = false

        for (tick in 0..100) {

            val hit = rayCast(px, py, pz, mx, my, mz)

            if (hit != null) {
                if (hit == to) {
                    good = true
                    break
                }
                break
            }

            px = mx
            py = my
            pz = mz

            val currDist = (px - origin.x) * (px - origin.x) + (pz - origin.z) * (pz - origin.z)
            if (currDist >= dist) {
                good = true
                break
            }

            mx *= 0.99
            my = my * 0.99 - 0.05
            mz *= 0.99
        }
        if (good) return dir
    }
    return null
}

fun getArrowDirection(to: BlockPos, isTerminator: Boolean = false) = getArrowDirection(mc.player!!.eyePosition(), to, isTerminator)

fun getArrowOrigin(from: Vec3, yaw: Float, isTerminator: Boolean): Vec3 {
    return if (isTerminator) {
        Vec3(from.x, from.y - 0.01, from.z)
    } else {
        val r = yaw.rad
        Vec3(
            from.x - cos(r) * 0.16,
            from.y - 0.1,
            from.z - sin(r) * 0.16
        )
    }
}

fun isPathClear(from: Vec3, target: Vec3): Boolean {
    val level = mc.level ?: return false
    val result = level.clip(
        ClipContext(
            from,
            target,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player as Entity
        )
    )
    return result.type == HitResult.Type.MISS
}

fun getVisiblePoint(from: Vec3, to: BlockPos): Vec3? {
    val t = to.vec3
    val targets = listOf(
        // centre
        t.add(0.5, 1.0, 0.5),
        t.add(0.0, 0.5, 0.5), // left
        t.add(1.0, 0.5, 0.5), // right
        t.add(0.5, 0.5, 0.0), // front
        t.add(0.5, 0.5, 1.0), // back
        t.add(0.5, 0.0, 0.5), // bottom
        // left
        t.add(0.0, 0.001, 0.001),
        t.add(0.0, 0.001, 0.999),
        t.add(0.0, 0.999, 0.001),
        t.add(0.0, 0.999, 0.999),
        // right
        t.add(1.0, 0.001, 0.001),
        t.add(1.0, 0.001, 0.999),
        t.add(1.0, 0.999, 0.001),
        t.add(1.0, 0.999, 0.999),
        // front
        t.add(0.001, 0.001, 0.0),
        t.add(0.001, 0.999, 0.0),
        t.add(0.999, 0.001, 0.0),
        t.add(0.999, 0.999, 0.0),
        // back
        t.add(0.001, 0.001, 1.0),
        t.add(0.001, 0.999, 1.0),
        t.add(0.999, 0.001, 1.0),
        t.add(0.999, 0.999, 1.0),
        // bottom
        t.add(0.001, 0.0, 0.001),
        t.add(0.001, 0.0, 0.999),
        t.add(0.999, 0.0, 0.001),
        t.add(0.999, 0.0, 0.999),
        // top
        t.add(0.001, 1.0, 0.001),
        t.add(0.001, 1.0, 0.999),
        t.add(0.999, 1.0, 0.001),
        t.add(0.999, 1.0, 0.999)
    )

    for (targetVec in targets) {
        val hitPos = rayCast(from, targetVec.subtract(from))
        if (hitPos == to) {
            return targetVec
        }
    }
    return null
}

fun getVisiblePoint(to: BlockPos) = getVisiblePoint(mc.player!!.eyePosition(), to)

fun rayCast(
    x: Double, y: Double, z: Double,
    dx: Double, dy: Double, dz: Double,
    etherwarp: Boolean = true,
): BlockPos? {
    return traverseVoxels(x, y, z, x + dx, y + dy, z + dz, etherwarp).pos
}

fun rayCast(vec3: Vec3, vec31: Vec3, firstBlock: Boolean = false) =
    rayCast(vec3.x, vec3.y, vec3.z, vec31.x, vec31.y, vec31.z, firstBlock)

fun rayCast(
    lookVec: Vec3 = mc.player!!.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)),
    distance: Double = 61.0,
    etherwarp: Boolean = true
): BlockPos? {
    val player = mc.player ?: return null
    val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    val origin = Vec3(
        player.x,
        player.getEyePosition(pt).y,
        player.z
    )

    val delta = lookVec.scale(distance)
    return rayCast(origin, delta, etherwarp)
}

fun rayCastVec(
    x: Double, y: Double, z: Double,
    dx: Double, dy: Double, dz: Double,
    etherwarp: Boolean = true
): Vec3? {
    val w = mc.level ?: return null

    val startVec = Vec3(x, y, z)
    val endVec = Vec3(x + dx, y + dy, z + dz)

    val result = traverseVoxels(startVec, endVec, etherwarp)

    val bp = result.pos ?: return null
    val bs = result.state ?: return null

    val shape = bs.getShape(w, bp)
    val hitResult = shape.clip(startVec, endVec, bp)

    if (hitResult != null) {
        return hitResult.location
    }

    val fallbackHit = bp.aabb.clip(startVec, endVec)
    if (fallbackHit.isPresent) {
        return fallbackHit.get()
    }

    return startVec
}

fun rayCastVec(vec3: Vec3, vec31: Vec3, firstBlock: Boolean = false): Vec3? =
    rayCastVec(vec3.x, vec3.y, vec3.z, vec31.x, vec31.y, vec31.z, firstBlock)

fun rayCastVec(
    lookVec: Vec3 = mc.player!!.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)),
    distance: Double = 61.0,
    etherwarp: Boolean = false
): Vec3? {
    val player = mc.player ?: return null
    val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    val origin = Vec3(
        player.x,
        player.getEyePosition(pt).y,
        player.z
    )

    val delta = lookVec.scale(distance)
    return rayCastVec(origin, delta, etherwarp)
}

fun isXZInterceptable(box: AABB, range: Double, pos: Vec3, yaw: Float, pitch: Float): Boolean {
    val start = pos.addVec(y = (mc.player?.eyeY ?: 0.0))
    val goal = start.add(getLook(yaw, pitch).multiply(range, range, range))

    return isVecInZ(start.intermediateWithXValue(goal, box.minX), box) ||
            isVecInZ(start.intermediateWithXValue(goal, box.maxX), box) ||
            isVecInX(start.intermediateWithZValue(goal, box.minZ), box) ||
            isVecInX(start.intermediateWithZValue(goal, box.maxZ), box)
}

fun getLook(yaw: Float, pitch: Float): Vec3 {
    val f2 = -cos(-pitch * 0.017453292f).toDouble()
    return Vec3(
        sin(-yaw * 0.017453292f - 3.1415927f) * f2,
        sin(-pitch * 0.017453292f).toDouble(),
        cos(-yaw * 0.017453292f - 3.1415927f) * f2
    )
}

private fun isVecInX(vec: Vec3?, box: AABB): Boolean =
    vec != null && vec.x >= box.minX && vec.x <= box.maxX

private fun isVecInZ(vec: Vec3?, box: AABB): Boolean =
    vec != null && vec.z >= box.minZ && vec.z <= box.maxZ

private fun Vec3.intermediateWithXValue(goal: Vec3, x: Double): Vec3? {
    val dx = goal.x - this.x
    if (dx * dx < 1e-8) return null
    val t = (x - this.x) / dx
    return if (t in 0.0..1.0) Vec3(
        this.x + dx * t,
        this.y + (goal.y - this.y) * t,
        this.z + (goal.z - this.z) * t
    ) else null
}

private fun Vec3.intermediateWithZValue(goal: Vec3, z: Double): Vec3? {
    val dz = goal.z - this.z
    if (dz * dz < 1e-8) return null
    val t = (z - this.z) / dz
    return if (t in 0.0..1.0) Vec3(
        this.x + (goal.x - this.x) * t,
        this.y + (goal.y - this.y) * t,
        this.z + dz * t
    ) else null
}

fun dot(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double): Double {
    return (x0 * x1) + (y0 * y1) + (z0 * z1)
}


/**
 * modified OdinFabric (BSD 3-Clause)
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/render/Etherwarp.kt
 */

data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
    val vec3: Vec3 by lazy { Vec3(pos ?: BlockPos.ZERO)  }

    companion object {
        val NONE = EtherPos(false, null, null)
    }
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

            if (!etherwarp && isPassable) return EtherPos(false, mut.immutable(), state)

            val collisionTop = state.getCollisionShape(level, mut).max(McDirection.Axis.Y)
            val clearanceBaseY = mut.y + max(1.0, ceil(collisionTop))

            mut.set(x, clearanceBaseY, z)

            val feetFlags = blockFlags[Block.getId(level.getBlockState(mut))]
            if ((feetFlags and PASSABLE) == 0 || (feetFlags and BLOCKS_FEET) != 0)
                return EtherPos(false, mut.immutable(), state)

            mut.set(x, clearanceBaseY + 1, z)

            val headFlags = blockFlags[Block.getId(level.getBlockState(mut))]
            if ((headFlags and PASSABLE) == 0 || (headFlags and BLOCKS_FEET) != 0)
                return EtherPos(false, mut.immutable(), state)

            mut.set(x, y, z)
            return EtherPos(true, mut.immutable(), state)
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

private const val PASSABLE = 1        // ray passes through
private const val BLOCKS_FEET = 2     // cannot stand inside

private val blockFlags: IntArray = IntArray(Block.BLOCK_STATE_REGISTRY.size()).apply {
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
            is VineBlock -> true
            else -> false
        }

        var flags = 0
        if (passable) flags = flags or PASSABLE
        if (blocksFeet) flags = flags or BLOCKS_FEET

        this[id] = flags
    }
}