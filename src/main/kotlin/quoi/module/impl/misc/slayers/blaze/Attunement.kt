package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

private val aa = arrayOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
private val sc = arrayOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")

enum class Attunement(val sword: Item, val daggers: Array<String>) {
    ASHEN(Items.STONE_SWORD, aa),
    AURIC(Items.GOLDEN_SWORD, aa),
    SPIRIT(Items.IRON_SWORD, sc),
    CRYSTAL(Items.DIAMOND_SWORD, sc)
}