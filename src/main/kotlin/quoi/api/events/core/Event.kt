package quoi.api.events.core

open class Event {
    open fun post(): Boolean {
        EventBus.post(this)
        return false
    }
}

abstract class UnfilteredEvent : Event()