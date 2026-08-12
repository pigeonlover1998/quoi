package quoi.utils.skyblock.player.container

import quoi.api.events.core.AreaBoundListener
import quoi.module.settings.group.SettingGroup

class ContainerSettings(parent: AreaBoundListener) : SettingGroup(parent, "Settings") { // todo add other shit, make an option to render it without dropdown
    val startDelay by rangeSlider("Start delay", 1 to 2, 0, 5, unit = "t")
    val clickDelay by rangeSlider("Click delay", 1 to 2, 0, 5, unit = "t")
    val endDelay by rangeSlider("End delay", 1 to 2, 0, 5, unit = "t")
    val invWalk by switch("Inventory walk")
    val silent by switch("Silent GUI")
}