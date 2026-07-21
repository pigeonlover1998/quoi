package quoi.api.events.core

import net.minecraft.network.protocol.Packet
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventManager.register
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
    acceptCancelled: Boolean = false,
    register: Boolean = true,
    noinline block: T.() -> Unit
): Subscription<T> {
    val sub = Subscription(this, T::class.java, priority, acceptCancelled, block)
    if (register) register(sub)
    return sub
}

/**
 * Subscribes to [Event] until [block] returns true
 */
inline fun <reified T : Event> EventListener.until(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: T.() -> Boolean
): Subscription<T> = on<T>(priority, acceptCancelled) {
    if (!this@until.running || block(this)) {
        unregister()
    }
}

/**
 * Subscribes to [Event] for one dispatch
 */
inline fun <reified T : Event> EventListener.once(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: T.() -> Unit
): Subscription<T> = until<T>(priority, acceptCancelled) {
    block(this)
    true
}

/**
 * Subscribes to [Event] for [times] dispatches
 */
inline fun <reified T : Event> EventListener.repeated(
    times: Int,
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: T.() -> Unit
): Subscription<T> {
    require(times > 0) { "times must be > 0" }
    var i = 0
    return until<T>(priority, acceptCancelled) {
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
    acceptCancelled: Boolean = false,
    noinline block: PacketScope<E, P>.() -> Unit
): Subscription<E> where E : Event, E : PacketEvent = on<E>(priority, acceptCancelled) {
    if (packet is P) block(PacketScope(this, packet as P))
}

/**
 * [until] filtered to packets
 */
@JvmName("untilPacket")
inline fun <reified E, reified P : Packet<*>> EventListener.until(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: PacketScope<E, P>.() -> Boolean
): Subscription<E> where E : Event, E : PacketEvent = until<E>(priority, acceptCancelled) {
    if (packet is P) block(PacketScope(this, packet as P))
    else false
}

/**
 * [once] filtered to packets
 */
@JvmName("oncePacket")
inline fun <reified E, reified P : Packet<*>> EventListener.once(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: PacketScope<E, P>.() -> Unit
): Subscription<E> where E : Event, E : PacketEvent = once<E>(priority, acceptCancelled) {
    if (packet is P) block(PacketScope(this, packet as P))
}

/**
 * Subscribes to every dispatch of [Event] and tracks a value
 *
 * [block] is executed on every event dispatch receiving the previous value and must return the new value
 */
inline fun <reified E : Event, V> EventListener.trackedBy(
    defaultValue: V,
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: E.(prev: V) -> V
): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
    @Volatile
    private var value = defaultValue

    init {
        on<E>(priority, acceptCancelled) {
            value = block(this, value)
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        this.value = value
    }
}

/**
 * [trackedBy] filtered to packets
 */
@JvmName("trackedByPacket")
inline fun <reified E, reified P : Packet<*>, V> EventListener.trackedBy(
    defaultValue: V,
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    noinline block: PacketScope<E, P>.(prev: V) -> V
): ReadWriteProperty<Any?, V> where E : Event, E : PacketEvent = object : ReadWriteProperty<Any?, V> {
    @Volatile
    private var value = defaultValue

    init {
        on<E, P>(priority, acceptCancelled) {
            value = block(value)
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        this.value = value
    }
}
