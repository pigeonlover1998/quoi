package quoi.module.impl.dungeon

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.px
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.utils.ui.textPair

object InvincibilityTimer : Module(
    "Invincibility Timer",
    desc = "Gives visual information about your invincibility times."
) {
    private val dungeonOnly by switch("Dungeons only", desc = "Active in dungeons only.")
    private val bossOnly by switch("Boss only", desc = "Active in boss room only.")
    val mageReduction by switch("Mage reduction", desc = "Accounts for mage cooldown reduction.")
    val cataLevel by slider("Catacombs level", 0, 0, 50, desc = "Catacombs level for Bonzo's mask ability.")

    @Suppress("unused")
    private val hud by textHud("Invincibility timer", Colour.PINK, toggleable = false) {
        visibleIf { inSkyblock && (!bossOnly || inBoss) && (!dungeonOnly || inDungeons || bossOnly) }
        column {
            SkyblockPlayer.InvincibilityType.entries.forEach { type ->
                row(gap = 1.px) {
                    text(
                        string = "◼",
                        font = font,
                        size = 18.px,
                        colour = colour { if (type.shouldDot()) colour.rgb else Colour.TRANSPARENT.rgb },
                        pos = at(y = Centre - 3.px),
                    )
                    textPair(
                        string = "${type.displayName}:",
                        supplier = { type.getTime() },
                        labelColour = colour,
                        shadow = shadow,
                        font = font
                    )
                }
            }
        }
    }.withSettings(::dungeonOnly, ::bossOnly, ::mageReduction, ::cataLevel).setting()
}