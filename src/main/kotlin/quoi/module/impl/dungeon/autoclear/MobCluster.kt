package quoi.module.impl.dungeon.autoclear

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity

/**
 * Rrepresents a single hyperion kill zone
 *
 * @param pos Position where the hyperion should land
 * @param mobs All mobs within [HYPE_AOE] of [pos]
 */
data class MobCluster(
    var pos: BlockPos,
    val mobs: MutableList<LivingEntity>
)