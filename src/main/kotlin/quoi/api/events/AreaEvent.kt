package quoi.api.events

import quoi.api.events.core.UnfilteredEvent
import quoi.api.skyblock.Island

abstract class AreaEvent {
    class Main(val area: Island?) : UnfilteredEvent()
    class Sub(val subarea: String?) : UnfilteredEvent()
}