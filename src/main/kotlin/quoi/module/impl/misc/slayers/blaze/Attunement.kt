package quoi.module.impl.misc.slayers.blaze

import quoi.api.colour.Colour

private val aa = arrayOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
private val sc = arrayOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")

enum class Attunement(val colour: Colour, val daggers: Array<String>) {
    ASHEN(Colour.MINECRAFT_DARK_GRAY, aa),
    AURIC(Colour.MINECRAFT_YELLOW, aa),
    SPIRIT(Colour.WHITE, sc),
    CRYSTAL(Colour.MINECRAFT_AQUA, sc)
}