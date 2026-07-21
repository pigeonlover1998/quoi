package quoi.api.events.core

import quoi.QuoiMod.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Central dispatcher.
 * Owns one [SubscriptionRegistry] per event class.
 */
object EventManager {
    private val registries = ConcurrentHashMap<Class<*>, SubscriptionRegistry<*>>()

    /**
     * Gets registry for [eventClass]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> getRegistry(eventClass: Class<*>): SubscriptionRegistry<T> {
        return registries.computeIfAbsent(eventClass) { SubscriptionRegistry<T>() } as SubscriptionRegistry<T>
    }

    /**
     * Dispatches [event] to its [Subscription]s in [Subscription.priority] order skipping any that aren't
     * running or eligible. Stops early if a [CancellableEvent] gets cancelled.
     *
     * @return `true` if the event is [CancellableEvent] and was cancelled. otherwise `false`
     */
    fun <T : Event> post(event: T): Boolean {
        val registry = registries[event::class.java] ?: return false

        event.completed = false

        for (sub in registry.subscriptions) {
            if (event is CancellableEvent && event.cancelled && !sub.acceptCancelled) continue
            if (!sub.listener.running) continue
            if (!sub.listener.shouldHandle(event)) continue

            event.subscription = sub

            try {
                @Suppress("UNCHECKED_CAST")
                (sub.callback as T.() -> Unit).invoke(event)
            } catch (e: Exception) {
                logger.error("Err in ${sub.listener::class.simpleName} ${event::class.simpleName}", e)
                e.printStackTrace()
            }
        }

        event.subscription = null
        event.completed = true
        return if (event is CancellableEvent) event.cancelled else false
    }

    @Suppress("UNCHECKED_CAST")
    fun register(sub: Subscription<*>) {
        getRegistry<Event>(sub.eventClass).add(sub as Subscription<Event>)
    }

    @Suppress("UNCHECKED_CAST")
    fun unregister(sub: Subscription<*>) {
        (registries[sub.eventClass] as? SubscriptionRegistry<Event>)?.remove(sub as Subscription<Event>)
    }
}