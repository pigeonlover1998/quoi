package quoi.module.impl.misc.slayers.blaze

import quoi.api.skyblock.location.Island
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.SettingGroup

// todo auto terracotta fire thing dodge, auto aim. I am too lazy to do it myself.
@Suppress("unused_expression")
object BlazeSlayer : SettingGroup(Slayers, "Blaze", area = Island.CrimsonIsle, subarea = "smoldering tomb") {
    init {
        AutoAttune
    }
}