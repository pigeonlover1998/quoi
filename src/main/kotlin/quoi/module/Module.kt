package quoi.module

import quoi.QuoiMod
import quoi.api.commands.QuoiCommand
import quoi.api.events.PacketEvent
import quoi.api.events.core.Event
import quoi.api.events.core.EventManager
import quoi.api.events.core.PacketScope
import quoi.api.events.core.UnfilteredEvent
import quoi.api.input.CatKeys
import quoi.api.skyblock.Island
import quoi.api.skyblock.IslandArea
import quoi.api.skyblock.Location
import quoi.module.settings.Setting
import quoi.module.settings.impl.Keybinding
import quoi.utils.ChatUtils.modMessage
import net.minecraft.network.protocol.Packet
import quoi.annotations.AlwaysActive
import quoi.api.events.core.Subscription
import quoi.module.settings.SettingsDSL
import quoi.utils.ui.hud.HudDSL

abstract class Module(
    val name: String,
    val area: IslandArea? = null,
    val subarea: String? = null,
    val key: Int = CatKeys.KEY_NONE,
    @Transient val desc: String = "",
    toggled: Boolean = false,
    val tag: Tag = Tag.NONE
) : SettingsDSL(), HudDSL {
    constructor(
        name: String,
        area: Island,
        subarea: String? = null,
        key: Int = CatKeys.KEY_NONE,
        desc: String = "",
        toggled: Boolean = false,
        tag: Tag = Tag.NONE
    ) : this(name, IslandArea.Base(area), subarea, key, desc, toggled, tag)

    var isRegistered = false

    protected val subscriptions = mutableListOf<Subscription<*>>()

    @Transient
    val category: Category = getCategory(this::class.java) ?: Category.RENDER

    val keybinding: Keybinding = this@Module.key.let { Keybinding(it).apply { onPress = ::onKeybind } }  // todo on press/release/hold

    var enabled: Boolean = toggled
        private set

    protected inline val mc get() = QuoiMod.mc
    protected inline val level get() = requireNotNull(QuoiMod.mc.level) { "tried to access level before world was loaded" } // should never be null in tick events
    protected inline val player get() = requireNotNull(QuoiMod.mc.player) { "tried to access player before it was loaded" } // should never be null in tick events

    protected inline val command get() = QuoiCommand.command

    @Transient
    val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    init {
        if (alwaysActive) onToggle(true)
    }

    val settings: ArrayList<Setting<*>> = ArrayList()

    open fun onEnable() {
        if (!alwaysActive) onToggle(true)
    }

    open fun onDisable() {
        if (!alwaysActive) onToggle(false)
    }

    open fun onKeybind() {
        if (mc.screen != null) return
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

    override fun <K : Setting<*>> register(setting: K): K {
        addSettings(setting)
        return setting
    }

//    operator fun <K : Setting<*>> K.unaryPlus(): K = register(this)

    fun getSettingByName(name: String?): Setting<*>? {
        for (setting in settings) {
            if (setting.jsonName.equals(name, true) /*|| setting.name.equals(name, true)*/) {
                return setting
            }
        }
        return null
    }

    fun onToggle(state: Boolean) {
        val shouldBeRegistered = state || alwaysActive

        if (shouldBeRegistered && !isRegistered) {
            subscriptions.toList().forEach { EventManager.register(it) }
            isRegistered = true
        } else if (!shouldBeRegistered && isRegistered) {
            subscriptions.toList().forEach { EventManager.unregister(it) }
            isRegistered = false
        }
    }

    fun inArea() = area?.inBase() ?: true

    fun inSubarea(): Boolean {
        if (subarea == null) return true

        return Location.subarea?.contains(subarea, true) == true
    }

    fun inEnvironment(): Boolean = (area?.inArea() ?: true) && inSubarea()

    protected inline fun <reified T : Event> on(priority: Int = 0, noinline cb: T.() -> Unit): Subscription<T> {
        val sub = Subscription<T>(T::class.java, priority) {
            val event = this
            when (event) {
                is UnfilteredEvent -> if (inArea() && inSubarea()) cb()
                else -> if (inEnvironment()) cb()
            }
        }
        subscriptions.add(sub)
        if (alwaysActive || enabled) {
            EventManager.register(sub)
            isRegistered = true
        }
        return sub
    }

    @JvmName("onPacket")
    protected inline fun <reified E, reified P : Packet<*>> on(
        priority: Int = 0,
        noinline cb: PacketScope<E, P>.() -> Unit
    ): Subscription<E> where E : Event, E : PacketEvent {
        val sub = Subscription<E>(E::class.java, priority) {
            if (!inEnvironment()) return@Subscription
            if (packet !is P) return@Subscription
            cb(PacketScope(this, packet as P))
        }
        subscriptions.add(sub)
        if (alwaysActive || enabled) {
            EventManager.register(sub)
            isRegistered = true
        }
        return sub
    }

    protected inline fun <reified T : Event> until(priority: Int = 0, noinline cb: T.() -> Boolean): Subscription<T> {
        lateinit var sub: Subscription<T>
        sub = on<T>(priority) {
            if (cb()) {
                EventManager.unregister(sub)
                subscriptions.remove(sub)
            }
        }
        return sub
    }

    protected inline fun <reified T : Event> once(priority: Int = 0, noinline cb: T.() -> Unit) = until<T>(priority) {
        cb()
        true
    }

    enum class Tag(val desc: String = "") {
        NONE,
        LEGACY("A rewrite is currently planned. This module is no longer updated."),
        BETA("This feature is in beta. Issues may occur; no need to report bugs."),
        FORK("No longer maintained. Use jcnlk's fork for the latest updates and bug fixes: /quoi fork")
    }

    private companion object {
        private fun getCategory(clazz: Class<out Module>): Category? =
            Category.entries.find { clazz.`package`.name.contains(it.name, true) }
    }
}