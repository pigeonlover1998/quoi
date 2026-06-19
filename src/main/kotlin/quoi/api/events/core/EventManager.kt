package quoi.api.events.core

import java.util.concurrent.ConcurrentHashMap

object EventManager {
    private val registries = ConcurrentHashMap<Class<*>, SubscriptionRegistry<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> getRegistry(eventClass: Class<*>): SubscriptionRegistry<T> {
        return registries.computeIfAbsent(eventClass) { SubscriptionRegistry<T>() } as SubscriptionRegistry<T>
    }

    fun <T : Event> post(event: T): Boolean {
        val registry = registries[event::class.java] ?: return false

        event.completed = false

        for (sub in registry.subscriptions) {
            if (event is CancellableEvent && event.cancelled) break

            event.subscription = sub

            try {
                sub.execute(event)
            } catch (e: Exception) {
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