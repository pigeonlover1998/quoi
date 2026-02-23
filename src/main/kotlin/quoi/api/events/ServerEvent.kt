package quoi.api.events

import quoi.api.events.core.Event

abstract class ServerEvent {
    class Connect(val ip: String) : Event()
    class Disconnect(val ip: String) : Event()
}