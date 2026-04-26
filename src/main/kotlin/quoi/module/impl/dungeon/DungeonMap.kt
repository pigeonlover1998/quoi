package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.MapConfig
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.renderMap
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ui.hud.impl.TextHud

object DungeonMap : Module(
    "Dungeon Map",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
//    private val renderHidden by switch("Render hidden rooms")

    private val for_speds by text(
        """
            &c! &rTHIS MODULE IS NOT FINISHED &c!
            &c!! &rDO NOT USE &c!!
        """.trimIndent()
    )

    val shadow by switch("Shadow", true).asParent()
    val font by segmented("Font", TextHud.HudFont.Minecraft).asParent()
    val fontScale by slider("Font scale", 1f, 0.5f, 3f, 0.05f)

    val roomRadius by slider("Room radius", 5f, 1f, 10f, 1f)
    val darkenMultiplier by slider("Darken multiplier", 0.4f, 0.0f, 1.0f, 0.1f)

    val bgColour by colourPicker("Background colour", Colour.RGB(38, 85, 12, 0.8f), allowAlpha = true)
    val border by switch("Border")
    val borderThickness by slider("Border thickness", 2f, 1f, 10f, 0.5f, unit = "px").childOf(::border)
    val borderColour by colourPicker("Border colour", Colour.RGB(255, 255, 255), allowAlpha = true).childOf(::border)

    private val roomColour by text("Room colours")
    val normalRoom by colourPicker("Normal", Colour.RGB(107, 58, 17)).childOf(::roomColour)
    val entranceRoom by colourPicker("Entrance", Colour.RGB(81, 255, 0)).childOf(::roomColour)
    val puzzleRoom by colourPicker("Puzzle", Colour.RGB(117, 0, 133)).childOf(::roomColour)
    val trapRoom by colourPicker("Trap", Colour.RGB(216, 127, 51)).childOf(::roomColour)
    val miniRoom by colourPicker("Mini boss", Colour.RGB(254, 223, 0)).childOf(::roomColour)
    val bloodRoom by colourPicker("Blood", Colour.RGB(255, 0, 0)).childOf(::roomColour)
    val fairyRoom by colourPicker("Fairy", Colour.RGB(224, 0, 255)).childOf(::roomColour)
    val rareRoom by colourPicker("Rare", Colour.MINECRAFT_YELLOW).childOf(::roomColour)
//    val unknownRoom by colourPicker("Unknown", Colour.MINECRAFT_GRAY).childOf(::roomColour)

    private val doorColour by text("Door colours")
    val normalDoor by colourPicker("Normal", Colour.RGB(255, 132, 0)).json("Normal door").childOf(::doorColour)
    val witherDoor by colourPicker("Wither", Colour.RGB(40, 238, 1)).json("Wither door").childOf(::doorColour)
    val bloodDoor by colourPicker("Blood", Colour.RGB(0, 231, 169)).json("Blood door").childOf(::doorColour)
    val entranceDoor by colourPicker("Entrance", Colour.RGB(81, 255, 0)).json("Entrance door").childOf(::doorColour)

    val showHeads by switch("Show player heads")
    val showOwnHead by switch("Show own head").childOf(::showHeads)
    val iconScale by slider("Icon scale", 1f, 0.1f, 3.0f, 0.1f).childOf(::showHeads)
    val iconBorder by switch("Border").json("Icon border").childOf(::showHeads)
    val classColour by switch("Class border colour").childOf(::iconBorder)
    val iconBorderColour by colourPicker("Border colour", Colour.BLACK).json("Icon border colour").childOf(::iconBorder) { !classColour }
    val iconBorderThickness by slider("Border thickness", 2, 1, 10, unit = "px").json("Icon border thickness").childOf(::iconBorder)

    val showNames by switch("Show names")
    val whenLeap by switch("Only when leap").childOf(::showNames)
    val nameScale by slider("Name scale", 0.8f, 0.1f, 3.0f, 0.1f).childOf(::showNames)

    private val map by hud("Dungeon map", toggleable = false) {
        visibleIf { inClear }
        renderMap(config = MapConfig(font = font.selected.get()))
    }.withSettings(
        ::shadow, ::font, ::fontScale,
//        ::roomRadius, ::darkenMultiplier,
//        ::bgColour, ::border, ::borderThickness, ::borderColour,
//        ::roomColour, ::normalRoom, ::entranceRoom, ::puzzleRoom, ::trapRoom, ::miniRoom, ::bloodRoom, :: fairyRoom, ::rareRoom,
//        ::doorColour, ::normalDoor, ::witherDoor, ::bloodDoor, ::entranceDoor,
//        ::showHeads, ::showOwnHead, ::iconScale, ::iconBorder, ::classColour, ::iconBorderColour, ::iconBorderThickness,
//        ::showNames, ::whenLeap, ::nameScale
    ).setting()
}