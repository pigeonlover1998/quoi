package quoi.api.events.core

/**
 * A single registration of a handler for [Event]s
 *
 * Created via the [on], [until], [once], or [repeated] extensions
 *
 * @param listener the owner of this subscription; gates dispatch via [EventListener.running] or [EventListener.shouldHandle]
 * @param eventClass the runtime [Class] o the event being listened to
 * @param priority the execution order pirority (see [Priority]). higher values run first
 * @param callback the handler invoked with the event instance as its receiver
 */
class Subscription<T : Event>(
    val listener: EventListener,
    val eventClass: Class<*>,
    val priority: Int,
    val callback: T.() -> Unit,
) {
    /**
     * Removes this subscription so it no longer receives events
     */
    fun unregister() {
        EventManager.unregister(this)
    }
}