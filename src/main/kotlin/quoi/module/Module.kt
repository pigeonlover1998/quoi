package quoi.module

import quoi.api.commands.QuoiCommand
import quoi.api.input.CatKeys
import quoi.module.settings.Setting
import quoi.module.settings.impl.Keybinding
import quoi.utils.ChatUtils.modMessage
import quoi.annotations.AlwaysActive
import quoi.api.events.core.AreaBoundListener
import quoi.api.skyblock.location.Area
import quoi.module.settings.SettingsDSL
import quoi.utils.Shortcuts
import quoi.utils.ui.hud.HudDSL

abstract class Module(
    val name: String,
    override val area: Area? = null,
    override val subarea: String? = null,
    val key: Int = CatKeys.KEY_NONE,
    @Transient val desc: String = "",
    toggled: Boolean = false,
    val tag: Tag = Tag.NONE
) : SettingsDSL(), HudDSL, Shortcuts, AreaBoundListener {

    override val running: Boolean
        get() = enabled || alwaysActive

    inline val active: Boolean
        get() = running && inEnvironment()

    @Transient
    val category: Category = getCategory(this::class.java) ?: Category.RENDER

    val keybinding: Keybinding = Keybinding(key).apply { onPress = ::onKeybind }  // todo on press/release/hold

    var enabled: Boolean = toggled
        private set

    protected inline val command get() = QuoiCommand.command

    @Transient
    val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    init {
        if (alwaysActive) onEnable()
    }

    val settings: ArrayList<Setting<*>> = ArrayList()

    open fun onEnable() {}

    open fun onDisable() {}

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

    override fun <K : Setting<T>, T> register(setting: K): K {
        addSettings(setting)
        return setting
    }

    fun getSettingByName(name: String?): Setting<*>? {
        for (setting in settings) {
            if (setting.jsonName.equals(name, true) /*|| setting.name.equals(name, true)*/) {
                return setting
            }
        }
        return null
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