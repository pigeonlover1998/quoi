package quoi.module.impl.dungeon

import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.skyblock.ItemUtils.skyblockId
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

// Kyleen
object CancelInteract : Module("Cancel Interact") {

    private val dungeonsOnly by BooleanSetting("Dungeons only").withDependency { whitelistMode.selected != "Outside Dungeons" }
    private val whitelistMode by SelectorSetting("Ignore whitelist", "Never", arrayListOf("Never", "Always", "Outside Dungeons"))
    private val sneakMode by BooleanSetting("Sneaking only", desc = "for etherwarp/aote only")

    private val teleportItems = setOf(
        "ASPECT_OF_THE_VOID",
        "ASPECT_OF_THE_END",
        //"ETHERWARP_CONDUIT"
    )

    private val interactionWhitelist = setOf<Block>(
        Blocks.LEVER, Blocks.CHEST, Blocks.TRAPPED_CHEST,
        Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.SPRUCE_BUTTON,
        Blocks.BIRCH_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.ACACIA_BUTTON,
        Blocks.DARK_OAK_BUTTON, Blocks.MANGROVE_BUTTON, Blocks.CHERRY_BUTTON,
        Blocks.BAMBOO_BUTTON, Blocks.POLISHED_BLACKSTONE_BUTTON
    )

    private val interactionBlacklist = setOf<Block>(
        Blocks.COBBLESTONE_WALL, Blocks.HOPPER,
        Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE,
        Blocks.ACACIA_FENCE, Blocks.DARK_OAK_FENCE, Blocks.MANGROVE_FENCE, Blocks.CHERRY_FENCE,
        Blocks.BAMBOO_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.WARPED_FENCE, Blocks.CRIMSON_FENCE,
        Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE, Blocks.JUNGLE_FENCE_GATE,
        Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE, Blocks.MANGROVE_FENCE_GATE, Blocks.CHERRY_FENCE_GATE,
        Blocks.BAMBOO_FENCE_GATE, Blocks.WARPED_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE
    )

    private fun shouldIgnoreWhitelist(): Boolean =
        when (whitelistMode.selected) {
            "Always" -> true
            "Outside Dungeons" -> !inDungeons
            else -> false
        }

    private fun shouldCancelWithItem(heldItem: ItemStack): Boolean {
        if (heldItem.`is`(Items.ENDER_PEARL)) return true
        if (heldItem.skyblockId !in teleportItems) return false
        return !sneakMode || player.isSteppingCarefully
    }

    fun cancelInteractHook(blockPos: BlockPos): Boolean {
        if (!enabled) return false
        if (dungeonsOnly && !inDungeons) return false

        val state = level.getBlockState(blockPos)
        if (state.block in interactionWhitelist && !shouldIgnoreWhitelist()) { return false }

        val heldItem = player.mainHandItem
        if (heldItem.isEmpty) return false
        if (shouldCancelWithItem(heldItem)) return true

        return interactionBlacklist.contains(state.block) || state.isAir
    }
}