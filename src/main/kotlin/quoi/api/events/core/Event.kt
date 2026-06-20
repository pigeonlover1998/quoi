package quoi.api.events.core

import quoi.module.Module

// todo clean up all events and usages some day.

/**
 * Base class for everything that can be posted through [EventManager]
 */
abstract class Event {

    /**
     * The [Subscription] currently handling this event.
     * Used by [unregister]
     */
    var subscription: Subscription<*>? = null

    /**
     * True once [EventManager] has finished dispatching this event to every [Subscription]
     */
    var completed: Boolean = false

    /**
     * Unregisters the [Subscription] currently handling this event.
     */
    fun unregister() {
        subscription?.let { EventManager.unregister(it) }
    }

    /**
     * Dispatches this event to every [Subscription]
     *
     * @return `true` if the event is [CancellableEvent] and was cancelled. otherwise `false`
     */
    fun post(): Boolean {
        return EventManager.post(this)
    }
}

/**
 * An event that skips [Module]'s [Module.area] and [Module.subarea] filtering and always gets delivered
 */
abstract class UnfilteredEvent : Event()