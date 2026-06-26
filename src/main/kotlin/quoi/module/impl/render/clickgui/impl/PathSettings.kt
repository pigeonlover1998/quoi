package quoi.module.impl.render.clickgui.impl

import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.group.SettingGroup

object PathSettings : SettingGroup(ClickGui, "Pathfinder Settings") {
    val yawStep by slider("Yaw step", 6f, 2f, 10f, desc = "Horizontal density of raycasts. Lower values increase precision but reduce performance.")
    val pitchStep by slider("Pitch step", 7f, 2f, 10f, desc = "Vertical density of raycasts. Lower values increase precision but reduce performance.")
    val hWeight by slider("Guess weight", 6.7, 1.0, 15.0, 0.1, desc = "Higher values make the search much faster.")
    val threads by slider("Threads", 6, 1, 16, desc = "Number of CPU threads to use for simultaneous path expansion.")
    val timeout by slider("Timeout", 670L, 200L, 1000L, 50L, unit = "ms", desc = "Maximum time allowed for the pathfinder to search before giving up.")
}