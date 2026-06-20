package quoi.api.events.core

/**
 * An [Event] that [Subscription]s can cancel to stop further dispatch.
 * Once cancelled, lower priority [Subscription]s no longer get called.
 */
abstract class CancellableEvent : Event() {

    /**
     * Whether this event is cancelled
     */
    var cancelled: Boolean = false
        private set

    /**
     * Cancels this event
     * Once cancelled, [Subscription]s with lower priorities will not be executed
     */
    fun cancel() {
        if (!completed) cancelled = true
    }
}