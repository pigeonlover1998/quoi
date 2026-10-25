package quoi.module.impl.misc.rift.dreadfarm

import quoi.module.impl.misc.rift.Rift
import quoi.module.impl.misc.rift.dreadfarm.impl.*
import quoi.module.settings.group.SettingGroup

@Suppress("unused_expression")
object DreadFarm : SettingGroup(Rift, "Dread farm", subarea = "dreadfarm") {
    init {
        WiltedBerberis
    }
}