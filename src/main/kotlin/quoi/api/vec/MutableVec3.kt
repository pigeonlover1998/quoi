package quoi.api.vec

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.utils.BlockPos
import quoi.utils.sq
import kotlin.math.sqrt

data class MutableVec3(var x: Double, var y: Double, var z: Double) {
    constructor(x: Number, y: Number, z: Number) : this(x.toDouble(), y.toDouble(), z.toDouble())
    constructor(mut: MutableVec3) : this(mut.x, mut.y, mut.z)
    constructor(vec3: Vec3) : this(vec3.x, vec3.y, vec3.z)
    constructor(pos: BlockPos) : this(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

    fun immutable() = Vec3(x, y, z)
    fun toBlockPos() = BlockPos(x, y, z)

    fun inside(aabb: AABB): Boolean =
        x >= aabb.minX && x <= aabb.maxX &&
        y >= aabb.minY && y <= aabb.maxY &&
        z >= aabb.minZ && z <= aabb.maxZ

    fun distanceToSqr(x: Number, y: Number, z: Number): Double {
        val dx = (this.x - x.toDouble())
        val dy = (this.y - y.toDouble())
        val dz = (this.z - z.toDouble())
        return dx.sq + dy.sq + dz.sq
    }

    fun distanceTo(x: Number, y: Number, z: Number): Double =
        sqrt(distanceToSqr(x, y, z))

    fun distanceToSqr(to: Vec3): Double =
        distanceToSqr(to.x, to.y, to.z)

    fun add(x: Number = 0, y: Number = 0, z: Number = 0): MutableVec3 =
        MutableVec3(this.x + x.toDouble(), this.y + y.toDouble(), this.z + z.toDouble())

    companion object {
        val ZERO get() = MutableVec3(0.0, 0.0, 0.0)
    }
}