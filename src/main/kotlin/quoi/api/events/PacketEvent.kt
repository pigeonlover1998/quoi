package quoi.api.events

import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.minecraft.network.protocol.Packet

interface PacketEvent {
    val packet: Packet<*>
    class Received(override val packet: Packet<*>) : CancellableEvent(), PacketEvent
    class Sent(override val packet: Packet<*>) : CancellableEvent(), PacketEvent
    class ReceivedPost(override val packet: Packet<*>) : Event(), PacketEvent
}