package quoi.utils.ui.hud

import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.transforms.impl.Scale
import quoi.module.Module
import quoi.module.settings.Setting
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.TextComponent
import quoi.module.settings.impl.SliderComponent
import quoi.utils.ui.settingFromK0
import kotlin.reflect.KProperty0

open class Hud( // todo fix children shit
    val name: String,
    val module: Module,
    val toggleable: Boolean,
    open val builder: Scope.() -> Unit = {}
) {
    var enabled: Boolean = false

    val x = SliderComponent("x", 0.0f, 0.0f, 100.0f).hide()
    val y = SliderComponent("y", 0.0f, 0.0f, 100.0f).hide()
    var scale = SliderComponent("scale", 1.0f, 0.3f, 5f, 0.1f).hide()

    private val dummy = TextComponent("dummy").hide()

    val settings = arrayListOf<Setting<*>>(dummy, x, y, scale)

    var inContainer = false
        private set

    init {
        check(!HudManager.stupid) { "ABOBA | too late $name" }
        HudManager.huds.add(this)
    }

    fun container(): Hud {
        inContainer = true
        return this
    }

    fun withSettings(vararg settings: UIComponent<*>): Hud {
        settings.forEach { addSetting(it) }
        return this
    }

    fun withSettings(vararg settings: KProperty0<*>): Hud {
        settings.forEach { property ->
            val setting = settingFromK0(property)

            addSetting(setting)
        }

        return this
    }

    fun getSettingByName(name: String?): Setting<*>? {
        for (setting in settings) {
            if (setting.jsonName.equals(name, true) /*|| setting.name.equals(name, true)*/) {
                return setting
            }
        }
        return null
    }

    open fun savePosition(element: Element, screenWidth: Float, screenHeight: Float) {
        x.value = (element.x / screenWidth) * 100f
        y.value = (element.y / screenHeight) * 100f
    }

    private fun addSetting(setting: UIComponent<*>) {
        if (setting in module.settings) {
            setting.parent?.children?.remove(setting)

            dummy.childOf(setting)
            /*if (toggleable) */module.settings.remove(setting)
        }

        settings.add(setting)
    }

    inner class Element : Group(constrain(x.value.percent, y.value.percent, Bounding, Bounding)) {

        override var enabled: Boolean = true
            get() = field && (this@Hud.module.enabled || this@Hud.module.alwaysActive) && (this@Hud.enabled || !this@Hud.toggleable)

        override var usingCtx: Boolean = true

        var scaleTransformation by Scale(this@Hud.scale.value, centered = false).also {
            addTransform(it)
        }

        override fun getDefaultPositions() = Pair(Undefined, Undefined)

        fun rebuild(scope: Scope) {
            removeAll()
            builder.invoke(scope)
            scaleTransformation = this@Hud.scale.value
            redraw = true
        }
    }

    open class Scope(element: Element, val preview: Boolean) : ElementScope<Element>(element) {

        inline fun ElementScope<*>.visibleIf(crossinline block: () -> Boolean) {
            if (!preview) {
                operation {
                    element.enabled = block()
                    false
                }
            }
        }

        fun rebuildHuds() {
            element.rebuild(this)
        }
    }
}