package quoi.api.events

import quoi.api.events.core.Event
import net.minecraft.world.entity.Entity

abstract class ArrowEvent {
    class Despawn(val arrow: Entity, val owner: Entity, val entitiesHit: ArrayList<Entity>) : Event() // todo
    class Hit(val arrow: Entity, val target: Entity) : Event()
}