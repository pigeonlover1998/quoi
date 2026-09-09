package quoi.utils.skyblock.player.container

import quoi.api.events.core.AreaBoundListener
import quoi.module.settings.group.SettingGroup

interface IContainerSettings {
    val startDelay: Pair<Int, Int> get() = 0 to 0
    val clickDelay: Pair<Int, Int> get() = 0 to 0
    val endDelay: Pair<Int, Int> get() = 0 to 0
    val invWalk: Boolean get() = true
    val silent: Boolean get() = true
}

class ContainerSettings(parent: AreaBoundListener) : SettingGroup(parent, "Settings"), IContainerSettings { // todo add other shit, make an option to render it without dropdown
    override val startDelay by rangeSlider("Start delay", 1 to 2, 0, 5, unit = "t")
    override val clickDelay by rangeSlider("Click delay", 1 to 2, 0, 5, unit = "t")
    override val endDelay by rangeSlider("End delay", 1 to 2, 0, 5, unit = "t")
    override val invWalk by switch("Inventory walk")
    override val silent by switch("Silent GUI")
}

val CONTAINER_ZERO = object : IContainerSettings {}