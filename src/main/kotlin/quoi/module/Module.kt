package quoi.module

import quoi.QuoiMod
import quoi.api.commands.QuoiCommand
import quoi.api.events.PacketEvent
import quoi.api.events.core.Event
import quoi.api.events.core.EventBus
import quoi.api.events.core.PacketScope
import quoi.api.events.core.UnfilteredEvent
import quoi.api.input.CatKeys
import quoi.api.skyblock.Island
import quoi.api.skyblock.IslandArea
import quoi.api.skyblock.Location
import quoi.module.settings.AlwaysActive
import quoi.module.settings.Setting
import quoi.module.settings.impl.Keybinding
import quoi.utils.ChatUtils.modMessage
import net.minecraft.network.protocol.Packet

abstract class Module(
    val name: String,
    val area: IslandArea? = null,
    val subarea: String? = null,
    val key: Int = CatKeys.KEY_NONE,
    @Transient val desc: String = "",
    toggled: Boolean = false
) {
    constructor(
        name: String,
        area: Island,
        subarea: String? = null,
        key: Int = CatKeys.KEY_NONE,
        desc: String = "",
        toggled: Boolean = false
    ) : this(name, IslandArea.Base(area), subarea, key, desc, toggled)

    private var isRegistered = false

    val events = mutableListOf<EventBus.EventListener>()

    @Transient
    val category: Category = getCategory(this::class.java) ?: Category.RENDER

    val keybinding: Keybinding = this@Module.key.let { Keybinding(it).apply { onPress = ::onKeybind } }

    var enabled: Boolean = toggled
        private set

    protected inline val mc get() = QuoiMod.mc
    protected inline val level get() = requireNotNull(mc.level) { "tried to access level before world was loaded" } // should never be null in tick events
    protected inline val player get() = requireNotNull(mc.player) { "tried to access player before it was loaded" } // should never be null in tick events

    protected inline val command get() = QuoiCommand.command

    @Transient
    val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    val settings: ArrayList<Setting<*>> = ArrayList()

    open fun onEnable() {
        if (!alwaysActive) onToggle(true)
    }

    open fun onDisable() {
        if (!alwaysActive) onToggle(false)
    }

    open fun onKeybind() {
        toggle()
        toggleMessage()
    }

    fun toggleMessage() {
        modMessage("$name ${if (enabled) "§aenabled" else "§cdisabled"}§r.", name.hashCode())
    }

    fun toggle() {
        enabled = !enabled
        if (enabled) onEnable()
        else onDisable()
    }

    fun addSettings(vararg setArray: Setting<*>) {
        setArray.forEach {
            settings.add(it)
        }
    }

    fun <K : Setting<*>> register(setting: K): K {
        addSettings(setting)
        return setting
    }

    operator fun <K : Setting<*>> K.unaryPlus(): K = register(this)

    fun getSettingByName(name: String?): Setting<*>? {
        for (setting in settings) {
            if (setting.jsonName.equals(name, true) /*|| setting.name.equals(name, true)*/) {
                return setting
            }
        }
        return null
    }

    fun onToggle(state: Boolean) {
        val shouldBeRegistered = state // && inEnvironment()

        if (shouldBeRegistered && !isRegistered) {
            events.forEach { it.add() }
            isRegistered = true
        } else if (!shouldBeRegistered && isRegistered) {
            events.forEach { it.remove() }
            isRegistered = false
        }
    }

    fun inArea() = area?.inBase() ?: true

    fun inSubarea(): Boolean {
        if (subarea == null) return true

        return Location.subarea?.contains(subarea, true) == true
    }

    fun inEnvironment(): Boolean = area?.inArea() ?: true && inSubarea()

    inline fun <reified T : Event> on(priority: Int = 0, noinline cb: T.() -> Unit) {
        events.add(EventBus.on<T>(priority, {
            val event = this
            when (event) {
                is UnfilteredEvent -> if (inArea() && inSubarea()) cb()
                else -> if (inEnvironment()) cb()
            }
        }, false))
    }

    @JvmName("onPacket")
    inline fun <reified E, reified P : Packet<*>> on(
        priority: Int = 0,
        noinline cb: PacketScope<E, P>.() -> Unit
    ) where E : Event, E : PacketEvent {
        events.add(EventBus.on<E, P>(priority, {
            if (inEnvironment()) cb()
        }, false))
    }

    private companion object {
        private fun getCategory(clazz: Class<out Module>): Category? =
            Category.entries.find { clazz.`package`.name.contains(it.name, true) }
    }
}