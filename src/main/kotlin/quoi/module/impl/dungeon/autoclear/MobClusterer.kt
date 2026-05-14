package quoi.module.impl.dungeon.autoclear

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import quoi.utils.BlockPos
import quoi.utils.vec3

object MobClusterer {

    /**
     * Groups [mobs] that fall within the [HYPE_AOE].
     *
     * @param mobs Starred mobs in the dungeon room.
     * @return A list of [MobCluster]s, each representing one hyperion strike.
     */
    fun cluster(mobs: List<LivingEntity>): List<MobCluster> {
//        val clusters = mutableListOf<MobCluster>()
//
//        for (mob in mobs) {
//            val mobPos = mob.position()
//
//            val existing = clusters.firstOrNull { cluster ->
//                cluster.seed.distanceTo(mobPos) <= HYPE_AOE
//            }
//
//            if (existing != null) {
//                existing.mobs.add(mob)
//            } else {
//                clusters.add(
//                    MobCluster(
//                        seed = mobPos,
//                        mobs = mutableListOf(mob)
//                    )
//                )
//            }
//        }
//
//        return clusters

        val clusters = mutableListOf<MobCluster>()

        for (mob in mobs) {
            var added = false

            for (cluster in clusters) {
                val candidates = cluster.mobs + mob
                val candidatePos = getMiddle(candidates)

                val valid = candidates.all { it.position().distanceTo(candidatePos.vec3) <= HYPE_AOE }

                if (valid) {
                    cluster.mobs.add(mob)
                    cluster.pos = candidatePos.below()
                    added = true
                    break
                }
            }

            if (!added) {
                clusters.add(
                    MobCluster(
                        pos = getMiddle(listOf(mob)).below(),
                        mobs = mutableListOf(mob)
                    )
                )
            }
        }

        return clusters
    }

    /**
     * Orders [clusters] by proximity.
     *
     * @param from Starting position.
     * @param clusters Unordered clusters returned by [cluster].
     * @return An ordered list of [MobCluster]s
     */
    fun greedyOrder(
        from: Vec3,
        clusters: List<MobCluster>,
    ): List<MobCluster> { // todo maybe move seeds
        if (clusters.isEmpty()) return emptyList()

        val remaining = clusters.toMutableList()
        val sorted = mutableListOf<MobCluster>()
        var currPos = from

        while (remaining.isNotEmpty()) {
            val nearest = remaining.minByOrNull { it.pos.below().vec3.distanceTo(currPos) }!!
            sorted.add(nearest)
            remaining.remove(nearest)
            currPos = nearest.pos.vec3
        }

        return sorted
    }

    /**
     * Clusters [mobs] then immediately sorts the result
     * using [greedyOrder] starting from [from].
     *
     * @param from The player's position.
     * @param mobs Starred mobs from the dungeon room.
     * @return An ordered list of [MobCluster]s
     */
    fun getOrderedClusters(
        from: Vec3,
        mobs: List<LivingEntity>,
    ): List<MobCluster> {
        val clusters = cluster(mobs)
        return greedyOrder(from, clusters)
    }

    private fun getMiddle(mobs: List<LivingEntity>): BlockPos {
        var x = 0.0
        var y = 0.0
        var z = 0.0

        mobs.forEach { mob ->
            val pos = mob.position()
            x += pos.x
            y += pos.y
            z += pos.z
        }

        val size = mobs.size
        return BlockPos(x / size, y / size, z / size)
    }
}

