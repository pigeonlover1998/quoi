package quoi.utils

import quoi.QuoiMod.mc
import kotlin.reflect.KClass

inline val sf get() = mc.window.guiScale
inline val width get() = mc.window.width
inline val height get() = mc.window.height

inline val scaledWidth get() = width / sf
inline val scaledHeight get() = height / sf

inline val level get() = requireNotNull(mc.level) { "tried to access level before world was loaded" }
inline val player get() = requireNotNull(mc.player) { "tried to access player before it was loaded" }
inline val connection get() = requireNotNull(mc.connection) { "mc.connection is null" }
inline val gameMode get() = requireNotNull(mc.gameMode) { "mc.gameMode is null" }
inline val inGame get() = mc.level != null && mc.player != null

fun Any?.equalsOneOf(vararg options: Any?): Boolean {
    return options.any { this == it }
}

fun Any?.isOneOf(vararg types: KClass<*>): Boolean {
    return types.any { it.isInstance(this) }
}

inline fun <K, V> MutableMap<K, V>.removeIf(predicate: (K, V) -> Boolean) {
    val iterator = this.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (predicate(entry.key, entry.value)) iterator.remove()
    }
}
