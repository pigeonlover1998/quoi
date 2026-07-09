package quoi.module.impl.misc.dojo

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

enum class DojoType(val block: Block = Blocks.CHISELED_STONE_BRICKS) {
    FORCE(Blocks.LIGHT_GRAY_STAINED_GLASS),
    STAMINA,
    MASTERY,
    DISCIPLINE,
    SWIFTNESS,
    CONTROL,
    TENACITY(Blocks.POLISHED_ANDESITE),
    NONE;

    companion object {
        fun fromString(string: String): DojoType =
            entries.firstOrNull { string.contains(it.name) } ?: NONE
    }
}