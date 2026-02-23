package quoi.utils

import quoi.QuoiMod.mc
import quoi.mixins.accessors.KeyMappingAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import kotlin.reflect.KClass

inline val sf get() = mc.window.guiScale
inline val width get() = mc.window.width
inline val height get() = mc.window.height

inline val scaledWidth get() = width / sf
inline val scaledHeight get() = height / sf

inline val KeyMapping.key: InputConstants.Key get() = (this as KeyMappingAccessor).boundKey

fun Any?.equalsOneOf(vararg options: Any?): Boolean {
    return options.any { this == it }
}

fun Any?.isOneOf(vararg types: KClass<*>): Boolean {
    return types.any { it.isInstance(this) }
}
