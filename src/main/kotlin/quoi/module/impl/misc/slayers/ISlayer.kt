package quoi.module.impl.misc.slayers

import quoi.module.settings.group.ToggleableGroup

interface ISlayer {
    val features: Set<ToggleableGroup>
}