package quoi.utils

import quoi.QuoiMod.mc
import quoi.utils.WorldUtils.world
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/me/odinmain/utils/VecUtils.kt
 */
operator fun Vec3.component1(): Double = x
operator fun Vec3.component2(): Double = y
operator fun Vec3.component3(): Double = z

operator fun BlockPos.component1(): Int = x
operator fun BlockPos.component2(): Int = y
operator fun BlockPos.component3(): Int = z


operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

inline val Vec3.aabb: AABB get() = AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0)
inline val BlockPos.aabb: AABB get() = AABB(this)
inline val BlockPos.vec3: Vec3 get() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3.rotate(rotation: Int): Vec3 =
    when ((rotation % 360 + 360) % 360) {
        0   -> Vec3(x, y, z)
        90  -> Vec3(z, y, -x)
        180 -> Vec3(-x, y, -z)
        270 -> Vec3(-z, y, x)
        else -> this
    }

fun Vec3.unrotate(rotation: Int): Vec3 = rotate(360 - rotation)

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

/**
 * Returns Triple(distance, yaw, pitch) in minecraft coordinate system to get from x0y0z0 to x1y1z1.
 *
 * @param vec3 Vec3 to get direction to.
 *
 * @param vec31 Vec3 to get direction from.
 *
 * @return Triple of distance, yaw, pitch
 * @author Aton
 */
fun getDirection(vec3: Vec3, vec31: Vec3): Triple<Double, Float, Float> {
    val dist = sqrt((vec31.x - vec3.x).pow(2) + (vec31.y - vec3.y).pow(2) + (vec31.z - vec3.z).pow(2))
    val yaw = -atan2((vec31.x - vec3.x), (vec31.z - vec3.z)) / PI * 180
    val pitch = -atan2((vec31.y - vec3.y), sqrt((vec31.x - vec3.x).pow(2) + (vec31.z - vec3.z).pow(2))) / PI * 180
    return Triple(dist, yaw.toFloat() % 360f, pitch.toFloat() % 360f)
}

/**
 * Returns a triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible.
 *
 * @param targetPos The position to rotate to.
 * @return A triple of distance, yaw, pitch to rotate to the given position with etherwarp physics, or null if etherwarp is not possible
 * @see getDirection
 * @author Aton
 */
fun etherwarpRotateTo(targetPos: BlockPos, dist: Double = 61.0): Triple<Double, Float, Float>? {
    val player = mc.player ?: return null

    if (player.distanceToSqr(targetPos.vec3) > (dist + 2) * (dist + 2)) return null

    // check whether the block can be seen or is to far away
    val targets = listOf(
        // center
        Vec3(targetPos).add(Vec3(0.5, 1.0, 0.5)),

        // face centers
        Vec3(targetPos).add(Vec3(0.0, 0.5, 0.5)),
        Vec3(targetPos).add(Vec3(1.0, 0.5, 0.5)),
        Vec3(targetPos).add(Vec3(0.5, 0.5, 0.0)),
        Vec3(targetPos).add(Vec3(0.5, 0.5, 1.0)),
        Vec3(targetPos).add(Vec3(0.5, 0.0, 0.5)),

        // bottom layer
        Vec3(targetPos).add(Vec3(0.001, 0.0, 0.001)),
        Vec3(targetPos).add(Vec3(0.001, 0.0, 0.999)),
        Vec3(targetPos).add(Vec3(0.999, 0.0, 0.001)),
        Vec3(targetPos).add(Vec3(0.999, 0.0, 0.999)),

        // top layer
        Vec3(targetPos).add(Vec3(0.001, 1.0, 0.001)),
        Vec3(targetPos).add(Vec3(0.001, 1.0, 0.999)),
        Vec3(targetPos).add(Vec3(0.999, 1.0, 0.001)),
        Vec3(targetPos).add(Vec3(0.999, 1.0, 0.999))
    )

    val eyeVec = player.eyePosition

    for (targetVec in targets) {
        val result = mc.level!!.clip( // todo use rayCast
            ClipContext(
                eyeVec,
                targetVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        if (result.blockPos == targetPos) {
            return getDirection(eyeVec, targetVec)
        }
    }

    return null
}

fun rayCast(
    x: Double, y: Double, z: Double,
    dx: Double, dy: Double, dz: Double,
    firstBlock: Boolean = false,
): BlockPos? {
    val w = world ?: return null

    for (bp in DDA(x, y, z, dx, dy, dz)) {
        val bs = w.getBlockState(bp)
        if (firstBlock && !bs.isAir) return bp
        if (!BlockTypes.AirLike.contains(bs.block)) return bp
    }

    return null
}

fun rayCast(vec3: Vec3, vec31: Vec3, firstBlock: Boolean = false) =
    rayCast(vec3.x, vec3.y, vec3.z, vec31.x, vec31.y, vec31.z, firstBlock)

fun rayCast(
    lookVec: Vec3 = mc.player!!.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)),
    distance: Double = 61.0,
    firstBlock: Boolean = false
): BlockPos? {
    val player = mc.player ?: return null
    val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    val origin = Vec3(
        player.x,
        player.getEyePosition(pt).y,
        player.z
    )

    val delta = lookVec.scale(distance)
    return rayCast(origin, delta, firstBlock)
}

fun rayCastVec(
    x: Double, y: Double, z: Double,
    dx: Double, dy: Double, dz: Double,
    firstBlock: Boolean = false
): Vec3? {
    val w = world ?: return null

    val startVec = Vec3(x, y, z)
    val endVec = Vec3(x + dx, y + dy, z + dz)

    for (bp in DDA(x, y, z, dx, dy, dz)) {
        val bs = w.getBlockState(bp)
        val isTarget = if (firstBlock) !bs.isAir else !BlockTypes.AirLike.contains(bs.block)

        if (isTarget) {

            val shape = bs.getShape(w, bp)
            val hitResult = shape.clip(startVec, endVec, bp)

            if (hitResult != null) {
                return hitResult.location
            }

            val aabb = AABB(bp)
            val fallbackHit = aabb.clip(startVec, endVec)
            if (fallbackHit.isPresent) {
                return fallbackHit.get()
            }

            return Vec3(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5)
        }
    }

    return null
}

fun rayCastVec(vec3: Vec3, vec31: Vec3, firstBlock: Boolean = false): Vec3? =
    rayCastVec(vec3.x, vec3.y, vec3.z, vec31.x, vec31.y, vec31.z, firstBlock)

fun rayCastVec(
    lookVec: Vec3 = mc.player!!.getViewVector(mc.deltaTracker.getGameTimeDeltaPartialTick(false)),
    distance: Double = 61.0,
    firstBlock: Boolean = false
): Vec3? {
    val player = mc.player ?: return null
    val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    val origin = Vec3(
        player.x,
        player.getEyePosition(pt).y,
        player.z
    )

    val delta = lookVec.scale(distance)
    return rayCastVec(origin, delta, firstBlock)
}