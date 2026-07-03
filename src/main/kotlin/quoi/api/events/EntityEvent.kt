package quoi.api.events

import quoi.api.colour.Colour
import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.minecraft.world.entity.Entity

abstract class EntityEvent {
    class Attack(val entity: Entity) : CancellableEvent()
    class Spawn(val entity: Entity) : Event()
    class Despawn(val entity: Entity, val reason: Entity.RemovalReason) : CancellableEvent()
    class ForceGlow(val entity: Entity) : Event() {
        var isGlowing: Boolean = false
        var glowColour: Colour = Colour.WHITE
            set(value) {
                isGlowing = true
                field = value
            }
    }
}