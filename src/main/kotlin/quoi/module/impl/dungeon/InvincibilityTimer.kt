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
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import quoi.utils.ui.textPair

object InvincibilityTimer : Module(
    "Invincibility Timer",
    desc = "Gives visual information about your invincibility times."
) {
    private val dungeonOnly by BooleanSetting("Dungeons only", desc = "Active in dungeons only.")
    private val bossOnly by BooleanSetting("Boss only", desc = "Active in boss room only.")
//    private val serverTicks by BooleanSetting("Use server ticks", desc = "Uses server ticks instead of real time.")
    val mageReduction by BooleanSetting("Mage reduction", desc = "Accounts for mage cooldown reduction.")
    val cataLevel by NumberSetting("Catacombs level", 0, 0, 50, desc = "Catacombs level for Bonzo's mask ability.")

    private val hud by TextHud("Invincibility timer", Colour.PINK, toggleable = false) {
        visibleIf { this@InvincibilityTimer.enabled && inSkyblock && (!bossOnly || inBoss) && (!dungeonOnly || inDungeons || bossOnly) }
        column {
            SkyblockPlayer.InvincibilityType.entries.forEach { type ->
                val (col, time) = type.getTime()
                row(gap = 1.px) {
                    text(
                        string = "◼",
                        font = minecraftFont,
                        size = 18.px,
                        colour = colour { if (type.shouldDot()) colour.rgb else Colour.TRANSPARENT.rgb },
                        pos = at(y = Centre - 2.px)
                    )
                    textPair(
                        string = "${type.displayName}:",
                        supplier = { time() },
                        labelColour = colour,
                        valueColour = col(),
                        shadow = shadow
                    )
                }
            }
        }
    }.withSettings(::dungeonOnly, ::bossOnly, ::mageReduction, ::cataLevel).setting()
}