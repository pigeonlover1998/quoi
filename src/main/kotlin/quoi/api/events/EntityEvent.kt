package quoi.api.events

import quoi.api.colour.Colour
import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.minecraft.world.entity.Entity

abstract class EntityEvent {
    class Join(val entity: Entity) : CancellableEvent() // todo
    class Leave(val entity: Entity, val reason: Entity.RemovalReason) : CancellableEvent()
    class ForceGlow(val entity: Entity) : Event() {
        var isGlowing: Boolean = false
        var glowColour: Colour = Colour.WHITE
            set(value) {
                isGlowing = true
                field = value
            }
    }
//    class Attack(val player: PlayerEntity, val target: Entity) : Event() // todo
//    class Metadata(val packet: EntityTrackerUpdateS2CPacket, val entity: Entity, val name: String) : CancellableEvent() // todo
//    class Spawn(val packet: EntitySpawnS2CPacket, val entity: Entity, val name: String) : CancellableEvent() // todo
//    class Interact(val player: PlayerEntity, val world: World, val hand: Hand, val action: String, val pos: BlockPos? = null) : Event() // todo
}