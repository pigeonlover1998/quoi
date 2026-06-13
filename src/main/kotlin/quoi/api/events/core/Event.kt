package quoi.api.events.core

open class Event { // todo recode.
    open fun post(): Boolean {
        EventBus.post(this)
        return false
    }
}

abstract class UnfilteredEvent : Event()