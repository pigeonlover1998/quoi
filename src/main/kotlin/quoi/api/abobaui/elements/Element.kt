package quoi.api.abobaui.elements

import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.elements.impl.Scrollable
import quoi.api.abobaui.elements.impl.Text
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.elements.impl.layout.Grid
import quoi.api.abobaui.elements.impl.layout.Row
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.events.Lifetime
import quoi.api.abobaui.events.Mouse
import quoi.api.abobaui.transforms.Transform
import quoi.api.colour.Colour
import quoi.utils.render.DrawContextUtils.pushScissor
import quoi.utils.ui.rendering.NVGRenderer
import kotlin.experimental.ExperimentalTypeInference

abstract class Element(
    val constraints: Constraints,
    var colour: Colour? = null
) {
    lateinit var ui: AbobaUI

    inline val ctx get() = ui.ctx

    var parent: Element? = null

    var children: ArrayList<Element>? = null

    private var events: HashMap<Any, ArrayList<(AbobaEvent) -> Boolean>>? = null

    var acceptsInput = false

    var hovered: Boolean = false
        set(value) {
            if (field == value) return
            if (value) accept(Mouse.Entered) else accept(Mouse.Exited)
            field = value
        }

    var x = 0f
    var y = 0f
    var width = 0f
    var height = 0f

    var internalX = 0f
    var internalY = 0f

    private var transforms: ArrayList<Transform>? = null
    private var shadows: ArrayList<Element>? = null

    var scaleX = 1f
    var scaleY = 1f

    var redraw = false

    var renders: Boolean = true
        get() = field && enabled
        private set

    var pressed: Boolean = false

    open var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            redraw()
        }

    var scissors: Boolean = false

    fun init() {
        accept(Lifetime.Initialised)
        children?.forEach { it.init() }
        shadows?.forEach { it.init() }
    }

    abstract fun draw()

    fun render() {
        if (redraw) {
            redraw = false
            size()
            positionChildren()
            clip()
        }
        if (renders) {
            ctx.pose().pushMatrix()
            NVGRenderer.push()
            if (scissors) {
                ctx.pushScissor(x.toInt(), y.toInt(), width.toInt(), height.toInt())
                NVGRenderer.pushScissor(x, y, width, height)
            }

            shadows?.forEach { it.render() }

            transforms?.forEach {
                it.apply(element = this)
            }
            draw()
            if (ui.debug) {
                val col = when(this) {
                    is Column -> Colour.CYAN
                    is Grid -> Colour.ORANGE
                    is Row -> Colour.GREEN
                    is Block -> Colour.MAGENTA
                    is Group -> Colour.YELLOW
                    is Scrollable -> Colour.PINK
                    is Text -> Colour.BLUE
                    is Layout.Divider -> Colour.RED
                    else -> Colour.BLACK
                }
                NVGRenderer.hollowRect(x, y, width, height, 1.0f, col.rgb)
            }
            children?.forEach { it.render() }
            if (scissors) {
                ctx.disableScissor()
                NVGRenderer.popScissor()
            }
            ctx.pose().popMatrix()
            NVGRenderer.pop()
        }
    }

    fun redraw() {
        var p = parent
        while (p != null) {
            if (!p.constraints.sizeReliesOnChildren()) break
            p = p.parent
        }
        (p ?: this).redraw = true
    }

    fun size() {
        if (!enabled) return
        if (!constraints.width.reliesOnChildren()) width = constraints.width.calculateSize(this, true)
        if (!constraints.height.reliesOnChildren()) height = constraints.height.calculateSize(this, false)
        children?.forEach {
            it.size()
        }
        shadows?.forEach { it.size() }
    }

    fun clipContents() {
        if (!enabled) return
        scissors = true
        children?.forEach {
            it.clipContents()
        }
    }

    open fun prePosition() {}

    open fun postPosition() {}

    fun positionChildren(proceed: Boolean = true) {
        if (!enabled) return
        prePosition()
        children?.forEach {
            position(it, x, y)
            it.positionChildren(proceed)
        }
        shadows?.forEach {
            position(it, x, y)
            it.positionChildren(proceed)
        }
        postSize(proceed)
        postPosition()

        if (hovered && !isInside(ui.mx, ui.my)) {
            ui.recalculateMouse = true
        }
    }

    open fun position(element: Element, newX: Float, newY: Float) {
        element.internalX = element.constraints.x.calculatePos(element, true)
        element.internalY = element.constraints.y.calculatePos(element, false)
        element.x = element.internalX + newX
        element.y = element.internalY + newY
    }

    private fun postSize(proceed: Boolean = true) {
        val widthRelies = constraints.width.reliesOnChildren()
        val heightRelies = constraints.height.reliesOnChildren()
        if (widthRelies) width = constraints.width.calculateSize(this, true)
        if (heightRelies) height = constraints.height.calculateSize(this, false)

        if ((widthRelies || heightRelies) && proceed) {
            size()
            parent?.positionChildren(false)
        }
    }

    fun clip() {
        val check = { it: Element ->
            it.renders = it.intersects(x, y, width, height) && !(it.width == 0f && it.height == 0f)
            if (it.renders) it.clip()
        }
        children?.forEach(check)
        shadows?.forEach(check)
    }

    fun addElement(element: Element) {
        if (children == null) children = arrayListOf()
        children!!.add(element)
        element.parent = this
        element.constraints.apply {
            val (newX, newY) = getDefaultPositions()
            if (x.undefined()) x = newX
            if (y.undefined()) y = newY
        }
        element.ui = ui
        redraw = true
    }

    fun removeElement(element: Element?) {
        if (element == null || children.isNullOrEmpty()) return
        children!!.remove(element)
        element.parent = null
        ui.eventManager.postToAll(Lifetime.Uninitialised, element)
    }

    fun removeAll() {
        if (children.isNullOrEmpty()) return
        children?.removeIf { element ->
            element.parent = null
            ui.eventManager.postToAll(Lifetime.Uninitialised, element)
            true
        }
    }

    open fun getDefaultPositions(): Pair<Constraint.Position, Constraint.Position> = Pair(Centre, Centre)

    fun accept(event: AbobaEvent): Boolean {
        if (events != null) {
            val key: Any = if (event is AbobaEvent.NonSpecific) event::class.java else event
            val listeners = events!![key] ?: return false
//            when (event) {
//                is Lifetime -> events!!.remove(key)
//            }
            listeners.forEach { if (it(event)) return true }
        }
        return false
    }

    fun acceptFocused(event: AbobaEvent): Boolean {
        if (events != null) {
            val action = events!![event::class.java] ?: return false
            action.forEach { if (it(event)) return true }
        }
        return false
    }

    @OptIn(ExperimentalTypeInference::class)
    @Suppress("UNCHECKED_CAST")
    @OverloadResolutionByLambdaReturnType
    fun <E : AbobaEvent> registerEvent(event: E, block: (E) -> Boolean) {
        if (event !is Lifetime) acceptsInput = true
        if (events == null) events = hashMapOf()
        val key: Any = if (event is AbobaEvent.NonSpecific) event::class.java else event
        events!!.getOrPut(key) { arrayListOf() }.add(block as (AbobaEvent) -> Boolean)
    }

    fun addTransform(transform: Transform) {
        if (transforms == null) transforms = arrayListOf()
        transforms!!.add(transform)
    }

    fun addShadow(element: Element) {
        if (shadows == null) shadows = arrayListOf()
        shadows!!.add(element)
        element.parent = this
        element.ui = ui
        redraw = true
    }

    fun getSize(horizontal: Boolean) = (if (horizontal) width else height)
    fun getPosition(horizontal: Boolean) = if (horizontal) internalX else internalY

    fun isInside(x: Float, y: Float): Boolean {
        val tx = this.x
        val ty = this.y
        return x in tx..tx + (screenWidth()) && y in ty..ty + (screenHeight())
    }

    fun intersects(other: Element): Boolean {
        return intersects(other.x, other.y, other.screenWidth(), other.screenHeight())
    }

    private fun intersects(x: Float, y: Float, width: Float, height: Float): Boolean {
        val tx = this.x
        val ty = this.y
        val tw = screenWidth()
        val th = screenHeight()
        return (x < tx + tw && tx < x + width) && (y < ty + th && ty < y + height)
    }

    fun screenWidth() = width * scaleX

    fun screenHeight() = height * scaleY
}