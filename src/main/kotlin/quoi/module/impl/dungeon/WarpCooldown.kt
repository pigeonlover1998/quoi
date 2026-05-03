package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.utils.StringUtils
import quoi.utils.ui.textPair

object WarpCooldown : Module(
    "Warp Cooldown",
    desc = "Dungeon warp cooldown display"
) {
    private val hud by textHud("Warp cooldown", toggleable = false) {
        visibleIf { Dungeon.warpCooldown != 0L }

        textPair(
            string = "Warp:",
            supplier = { StringUtils.formatTime(Dungeon.warpCooldown) },
            labelColour = colour,
            valueColour = Colour.WHITE,
            shadow = shadow,
            font = font
        )
    }.setting()
}