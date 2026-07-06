package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import quoi.api.colour.Colour

private val aa = arrayOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
private val sc = arrayOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")

enum class Attunement(val sword: Item, val colour: Colour, val daggers: Array<String>) {
    ASHEN(Items.STONE_SWORD, Colour.MINECRAFT_DARK_GRAY, aa),
    AURIC(Items.GOLDEN_SWORD, Colour.MINECRAFT_YELLOW, aa),
    SPIRIT(Items.IRON_SWORD, Colour.WHITE, sc),
    CRYSTAL(Items.DIAMOND_SWORD, Colour.MINECRAFT_AQUA, sc)
}