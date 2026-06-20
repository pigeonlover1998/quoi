package quoi.api.events.core

import net.minecraft.network.protocol.Packet
import quoi.api.events.PacketEvent

/**
 * A wrapper scope providing access to a specific [Packet]
 * inside packet filtered event subscriptions.
 */
class PacketScope<E : PacketEvent, P : Packet<*>>(val event: E, val packet: P) {
    fun cancel() {
        if (event is CancellableEvent) event.cancel()
    }

    fun unregister() {
        (event as Event).unregister()
    }
}