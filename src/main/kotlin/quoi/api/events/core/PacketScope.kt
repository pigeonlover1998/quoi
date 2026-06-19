package quoi.api.events.core

import net.minecraft.network.protocol.Packet
import quoi.api.events.PacketEvent

class PacketScope<E : PacketEvent, P : Packet<*>>(val event: E, val packet: P) {
    fun cancel() {
        if (event is CancellableEvent) event.cancel()
    }

    fun unsubscribe() {
        (event as Event).unsubscribe()
    }
}