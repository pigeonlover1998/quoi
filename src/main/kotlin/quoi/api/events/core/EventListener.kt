package quoi.api.events.core

import net.minecraft.network.protocol.Packet
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventManager.register

/**
 * Anything that can own [Subscription].
 */
interface EventListener {

    /**
     * Whether this listener should currently receive events. Checked before [shouldHandle]
     */
    val running: Boolean
        get() = parent()?.running ?: true

    /**
     * Parent listener to inherit [running] and [shouldHandle] from
     */
    fun parent(): EventListener? = null

    /**
     * Extra per event check. Runs after [running]
     */
    fun shouldHandle(event: Event): Boolean = parent()?.shouldHandle(event) ?: true
}

/**
 * Subscribes to every dispatch of [Event]
 */
inline fun <reified T : Event> EventListener.on(
    priority: Int = 0,
    register: Boolean = true,
    noinline block: T.() -> Unit
): Subscription<T> {
    val sub = Subscription(this, T::class.java, priority, block)
    if (register) register(sub)
    return sub
}

/**
 * Subscribes to [Event] until [block] returns true
 */
inline fun <reified T : Event> EventListener.until(
    priority: Int = 0,
    noinline block: T.() -> Boolean
): Subscription<T> = on<T>(priority) {
    if (!this@until.running || block(this)) {
        unregister()
    }
}

/**
 * Subscribes to [Event] for one dispatch
 */
inline fun <reified T : Event> EventListener.once(
    priority: Int = 0,
    noinline block: T.() -> Unit
): Subscription<T> = until<T>(priority) {
    block(this)
    true
}

/**
 * Subscribes to [Event] for [times] dispatches
 */
inline fun <reified T : Event> EventListener.repeated(
    times: Int,
    priority: Int = 0,
    noinline block: T.() -> Unit
): Subscription<T> {
    require(times > 0) { "times must be > 0" }
    var i = 0
    return until<T>(priority) {
        block(this)
        ++i >= times
    }
}

/**
 * [on] filtered to packets
 */
@JvmName("onPacket")
inline fun <reified E, reified P : Packet<*>> EventListener.on(
    priority: Int = 0,
    noinline block: PacketScope<E, P>.() -> Unit
): Subscription<E> where E : Event, E : PacketEvent = on<E>(priority) {
    if (packet is P) block(PacketScope(this, packet as P))
}

/**
 * [until] filtered to packets
 */
@JvmName("untilPacket")
inline fun <reified E, reified P : Packet<*>> EventListener.until(
    priority: Int = 0,
    noinline block: PacketScope<E, P>.() -> Boolean
): Subscription<E> where E : Event, E : PacketEvent = until<E>(priority) {
    if (packet is P) block(PacketScope(this, packet as P))
    else false
}

/**
 * [once] filtered to packets
 */
@JvmName("oncePacket")
inline fun <reified E, reified P : Packet<*>> EventListener.once(
    priority: Int = 0,
    noinline block: PacketScope<E, P>.() -> Unit
): Subscription<E> where E : Event, E : PacketEvent = once<E>(priority) {
    if (packet is P) block(PacketScope(this, packet as P))
}
/*
inline fun <reified E : Event, V> trackedBy(
    defaultValue: V,
    priority: Int = 0,
    noinline block: (event: E, prev: V) -> V
): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
    @Volatile
    private var value = defaultValue

    init {
        on<E>(priority) {
            value = block(this, value)
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        this.value = value
    }
}*/
