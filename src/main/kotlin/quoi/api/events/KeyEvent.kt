package quoi.api.events

import quoi.api.events.core.CancellableEvent

abstract class KeyEvent {
    class Press(val key: Int, val scanCode: Int, val modifiers: Int) : CancellableEvent()
    class Release(val keyCode: Int, val scanCode: Int, val modifiers: Int) : CancellableEvent()
}