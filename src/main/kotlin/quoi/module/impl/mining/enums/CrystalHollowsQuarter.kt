package quoi.module.impl.mining.enums

import net.minecraft.core.BlockPos

enum class CrystalHollowsQuarter(val predicate: (BlockPos) -> Boolean) {
    JUNGLE({ it.x <= 576 && it.z <= 576 }),
    PRECURSOR_REMNANTS({ it.x > 448 && it.z > 448 }),
    GOBLIN_HOLDOUT({ it.x <= 576 && it.z > 448 }),
    MITHRIL_DEPOSITS({ it.x > 448 && it.z <= 576 }),
    MAGMA_FIELDS({ it.y < 80 }),
    ANY({ true });

    fun test(pos: BlockPos) = predicate(pos)
}