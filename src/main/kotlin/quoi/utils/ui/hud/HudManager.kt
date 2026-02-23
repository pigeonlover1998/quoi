package quoi.utils.ui.hud

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.Layout.Companion.section
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.popup
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.GuiEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus
import quoi.api.input.CatKeys
import quoi.config.Config
import quoi.module.settings.UISetting
import quoi.module.settings.impl.ColourSetting
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.toFixed
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.popupX
import quoi.utils.ui.popupY
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.screens.UIContainer
import quoi.utils.ui.screens.UIOverlay
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.collections.forEach
import kotlin.math.abs

object HudManager {
    val huds = arrayListOf<Hud>()
    var stupid = false

    private var overlay: UIOverlay? = null

    private var selected: Popup? = null
    private var lineX: Float = -1f
    private var lineY: Float = -1f
    private const val SNAP_THRESHOLD = 5f

    init {
        EventBus.on<GuiEvent.Open.Post> {
            if (screen !is AbstractContainerScreen<*>) return@on

            UIContainer(aloba {
                huds.forEach { hud ->
                    if (!hud.inContainer) return@forEach
                    val element = hud.Element()
                    element.add()
                    Hud.Scope(element, preview = false).apply { hud.builder(this) }
                }
            }, cancelling = false).apply { open() }
        }

        EventBus.on<WorldEvent.Load.Start> { // fixes visibleIf {} shit. todo find an actual fix
            reinit()
        }
    }

    fun init() {
        stupid = true
        if (mc.font == null) {
            scheduleTask { init() }
            return
        }
        if (!mc.isSameThread) {
            mc.execute { init() }
            return
        }

        overlay = UIOverlay(aloba {
            huds.forEach { hud ->
                if (hud.inContainer) return@forEach
                val element = hud.Element()
                element.add()
                Hud.Scope(element, preview = false).apply { hud.builder(this) }
            }
        }).apply { open() }
    }

    fun reinit(immediately: Boolean = true) {
        overlay?.close()
        if (immediately) mc.execute { init() }
        else scheduleTask { mc.execute { init() } }
    }

    var hudSettings: Popup? = null

    fun editor() = aboba("Quoi! hud editor") {

        ui.debug = false
        var hoverInfo: Popup? = null

        object : Element(copies()) {
            override fun draw() {
                NVGRenderer.line(lineX, 0f, lineX, ui.main.height, 1f, Colour.YELLOW.rgb)
                NVGRenderer.line(0f, lineY, ui.main.width, lineY, 1f, Colour.YELLOW.rgb)
            }
        }.add()

        onAdd {
            overlay?.close()
        }

        onRemove {
            scheduleTask { Config.save() }
            reinit(immediately = false)
        }

        onClick {
            selected?.closePopup()
            selected = null
            ui.unfocus()
        }

        dragSelection()

        huds.forEach { hud ->
            val element = hud.Element()
            element.add()

            Hud.Scope(element, preview = true).apply {
                hud.builder(this)

                var dragging = false
                var clickedX = 0f
                var clickedY = 0f

                onClick(button = 0) {
                    dragging = true

                    if (element.constraints.x !is Pixel) {
                        element.constraints.x = element.x.px
                        element.constraints.y = element.y.px
                    }

                    clickedX = ui.mx - element.x
                    clickedY = ui.my - element.y

                    element.moveToTop()

                    ui.focus(element)
                    true
                }

                onRelease {
                    dragging = false
                }

                onMouseMove {
                    if (!dragging) return@onMouseMove false
                    val newX = ui.mx - clickedX
                    val newY = ui.my - clickedY

                    element.constraints.x.pixels = newX.coerceIn(0f, ui.main.width - element.screenWidth())
                    element.constraints.y.pixels = newY.coerceIn(0f, ui.main.height - element.screenHeight())

                    element.redraw()
                    true
                }

//                draggable(moves = element)

                onRemove {
                    hud.savePosition(element, ui.main.width, ui.main.height)
                }

                onFocus {
                    hoverInfo = popup(at(popupX(), popupY()), smooth = false) {
                        block(
                            bounds(padding = 5.px),
                            colour = theme.background,
                            5.radius()
                        ) {
                            outline(theme.accent, thickness = 2.px)
                            column {
                                textSupplied(
                                    supplier = {
                                        var str = "x: ${element.x.toInt()}, y: ${element.y.toInt()}"
                                        if (element.scaleX != 1.0f) str += ", scale: ${element.scaleX.toFixed(1)}"
                                        str
                                    },
                                    colour = theme.textSecondary,
                                    size = theme.textSize
                                )
                            }
                        }
                    }
                }

                onFocusLost {
                    hoverInfo?.closePopup()
                    hoverInfo = null
                }

                onKeyPressed { (key, mods) ->
                    val step = if (mods.isShiftDown) 10 else 1
                    val (x, y) = when (key) {
                        CatKeys.KEY_RIGHT -> step to 0
                        CatKeys.KEY_LEFT -> -step to 0
                        CatKeys.KEY_UP -> 0 to -step
                        CatKeys.KEY_DOWN -> 0 to step
                        else -> return@onKeyPressed false
                    }
                    if (element.constraints.x !is Pixel) {
                        element.constraints.x = element.x.px
                        element.constraints.y = element.y.px
                    }

                    val newX = (element.constraints.x.pixels + x).coerceIn(0f, ui.main.width - element.width)
                    val newY = (element.constraints.y.pixels + y).coerceIn(0f, ui.main.height - element.height)

                    element.constraints.x.pixels = newX
                    element.constraints.y.pixels = newY
                    element.redraw()
                    true
                }

                onClick(button = 1) {
                    ui.focus(element)
                    hoverInfo?.closePopup()
                    hudSettings?.closePopup()
                    element.moveToTop()
                    hudSettings = settings(at(popupX(), popupY()), hud, { hudSettings = null }, element) {
                        hud.savePosition(element, ui.main.width, ui.main.height)
                        rebuildHuds()
                    }
                    true
                }

                onScroll { (amount) ->
                    val newValue = hud.scale.value + (hud.scale.incrementD * amount)
                    hud.scale.set(newValue)
                    element.scaleTransformation = hud.scale.value
                }
            }
        }
    }

    private fun ElementScope<*>.selectHuds(selectedHuds: List<Hud.Element>): Popup {
        redraw()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = 0f
        var maxY = 0f

        selectedHuds.forEach {
            if (it.constraints.x !is Pixel) {
                it.constraints.x = it.x.px
                it.constraints.y = it.y.px
            }
            val right = it.x + it.screenWidth()
            val bottom = it.y + it.screenHeight()
            minX = minOf(minX, it.x)
            minY = minOf(minY, it.y)
            maxX = maxOf(maxX, right)
            maxY = maxOf(maxY, bottom)
        }

        val px = minX.px
        val py = minY.px
        val width = (maxX - minX).px
        val height = (maxY - minY).px


        return popup(constraints = constrain(px, py, width, height), smooth = true) {
            outlineBlock(
                constraints = copies(),
                colour = Colour.WHITE,
                thickness = 1.px,
                4.radius()
            )

            var mouseDown = false
            var offsetX = ui.mx - px.pixels
            var offsetY = ui.my - py.pixels

            onClick {
                mouseDown = true
                offsetX = ui.mx - px.pixels
                offsetY = ui.my - py.pixels
                true
            }

            onRelease {
                mouseDown = false
                lineX = -1f
                lineY = -1f
            }

            onMouseMove {
                if (!mouseDown) return@onMouseMove false

                val parent = element.parent ?: return@onMouseMove false
                val centerX = parent.width / 2
                val centerY = parent.height / 2

                var newX = (ui.mx - offsetX).coerceIn(0f, parent.width - element.screenWidth())
                var newY = (ui.my - offsetY).coerceIn(0f, parent.height - element.screenHeight())

                lineX = -1f
                lineY = -1f

                if (abs(newX + element.screenWidth() / 2 - centerX) <= SNAP_THRESHOLD) {
                    newX = centerX - element.screenWidth() / 2
                    lineX = centerX
                }
                if (abs(newY + element.screenHeight() / 2 - centerY) <= SNAP_THRESHOLD) {
                    newY = centerY - element.screenHeight() / 2
                    lineY = centerY
                }

                parent.children?.forEach { other ->
                    if (other !is Hud.Element || other in selectedHuds || !other.enabled) return@forEach

                    val otherRight = other.x + other.screenWidth()
                    val thisRight = newX + element.screenWidth()
                    val otherBottom = other.y + other.screenHeight()
                    val thisBot = newY + element.screenHeight()

                    when {
                        abs(thisBot - otherBottom) <= SNAP_THRESHOLD -> { // bot - bot
                            newY = otherBottom - element.screenHeight()
                            lineY = otherBottom
                        }
                        abs(newY - other.y) <= SNAP_THRESHOLD -> { // top - top
                            newY = other.y
                            lineY = newY
                        }
                        abs(thisBot - other.y) <= SNAP_THRESHOLD -> { // bot - top
                            newY = other.y - element.screenHeight()
                            lineY = other.y
                        }
                        abs(newY - otherBottom) <= SNAP_THRESHOLD -> { // top - bot
                            newY = otherBottom
                            lineY = newY
                        }
                    }

                    when {
                        abs(thisRight - otherRight) <= SNAP_THRESHOLD -> { // right - right
                            newX = otherRight - element.screenWidth()
                            lineX = otherRight
                        }
                        abs(newX - other.x) <= SNAP_THRESHOLD -> { // left - left
                            newX = other.x
                            lineX = newX
                        }
                        abs(thisRight - other.x) <= SNAP_THRESHOLD -> { // right left
                            newX = other.x - element.screenWidth()
                            lineX = other.x
                        }
                        abs(newX - otherRight) <= SNAP_THRESHOLD -> { // left - right
                            newX = otherRight
                            lineX = newX
                        }
                    }
                }

                val dX = newX - px.pixels
                val dY = newY - py.pixels

                if (dX != 0f || dY != 0f) {
                    px.pixels = newX
                    py.pixels = newY
                    selectedHuds.forEach {
                        it.constraints.x.pixels += dX
                        it.constraints.y.pixels += dY
                    }
                    redraw()
                }
                true
            }
        }
    }

    private fun ElementScope<*>.dragSelection() {
        val selection = block(
            constraints = constrain(0.px, 0.px, 0.px, 0.px),
            colour = Colour.RGB(61, 174, 233, 0.25f),
            4.radius()
        ).outline(Colour.RGB(61, 174, 233), thickness = 1.px).toggle()

        var clickedX = 0f
        var clickedY = 0f

        onClick {
            selection.toggle()
            selection.element.moveToTop()
            clickedX = ui.mx
            clickedY = ui.my

            selection.element.constraints.apply {
                x.pixels = clickedX
                y.pixels = clickedY
                width.pixels = 0f
                height.pixels = 0f
            }
            selection.redraw()
            true
        }

        onRelease {
            if (selection.enabled) {
                val selectedHuds = element.children
                    ?.filterIsInstance<Hud.Element>()
                    ?.filter { it.enabled && it.intersects(selection.element) }
                    ?.toList()
                    .orEmpty()

                selected?.closePopup()
                selected = selectHuds(selectedHuds)

                selection.toggle()
                selection.redraw()
            } else selected?.takeIf { !it.element.isInside(ui.mx, ui.my) }?.closePopup()?.also { selected = null }
        }

        onMouseMove {
            if (!selection.enabled) return@onMouseMove false

            val newW = ui.mx - clickedX
            val newH = ui.my - clickedY

            selection.element.constraints.apply {
                x.pixels = if (newW < 0) clickedX + newW else clickedX
                y.pixels = if (newH < 0) clickedY + newH else clickedY
                width.pixels = abs(newW)
                height.pixels = abs(newH)
                redraw()
            }
            true
        }
    }

    fun ElementScope<*>.settings(pos: Positions, hud: Hud, onClose: () -> Unit, hudElement: Element? = null, onValue: () -> Unit = {}) = popup(copies(), smooth = false) {
        if (hud.settings.size == 4) return@popup


        onClick {
            closePopup()
            onClose()
        }

        group(
            constrain(
                x = pos.x, y = pos.y,
                w = 260.px, h = GroupHeight
            )
        ) {

            column {
                onClick {
                    true
                }

                dropShadow(
                    colour = Colour.BLACK.withAlpha(0.25f),
                    blur = 10f,
                    spread = 5f,
                    radius = 10.radius()
                )

                block(
                    size(260.px, 35.px),
                    colour = theme.background,
                    radius(tl = 10, tr = 10)
                ) {
                    text(
                        string = hud.name,
                        size = 70.percent,
                        colour = theme.textPrimary
                    )
                }

                column(size(w = Copying), gap = 5.px) {
                    block(
                        copies(),
                        colour = theme.background.withAlpha(0.7f)
                    )
                    column(constrain(x = 5.px, w = Copying - 10.px, h = ColumnHeight), gap = 5.px) { // fixme
                        divider(5.px)
                        hud.settings.forEach { setting ->
                            if (setting !is UISetting) return@forEach

                            var wasRainbow = (setting as? ColourSetting)?.rainbow ?: false
                            setting.render(this).onEvent(UISetting.ValueUpdated) {
                                val cs = setting as? ColourSetting
                                if (cs?.rainbow != wasRainbow) onValue()
                                wasRainbow = cs?.rainbow ?: false
                                true
                            }
                        }
                    }
                    section(size = 40.px) {
                        val thickness = Animatable(from = 2.px, to = 3.px)
                        block(
                            size(w = 90.percent, h = 70.percent),
                            colour = theme.panel,
                            5.radius()
                        ) {
                            outline(theme.accent, thickness = thickness)

                            text(
                                string = "Reset",
                                size = 70.percent,
                                colour = theme.textPrimary
                            )

                            onClick(button = 0) {
                                hud.settings.drop(if (hudElement == null) 0 else 3).forEach { // schizo, idc
                                    it.reset()
                                }
                                thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)?.onFinish {
                                    scheduleTask { thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint) }
                                }

//                                hudElement?.let { element ->
//                                    element.constraints.x.pixels = 0.0f
//                                    element.constraints.y.pixels = 0.0f
//                                }
                                true
                            }
                        }
                    }
                }


                block(
                    size(260.px, 10.px),
                    colour = theme.background,
                    radius(bl = 10, br = 10)
                )
            }
        }
    }
}

// temporary fix for "wobble" on visibility setting change.... I am an idiot sandwich and idk how to fix Bounding...
private val ColumnHeight = object : Constraint.Size {
    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        if (horizontal) return Bounding.calculateSize(element, true)

        val layout = element as? Layout ?: return Bounding.calculateSize(element, false)

        val gap = layout.gap?.calculateSize(layout, false) ?: 0f
        var totalHeight = 0f

        layout.children?.forEach { child ->
            if (child.enabled) {
                val childH = child.constraints.height.calculateSize(child, false)
                val gap = if (child is Layout.Divider) 0f else gap
                val actualGap = if (childH < gap) childH else gap

                totalHeight += childH + actualGap
            }
        }
        return totalHeight
    }

    override fun reliesOnChildren() = true
}

val GroupHeight = object : Constraint.Size {
    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        if (horizontal) return Bounding.calculateSize(element, true)

        var value = 0f
        element.children?.forEach {
            if (it.enabled) {
                val new = it.constraints.height.calculateSize(it, false)
                if (new > value) value = new
            }
        }
        return value
    }

    override fun reliesOnChildren() = true
}