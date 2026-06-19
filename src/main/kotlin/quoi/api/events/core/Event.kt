package quoi.api.events.core

// todo clean up all events and usages some day.
abstract class Event {

    var subscription: Subscription<*>? = null

    var completed: Boolean = false

    fun unsubscribe() {
        subscription?.let { EventManager.unregister(it) }
    }

    open fun post(): Boolean {
        return EventManager.post(this)
    }
}

abstract class UnfilteredEvent : Event()