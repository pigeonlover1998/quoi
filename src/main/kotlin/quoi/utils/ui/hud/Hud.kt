package quoi.utils.ui.hud

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.elements.Element
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

open class Hud(
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
        val list = settings.toList()
        list.forEach { addSetting(it, list) }
        return this
    }

    fun withSettings(vararg settings: KProperty0<*>): Hud {
        val list = settings.map { settingFromK0(it) }
        list.forEach { addSetting(it, list) }
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

    private fun addSetting(setting: UIComponent<*>, adding: List<UIComponent<*>> = emptyList()) {
        if (setting in module.settings) {
            if (setting.parent != null && setting.parent as UIComponent !in this.settings && setting.parent !in adding) {
                setting.parent?.children?.remove(setting)
                setting.parent = null
            }

            if (setting.parent == null && !setting.forceParent) {
                dummy.childOf(setting)
            }

            module.settings.remove(setting)
        }

        settings.add(setting)
    }

    inner class Element : Group(constrain(x.value.percent, y.value.percent, IsolatedBounding, IsolatedBounding)) {

        override var enabled: Boolean = true
            get() = field && (this@Hud.module.enabled || this@Hud.module.alwaysActive) && (this@Hud.enabled || !this@Hud.toggleable)

        override var usingCtx: Boolean = true

        var scaleTransformation by Scale(this@Hud.scale.value, centered = false).also {
            addTransform(it)
        }

        override fun getDefaultPositions() = Pair(Undefined, Undefined)

        override fun prePosition() {
            parent?.let { p ->
                this.internalX = this.constraints.x.calculatePos(this, true)
                this.internalY = this.constraints.y.calculatePos(this, false)
                this.x = this.internalX + p.x
                this.y = this.internalY + p.y
            }
            super.prePosition()
        }

        override fun postPosition() {
            width = Bounding.calculateSize(this, true)
            height = Bounding.calculateSize(this, false)
            parent?.let { p ->
                this.renders = this.intersects(p) && !(this.width == 0f && this.height == 0f)
            }
            super.postPosition()
        }

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

private val IsolatedBounding = object : Constraint.Size {
    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        return Bounding.calculateSize(element, horizontal)
    }

    override fun reliesOnChildren(): Boolean = false
}