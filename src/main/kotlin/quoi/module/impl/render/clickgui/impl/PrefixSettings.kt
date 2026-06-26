package quoi.module.impl.render.clickgui.impl

import quoi.api.colour.Colour
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.group.SettingGroup

object PrefixSettings : SettingGroup(ClickGui, "Prefix settings") {
    val prefixText by textInput("Prefix", "quoi!")
    val prefixColour by colourPicker("Colour", Colour.GREEN)
    val bracketsColour by colourPicker("Brackets", Colour.WHITE)
}