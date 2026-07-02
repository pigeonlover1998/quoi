package quoi.module.settings.group

import quoi.api.events.core.AreaBoundListener
import quoi.api.events.core.EventListener
import quoi.api.skyblock.location.Area
import quoi.api.skyblock.location.Location
import quoi.module.Module
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.Setting
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.SettingsDSL
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.TextComponent
import quoi.utils.Shortcuts
import quoi.utils.ui.hud.HudDSL
import kotlin.reflect.KProperty0

/**
 * A [SettingGroup] acts as a logical and visual container for related settings
 * rendering them as a collapsible dropdown within the [ClickGui].
 *
 * It's purely organisational and for categorising settings.
 *
 * @param module The [Module] that owns this group
 * @param parent The [UIComponent] that serves as the header and uI parent for this group.
 * @param area Optional [Area] condition for this group's [EventListener]s to be active.
 * @param subarea Optinal [Location.subarea] string condition for this group's [EventListener]s.
 */
open class SettingGroup(
    val module: Module,
    val parent: UIComponent<*>,
    override val area: Area? = null,
    override val subarea: String? = null
) : SettingsDSL(), HudDSL, Shortcuts, AreaBoundListener {

    /**
     * Creates a [SettingGroup] with a [TextComponent] as the header
     */
    constructor(
        module: Module,
        name: String,
        desc: String = "",
        area: Area? = null,
        subarea: String? = null
    ) : this(module, TextComponent(name, desc), area, subarea)

    init {
        require(module.subarea != null && subarea != null) {
            "can't specify a subarea ($subarea) for a settinggroup when the module already has a subarea (${module.subarea})"
        }

        parent.apply {
            module.register(this)
            asParent()
        }
    }

    override val hudModule: Module
        get() = module

    override fun parent() = module

    override fun inArea(): Boolean = super.inArea() && module.inArea()

    override fun inSubarea(): Boolean = super.inSubarea() && module.inSubarea()

    override fun inEnvironment(): Boolean = super.inEnvironment() && module.inEnvironment()

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