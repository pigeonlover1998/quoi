package quoi.api.events

import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import quoi.api.input.MutableInput
import net.minecraft.world.entity.player.Input as ClientInput

abstract class KeyEvent {
    class Press(val key: Int, val scanCode: Int, val modifiers: Int) : CancellableEvent()
    class Release(val keyCode: Int, val scanCode: Int, val modifiers: Int) : CancellableEvent()
    class Input(val clientInput: ClientInput, val input: MutableInput) : Event()
}