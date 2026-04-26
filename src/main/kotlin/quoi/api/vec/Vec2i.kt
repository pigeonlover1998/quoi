package quoi.api.vec

data class Vec2i(val x: Int, val z: Int) {
    fun add(other: Vec2i) = Vec2i(x + other.x, z + other.z)
    fun add(x: Number, z: Number) = Vec2i(this.x + x.toInt(), this.z + z.toInt())
}