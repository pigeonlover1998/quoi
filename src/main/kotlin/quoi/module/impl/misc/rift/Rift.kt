package quoi.module.impl.misc.rift

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.misc.rift.dreadfarm.DreadFarm

@Suppress("unused_expression")
object Rift : Module(
    "Rift",
    desc = "Rift stuff.",
    area = Island.Rift
) {
    init {
        DreadFarm
    }
}