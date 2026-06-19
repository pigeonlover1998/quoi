package quoi.api.events.core


class Subscription<T : Event>(
    val eventClass: Class<*>,
    val priority: Int,
    val callback: T.() -> Unit,
) {
    fun unregister() {
        EventManager.unregister(this)
    }

    fun execute(event: Event) {
        @Suppress("UNCHECKED_CAST")
        callback.invoke(event as T)
    }
}