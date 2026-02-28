package quoi.api.events

import quoi.api.events.core.CancellableEvent

abstract class MouseEvent {
    class Click(val button: Int, val state: Boolean) : CancellableEvent()
    class Scroll(val horizontal: Double, val vertical: Double) : CancellableEvent()
    class Move(val mx: Double, val my: Double) : CancellableEvent()
}
