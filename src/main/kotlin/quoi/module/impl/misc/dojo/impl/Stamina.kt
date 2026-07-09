package quoi.module.impl.misc.dojo.impl

import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup

// jump in a hole shit
object Stamina : ToggleableGroup(Dojo, "Stamina", subarea = "dojo arena") {


    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.STAMINA
}