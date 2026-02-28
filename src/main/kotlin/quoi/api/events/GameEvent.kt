package quoi.api.events

import quoi.api.events.core.Event

abstract class GameEvent {
    class Load : Event()
    class Unload : Event()
}