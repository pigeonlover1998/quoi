package quoi.module.settings.group

import quoi.api.events.core.EventListener
import quoi.module.Module
import quoi.module.impl.render.ClickGui
import quoi.module.settings.Setting
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.SettingsDSL
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.TextComponent
import quoi.utils.Shortcuts
import kotlin.reflect.KProperty0

/**
 * A [SettingGroup] acts as a logical and visual container for related settings
 * rendering them as a collapsible dropdown within the [ClickGui].
 *
 * It's purely organisational and for categorising settings.
 *
 * @param module The [Module] that owns this group
 * @param parent The [UIComponent] that serves as the header and uI parent for this group.
 */
open class SettingGroup( // todo impl
    val module: Module,
    val parent: UIComponent<*>
) : SettingsDSL(), Shortcuts, EventListener {

    /**
     * Creates a [SettingGroup] with a [TextComponent] as the header
     */
    constructor(
        module: Module,
        name: String,
        desc: String = ""
    ) : this(module, TextComponent(name, desc))

    init {
        parent.apply {
            module.register(this)
            asParent()
        }
    }

    override fun parent() = module

    override fun <K : Setting<T>, T> register(setting: K): K {
        if (setting.jsonName == setting.name) {
            setting.json("${parent.jsonName}.${setting.name}")
        }

        module.register(setting)

        if (setting is UIComponent<*> && setting.parent == null) {
            setting.childOf(parent)
        }

        return setting
    }

    fun childOf(parentC: UIComponent<*>?, condition: () -> Boolean = { (parentC?.value as? Boolean) ?: true }) = apply {
        parent.childOf(parentC, condition)
    }

    fun childOf(parentP: KProperty0<*>?) = apply {
        parent.childOf(parentP)
    }

    fun <P> childOf(parentP: KProperty0<P>?, condition: (P) -> Boolean) = apply {
        parent.childOf(parentP, condition)
    }

    @JvmName("childOfBooleanGroup")
    fun childOf(parentP: KProperty0<Boolean>) = apply {
        parent.childOf(parentP)
    }
}