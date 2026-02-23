package quoi.api.abobaui

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.events.EventManager
import quoi.api.abobaui.events.Lifetime
import quoi.api.abobaui.operations.Operation
import quoi.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics

// heavily inspired by stivais' auroraui. I'm too stupid for this
class AbobaUI(val title: String) {

    val main = Group(Constraints(0.px, 0.px, 0.px, 0.px)).also { it.ui = this }

    lateinit var ctx: GuiGraphics

    val eventManager = EventManager(this)

    var recalculateMouse = false

    var initialised = false

    var debug = false

    inline val mx: Float
        get() = eventManager.mouseX

    inline val my: Float
        get() = eventManager.mouseY

    inline var clipboard: String?
        get() = mc.keyboardHandler.clipboard
        set(value) {
            mc.keyboardHandler.clipboard = value
        }

    private var operations = arrayListOf<Operation>()

    fun init(width: Int, height: Int) {
        main.constraints.width = width.px
        main.constraints.height = height.px

        main.size()
        main.positionChildren()
        main.clip()

        if (!initialised) {
            main.init()
            initialised = true
        }

    }

    fun render() {
        if (recalculateMouse) {
            eventManager.recalculate()
            recalculateMouse = false
        }
//        operations.removeIf { it.run() }
        operations.toList().forEach { if (it.run()) operations.remove(it) }
        NVGRenderer.push()
        main.render()
        NVGRenderer.pop()
    }

    fun close() {
        eventManager.postToAll(Lifetime.Uninitialised, main)
        initialised = false
    }

    fun resize(width: Int, height: Int) {
        main.constraints.width = width.px
        main.constraints.height = height.px
        main.redraw()
    }

    fun focus(element: Element) {
        eventManager.focused = element
    }

    fun unfocus() {
        eventManager.focused = null
    }

    fun addOperation(operation: Operation) {
        operations.add(operation)
    }

    class Instance(
        val title: String,
        private val block: ElementScope<Group>.() -> Unit
    ) {
        private var _ui: AbobaUI? = null

        internal constructor(ui: AbobaUI) : this(ui.title, {}) {
            _ui = ui
        }

        var ctx: GuiGraphics
            get() = ui.ctx
            set(value) { ui.ctx = value }

        val ui: AbobaUI
            get() {
                if (_ui == null) {
                    _ui = AbobaUI(title)
                    ElementScope(_ui!!.main).apply(block)
                }
                return _ui!!
            }

        val eventManager: EventManager
            get() = ui.eventManager

        fun init(width: Int, height: Int) = ui.init(width, height)

        fun render() = ui.render()

        fun close() = ui.close()

        fun resize(width: Int, height: Int) = ui.resize(width, height)
    }
}