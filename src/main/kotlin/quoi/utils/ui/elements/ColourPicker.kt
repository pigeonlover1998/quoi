package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.abobaui.elements.impl.layout.Column.Companion.sectionRow
import quoi.api.abobaui.elements.impl.popup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.animations.Animation
import quoi.api.colour.*
import quoi.api.input.CursorShape
import quoi.module.impl.render.ClickGui.rainbowSpeed
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.*
import quoi.utils.ui.data.Gradient
import quoi.utils.ui.data.Radii
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGRenderer.image
import java.awt.Color.HSBtoRGB
import kotlin.math.round
import kotlin.reflect.KMutableProperty0

fun ElementScope<*>.colourPicker(
    ref: KMutableProperty0<Colour.HSB>,
    allowAlpha: Boolean,
    pos: Positions = at(),
    isRainbow: KMutableProperty0<Boolean>? = null,
) = popup(copies(), smooth = false) {

    val value by ref

    val width = 295.0f
    val height = width * (1f / 1.1125f)
    val thickness = 0.px

    val colourOnlyHue = colour { HSBtoRGB(value.hue, 1f, 1f) }

    fun ElementScope<*>.pointer(
        onlyX: Boolean,
        w: Float = width,
        block: () -> Pair<Float, Float>,
    ) {
        val (sx, sy) = block()

        val size = 16f
        val offset = size / 2f

        val pointerX = Animatable.Raw((sx * w).coerceIn(offset, w - offset))
        val pointerY = if (onlyX) null else  Animatable.Raw((sy * height).coerceIn(offset, height))

        var animate = false

        val colour = colour { value.rgb }
        val constraints = constrain(
            x = pointerX.alignCentre,
            y = pointerY?.alignCentre ?: Centre,
            w = size.px, h = size.px
        )

        object : Element(constraints, colour) {
            override fun draw() {
                val r = 11f
                val centerX = round(x + r - 3f)
                val centerY = round(y + r - 3f)
                NVGRenderer.dropShadow(x - 1, y - 1, size + 2, size + 2, blur = 4f, spread = 3f, radius = 10f)
                NVGRenderer.circle(centerX, centerY, r, Colour.WHITE.rgb)
                if (allowAlpha) image("checker-24.svg".image(), x + 1, y + 1, size - 2, size - 2, 10.radius())
                NVGRenderer.circle(centerX, centerY, r - 4f, this.colour!!.rgb)
            }
        }.add()

        onClick {
            animate = true
        }

        watch(block) {
            val (x, y) = block()
            val duration = if (animate || (!ui.eventManager.mouseDown && isRainbow?.get() == false)) 0.15.seconds else 0f
            pointerX.animate(to = (x * w).coerceIn(offset, w - offset), duration, Animation.Style.EaseOutQuad)
            pointerY?.animate(to = (y * height).coerceIn(offset, height - offset), duration, Animation.Style.EaseOutQuad)

            animate = false
            redraw()
        }
    }

    onClick {
        closePopup()
    }

    block(
        constrain(
            x = pos.x, y = pos.y,
            w = Bounding + thickness,
            h = Bounding + thickness
        ),
        colour = theme.background,
        6.radius()
    ) {
        onClick { true }

        dropShadow(
            colour = Colour.BLACK.withAlpha(0.5f),
            blur = 4f,
            spread = 3f,
            radius = 6.radius()
        )

        column {
            sectionRow(40.px) {
                divider(14.px)
                text(
                    string = "Colour Picker",
                    colour = theme.textPrimary,
                    size = 40.percent,
                    pos = at(y = Centre - thickness / 2.px)
                )

                text(
                    string = "×",
                    colour = theme.textPrimary,
                    size = 22.5.px,
                    pos = at(x = 14.px.alignOpposite, y = Centre - thickness / 2.px)
                ) {
                    cursor(CursorShape.HAND)
                    onClick {
                        closePopup()
                    }
                }
            }

            group(size(width.px, height.px)) {
//                cursor(CursorShape.CROSSHAIR)
                block(
                    copies(),
                    colours = Colour.WHITE to colourOnlyHue,
                    gradient = Gradient.LeftToRight,
                )
                block(
                    copies(),
                    colours = Colour.TRANSPARENT to Colour.BLACK,
                    gradient = Gradient.TopToBottom,
                )

                pointer(onlyX = false) {
                    value.saturation to 1f - value.brightness
                }

                onMouseDrag { x, y ->
                    value.saturation = x
                    value.brightness = (1f - y)
                    true
                }
            }

            val padding = 16.px

            column(constrain(x = padding, w = width.px - padding * 2.px, h = Bounding)) {
                divider(padding)

                row(size(w = Copying)) {
                    divider(7.px)
                    image(
                        image = theme.pickerImage,
                        constrain(y = Centre, w = 22.5.px, h = 22.5.px),
                    )
                    divider(11.px)

                    val w = width - (padding.pixels * 2 + 18 + 22.5f)
                    column(constrain(y = Centre, w = w.px), gap = 12.px) {
                        image(
                            image = "HueScale.png".image(),
                            constraints = size(/*Fill - 18.px*/w.px, 18.px),
                            8.radius()
                        ) {
                            cursor(CursorShape.HAND)

                            pointer(onlyX = true, w = w) {
                                value.hue to 0f
                            }

                            onMouseDrag { x, _ ->
                                value.hue = x
                                true
                            }
                        }

                        if (allowAlpha) image(
                            "checker-225.svg".image(),
                            constraints = size(/*Fill - 18.px*/w.px, 18.px),
                            8.radius()
                        ) {
                            block(
                                copies(),
                                colours = Colour.TRANSPARENT to colourOnlyHue,
                                gradient = Gradient.LeftToRight,
                                8.radius(),
                            ) {
                                cursor(CursorShape.HAND)

                                pointer(onlyX = true, w = w) {
                                    value.alpha to 0f
                                }

                                onMouseDrag { x, _ ->
                                    value.alpha = x
                                    if (isRainbow?.get() == true) {
                                        ref.set(value.copy(alpha = x))
                                    }
                                    true
                                }
                            }
                        }


                    }
                }

                divider(12.px)

                val entries = arrayListOf("Hex", "HSB", "RGB")
                var selected = if (isRainbow != null) {
                    entries.add("Rainbow")
                    if (isRainbow.get()) 3 else 0
                } else 0

                refreshableGroup(size(w = Copying, h = 30.px)) {

                    row(copies()) {
                        val endRadius = if (allowAlpha) 0.radius() else radius(tr = 6, br = 6)
                        val centreWidth = if (allowAlpha) 50.percent else 75.percent

                        fun ElementScope<*>.cell(
                            w: Constraint.Size,
                            rad: Radii = 0.radius(),
                            block: ElementScope<Block>.() -> ElementScope<*>
                        ) = block(size(w, Fill), theme.background, rad) {
                            outline(theme.border, thickness = 2.px)
                            val scope = block()

                            if (scope.element is TextInput) {
                                @Suppress("UNCHECKED_CAST")
                                delegateClick(scope as ElementScope<TextInput>)
                                cursor(CursorShape.IBEAM)
                            }
                        }

                        block(
                            size(w = 25.percent, h = Fill),
                            colour = theme.background,
                            radius = radius(tl = 6, bl = 6)
                        ) {
                            outline(theme.border, thickness = 2.px)
                            cursor(CursorShape.HAND)

                            var popup: Popup? = null

                            val text = text(
                                string = entries[selected],
                                colour = theme.textPrimary,
                                pos = at(Centre, Centre)
                            )

                            onClick {
                                popup?.closePopup()
                                popup = selector(
                                    entries = entries,
                                    selected = selected,
                                    pos = at(popupX(gap = -100f), popupY(gap = 5f, corner = true))
                                ) {
                                    selected = entries.indexOf(it)

                                    isRainbow?.set(selected == 3)

                                    text.string = it
                                    this@refreshableGroup.element.refresh()
                                }
                                true
                            }
                        }

                        when (selected) {
                            0 -> cell(centreWidth, endRadius) {
                                hexInput(
                                    value = { value.toHexString() },
                                    allowAlpha = false,
                                    pos = at(Centre, Centre)
                                ) {
                                    try {
                                        val newColor = Colour.RGB(hexToRGBA(it)).toHSB()
                                        value.hue = newColor.hue
                                        value.saturation = newColor.saturation
                                        value.brightness = newColor.brightness
//                                        if (allowAlpha) value.alpha = newColor.alpha
                                    } catch (_: Exception) { }
                                }
                            }
                            1, 2 -> { // HSB or RGB // todo fix hsb going sicko mode when switching from rainbow.
                                val isHSB = selected == 1
                                val props = if (isHSB) {
                                    listOf(value::hue to "°", value::saturation to "%", value::brightness to "%")
                                } else {
                                    listOf(value::red to "", value::green to "", value::blue to "")
                                }

                                props.forEachIndexed { i, (prop, unit) ->
                                    val isLast = i == props.lastIndex
                                    cell(centreWidth / 3.px, if (isLast) endRadius else 0.radius()) {
                                        @Suppress("UNCHECKED_CAST")
                                        numberInput(
                                            prop as KMutableProperty0<Number>,
                                            unit = unit,
                                            min = if (isHSB) null else 0,
                                            max = if (isHSB) null else 255,
                                            pos = at(Centre, Centre)
                                        )
                                    }
                                }
                            }
                            3 -> cell(centreWidth, endRadius) { // rainbow speed
                                slider(
                                    ::rainbowSpeed,
                                    min = 0.05f,
                                    max = 5.0f,
                                    increment = 0.05f,
                                    size = size(w = Copying - 10.percent, h = 6.px)
                                )
                            }
                        }

                        if (allowAlpha) cell(25.percent, radius(tr = 6, br = 6)) {
                            numberInput(value::alpha, unit = "%", pos = at(Centre, Centre))
                        }
                    }
                }
                divider(padding)
            }
        }
    }
}