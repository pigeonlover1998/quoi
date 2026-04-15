package quoi.utils

import net.minecraft.core.BlockPos

object CachedSphere {

    const val RADIUS = 50
    const val RADIUS_SQ = RADIUS * RADIUS

    val table: LongArray
    val indices: IntArray

    init {
        val temp = Array(RADIUS_SQ + 1) { mutableListOf<Long>() }
        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) {
                for (z in -RADIUS..RADIUS) {
                    val dist = x * x + y * y + z * z
                    if (dist > RADIUS_SQ) continue
                    temp[dist].add(BlockPos.asLong(x, y, z))
                }
            }
        }

        indices = IntArray(RADIUS_SQ + 2)
        var totalSize = 0
        for (dist in 0..RADIUS_SQ) {
            indices[dist] = totalSize
            totalSize += temp[dist].size
        }
        indices[RADIUS_SQ + 1] = totalSize

        table = LongArray(totalSize)
        var cursor = 0
        for (list in temp) {
            for (packedRel in list) {
                table[cursor++] = packedRel
            }
        }
    }

    inline fun forEachInRadius(
        x: Int, y: Int, z: Int,
        radius: Float,
        block: (x: Int, y: Int, z: Int) -> Unit
    ) {
        val r = radius.sq.toInt().coerceAtMost(RADIUS_SQ)
        val end = indices[r + 1]

        for (i in 0 until end) {
            val packed = table[i]

            val dx = (packed shr 38).toInt()
            val dy = (packed shl 52 shr 52).toInt()
            val dz = (packed shl 26 shr 38).toInt()

            block(x + dx, y + dy, z + dz)
        }
    }
}