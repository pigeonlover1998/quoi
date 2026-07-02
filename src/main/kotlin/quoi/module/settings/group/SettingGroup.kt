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
 * @param component The [UIComponent] that serves as the header and UI parent for this group.
 * @param areaParent The [AreaBoundListener] whose area/subarea constraints this group inherits.
 *                   Defaults to [module]. When nesting groups pass the parent [SettingGroup]
 * @param area Optional [Area] condition for this group's [EventListener]s to be active.
 * @param subarea Optinal [Location.subarea] string condition for this group's [EventListener]s.
 */
open class SettingGroup(
    val module: Module,
    val component: UIComponent<*>,
    val areaParent: AreaBoundListener = module,
    override val area: Area? = null,
    override val subarea: String? = null
) : SettingsDSL(), HudDSL, Shortcuts, AreaBoundListener {

    /**
     * Creates a nested [SettingGroup] with a custom [UIComponent] as the header.
     * Automatically inherits area and subarea constraints from the [parent] group.
     */
    constructor(
        parent: SettingGroup,
        component: UIComponent<*>,
        area: Area? = null,
        subarea: String? = null
    ) : this(parent.module, component, parent, area, subarea)

    /**
     * Creates a root [SettingGroup] with a [TextComponent] as the header
     */
    constructor(
        module: Module,
        name: String,
        desc: String = "",
        area: Area? = null,
        subarea: String? = null
    ) : this(module, TextComponent(name, desc), module, area, subarea)

    /**
     * Creates a nested [SettingGroup] with a [TextComponent] as the header.
     * Automatically inherits area and subarea constraints from the [parent] group.
     */
    constructor(
        parent: SettingGroup,
        name: String,
        desc: String = "",
        area: Area? = null,
        subarea: String? = null
    ) : this(parent.module, TextComponent(name, desc), parent, area, subarea)

    init {
//        require(module.subarea != null && subarea != null) {
//            "can't specify a subarea ($subarea) for a settinggroup when the module already has a subarea (${module.subarea})"
//        }

        component.apply {
            module.register(this)
            asParent()

            if (areaParent is SettingGroup) {
                json("${areaParent.component.jsonName}.$name")
                childOf(areaParent.component)
            }
        }
    }

    override val hudModule: Module
        get() = module

    override fun parent() = module

    override fun inArea(): Boolean = super.inArea() && areaParent.inArea()

    override fun inSubarea(): Boolean = super.inSubarea() && areaParent.inSubarea()

    override fun inEnvironment(): Boolean = super.inEnvironment() && areaParent.inEnvironment()

    override fun <K : Setting<T>, T> register(setting: K): K {
        if (setting.jsonName == setting.name) {
            setting.json("${component.jsonName}.${setting.name}")
        }

        module.register(setting)

        if (setting is UIComponent<*> && setting.parent == null) {
            setting.childOf(component)
        }

        return setting
    }

    fun childOf(parentC: UIComponent<*>?, condition: () -> Boolean = { (parentC?.value as? Boolean) ?: true }) = apply {
        component.childOf(parentC, condition)
    }

    fun childOf(parent: KProperty0<*>?) = apply {
        component.childOf(parent)
    }

    fun <P> childOf(parent: KProperty0<P>?, condition: (P) -> Boolean) = apply {
        component.childOf(parent, condition)
    }

    @JvmName("childOfBooleanGroup")
    fun childOf(parent: KProperty0<Boolean>) = apply {
        component.childOf(parent)
    }
}