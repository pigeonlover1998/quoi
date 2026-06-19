package quoi.api.events.core

import net.minecraft.network.protocol.Packet
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventManager.register

inline fun <reified T : Event> on(
    priority: Int = 0,
    register: Boolean = true,
    noinline block: T.() -> Unit
): Subscription<T> {
    val sub = Subscription(T::class.java, priority, block)
    if (register) register(sub)
    return sub
}

inline fun <reified T : Event> until(
    priority: Int = 0,
    noinline block: T.() -> Boolean
): Subscription<T> {
    lateinit var sub: Subscription<T>
    sub = on<T>(priority) {
        if (block(this)) sub.unregister()
    }
    return sub
}

inline fun <reified T : Event> once(
    priority: Int = 0,
    noinline block: T.() -> Unit
): Subscription<T> = until<T>(priority) {
    block(this)
    true
}

inline fun <reified T : Event> repeated(
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

@JvmName("onPacket")
inline fun <reified E, reified P : Packet<*>> on(
    priority: Int = 0,
    noinline block: PacketScope<E, P>.() -> Unit
): Subscription<E> where E : Event, E : PacketEvent = on<E>(priority) {
    if (packet is P) block(PacketScope(this, packet as P))
}

@JvmName("untilPacket")
inline fun <reified E, reified P : Packet<*>> until(
    priority: Int = 0,
    noinline block: PacketScope<E, P>.() -> Boolean
): Subscription<E> where E : Event, E : PacketEvent = until<E>(priority) {
    if (packet is P) block(PacketScope(this, packet as P))
    else false
}

@JvmName("oncePacket")
inline fun <reified E, reified P : Packet<*>> once(
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
