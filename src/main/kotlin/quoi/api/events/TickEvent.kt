package quoi.api.events

import quoi.api.events.core.Event

abstract class TickEvent {
    class Start() : Event()
    class End() : Event()
    class Server(val ticks: Int) : Event()
}