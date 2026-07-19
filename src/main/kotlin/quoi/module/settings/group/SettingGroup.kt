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
 * @param parent The [AreaBoundListener] that owns this group. Must be [Module] or [SettingGroup]
 * @param component The [UIComponent] that serves as the header and UI parent for this group.
 * @param area Optional [Area] condition for this group's [EventListener]s to be active.
 * @param subarea Optinal [Location.subarea] string condition for this group's [EventListener]s.
 */
open class SettingGroup(
    val parent: AreaBoundListener,
    val component: UIComponent<*>,
    override val area: Area? = null,
    override val subarea: String? = null
) : SettingsDSL(), HudDSL, Shortcuts, AreaBoundListener {

    /**
     * Creates a [SettingGroup] with a [TextComponent] as the header
     */
    constructor(
        parent: AreaBoundListener,
        name: String,
        desc: String = "",
        area: Area? = null,
        subarea: String? = null
    ) : this(parent, TextComponent(name, desc), area, subarea)

    val module: Module = (parent as? Module)
        ?: (parent as? SettingGroup)?.module
        ?: error("parent must be a module or a settinggroup. got ${parent::class.simpleName}")

    private val settings: ArrayList<Setting<*>> = ArrayList()


    init {
        component.apply {
            module.register(this)
            asParent()

            val parent = this@SettingGroup.parent

            if (parent is SettingGroup) {
                json("${parent.component.jsonName}.$name")
                childOf(parent.component)
            }
        }
    }

    override val hudModule: Module
        get() = module

    override fun parent() = module

    override fun inArea(): Boolean = super.inArea() && parent.inArea()

    override fun inSubarea(): Boolean = super.inSubarea() && parent.inSubarea()

    override fun inEnvironment(): Boolean = super.inEnvironment() && parent.inEnvironment()

    override fun <K : Setting<T>, T> register(setting: K): K {
        if (setting.jsonName == setting.name) {
            setting.json("${component.jsonName}.${setting.name}")
        }

        settings.add(setting)
        module.register(setting)

        if (setting is UIComponent<*> && setting.parent == null) {
            setting.childOf(component)
        }

        return setting
    }

    companion object {
        fun <T : SettingGroup> T.hide() = apply {
            component.hide()
        }

        fun <T : SettingGroup> T.json(name: String) = apply {
            val old = component.jsonName
            if (old == name) return@apply

            component.json(name)

            settings.forEach { setting ->
                if (setting.jsonName.startsWith("$old.")) {
                    val s = setting.jsonName.substring(old.length + 1)
                    setting.json("$name.$s")
                }
            }
        }

        fun <T : SettingGroup> T.childOf(parent: UIComponent<*>?, condition: () -> Boolean = { (parent?.value as? Boolean) ?: true }) = apply {
            component.childOf(parent, condition)
        }

        fun <T : SettingGroup> T.childOf(parent: KProperty0<*>?) = apply {
            component.childOf(parent)
        }

        fun <T : SettingGroup, P> T.childOf(parent: KProperty0<P>?, condition: (P) -> Boolean) = apply {
            component.childOf(parent, condition)
        }

        @JvmName("childOfBooleanGroup")
        fun <T : SettingGroup> T.childOf(parent: KProperty0<Boolean>) = apply {
            component.childOf(parent)
        }
    }
}