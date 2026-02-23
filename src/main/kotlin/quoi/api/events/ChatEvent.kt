package quoi.api.events

import quoi.api.events.core.CancellableEvent
import net.minecraft.network.chat.Component

abstract class ChatEvent {
    class Receive(val message: String, val text: Component, val id: Int) : CancellableEvent() {
        class Post(val message: String, val text: Component, val id: Int) : CancellableEvent()
    }
    class Packet(val message: String, val text: Component) : CancellableEvent()
    class Sent(val message: String, val isCommand: Boolean) : CancellableEvent()
    class ActionBar(val message: String, val text: Component) : CancellableEvent()
}