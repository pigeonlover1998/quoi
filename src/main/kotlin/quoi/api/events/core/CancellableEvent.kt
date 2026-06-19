package quoi.api.events.core

abstract class CancellableEvent : Event() {
    var cancelled: Boolean = false
        private set

    fun cancel() {
        if (!completed) cancelled = true
    }
}