package quoi.module.impl.misc.slayers

import net.minecraft.world.entity.LivingEntity
import quoi.api.colour.Colour
import quoi.module.settings.group.ToggleableGroup

interface ISlayer {
    val features: Set<ToggleableGroup>

    val entitiesForRender: List<Pair<LivingEntity, Colour?>>
        get() = emptyList()
}