package quoi.module.impl.dungeon

import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.M7Phases
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.equalsOneOf
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

// Kyleen
object FuckDiorite : Module(
    "Fuck Diorite",
    desc = "Replaces the pillars in the storm fight with glass.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val GLASS_STATE = Blocks.GLASS.defaultBlockState()

    private val STAINED_GLASS_BLOCKS = arrayOf(
        Blocks.WHITE_STAINED_GLASS,
        Blocks.ORANGE_STAINED_GLASS,
        Blocks.MAGENTA_STAINED_GLASS,
        Blocks.LIGHT_BLUE_STAINED_GLASS,
        Blocks.YELLOW_STAINED_GLASS,
        Blocks.LIME_STAINED_GLASS,
        Blocks.PINK_STAINED_GLASS,
        Blocks.GRAY_STAINED_GLASS,
        Blocks.LIGHT_GRAY_STAINED_GLASS,
        Blocks.CYAN_STAINED_GLASS,
        Blocks.PURPLE_STAINED_GLASS,
        Blocks.BLUE_STAINED_GLASS,
        Blocks.BROWN_STAINED_GLASS,
        Blocks.GREEN_STAINED_GLASS,
        Blocks.RED_STAINED_GLASS,
        Blocks.BLACK_STAINED_GLASS
    )

    private val pillarBasedColor by BooleanSetting("Pillar based color", true, desc = "Swaps the diorite in the pillar to the corresponding glass color.")
    private val colorIndex by SelectorSetting("Color", "None", arrayListOf("NONE", "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"), desc = "Color for the stained glass.").withDependency { !pillarBasedColor }

    private val pillars = arrayOf(BlockPos(46, 169, 41), BlockPos(46, 169, 65), BlockPos(100, 169, 65), BlockPos(100, 169, 41))
    private val pillarColors = intArrayOf(5, 4, 10, 14)

    private val coordinates: Array<Set<BlockPos>> = Array(4) { pillarIndex ->
        val pillar = pillars[pillarIndex]
        buildSet {
            for (dx in (pillar.x - 3)..(pillar.x + 3))
                for (dy in pillar.y..(pillar.y + 37))
                    for (dz in (pillar.z - 3)..(pillar.z + 3))
                        add(BlockPos(dx, dy, dz))
        }
    }

    init {
        on<TickEvent.End> {
            if (Dungeon.getF7Phase() == M7Phases.P2) replaceDiorite()
        }
    }

    private fun replaceDiorite() {
        for ((index, coordinateSet) in coordinates.withIndex()) {
            for (pos in coordinateSet) {
                val state = level.getBlockState(pos) ?: return

                if (state.block.equalsOneOf(Blocks.DIORITE, Blocks.POLISHED_DIORITE)) {
                    setGlass(pos, index)
                }
            }
        }
    }

    private fun setGlass(pos: BlockPos, pillarIndex: Int) {
        val newState = when {
            pillarBasedColor -> STAINED_GLASS_BLOCKS[pillarColors[pillarIndex]].defaultBlockState()
            colorIndex.index != 0 -> STAINED_GLASS_BLOCKS[colorIndex.index - 1].defaultBlockState()
            else -> GLASS_STATE
        }

        level.setBlock(pos, newState, 3)
    }
}