package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.AspectRatio
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.colour.withAlpha
import quoi.api.input.CatKeys
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.watch
import kotlin.getValue
import kotlin.math.abs
import kotlin.math.round
import kotlin.reflect.KMutableProperty0
import kotlin.setValue

@Suppress("UNCHECKED_CAST")
fun <T : Number> ElementScope<*>.slider(
    ref: KMutableProperty0<T>,
    min: T,
    max: T,
    increment: Number,
    colour: Colour = theme.accent,
    pos: Positions = at(),
    size: Sizes = size()
): ElementScope<*> {

    var value by ref

    val min = min.toDouble()
    val max = max.toDouble()
    val increment = increment.toDouble()

    fun set(new: Number) {
        val n = (round((new.toDouble() / increment)) * increment).coerceIn(min, max)
        value = n as T
    }
    fun getPercent(): Float = ((value.toDouble() - min) / (max - min)).toFloat()

    val height = size.height.calculateSize(element, horizontal = true)
    val width = size.width.calculateSize(element, horizontal = true)
    val radius = (height * 0.3125f).radius()
    val margin = if (width > 0) height * 0.75f / width else 0.015f

    fun Float.coerce() = coerceIn(margin, 1f - margin)

    return block(constrain(pos.x, pos.y, size.width, size.height), theme.panel, radius) {
        cursor(CursorShape.HAND)

        dropShadow(
            colour = Colour.BLACK.withAlpha(0.1f),
            blur = 5f,
            spread = 2f,
            offsetY = 1.5f,
            radius = radius
        )

        val knobPos = Animatable.Raw(getPercent().coerce())

        var animate = false
        var clicked = false

        val col = Colour.Animated(
            from = colour,
            to = colour { theme.accent.rgb.multiply(1.15f) }
        )

        watch(ref) {
            if (animate || !clicked) {
                knobPos.animate(to = getPercent().coerce(), 0.4.seconds, style = Animation.Style.EaseOutQuint)
                animate = false
            }
            this@slider.element.redraw()
        }

        block(
            constrain((-0.5).px, (-0.5).px, knobPos * Copying + 1.px, 100.percent + 1.px),
            colour = col,
            radius = radius
        ).outline(colour { col.rgb.multiply(0.9f) }, thickness = 2.px)

        block(
            constrain(
                x = (knobPos * 100.percent).alignCentre, y = Centre,
                w = AspectRatio(1f), h = Copying + 50.percent
            ),
            colour = colour { col.rgb.multiply(1.1f) },
            radius = radius
        ) {
            outline(col, thickness = 2.px)
            dropShadow(
                colour = Colour.BLACK.withAlpha(0.25f),
                blur = 5f,
                spread = 2f,
                radius = radius
            )
        }
        onMouseEnterExit {
            col.animate(0.25.seconds, Animation.Style.Linear)
        }
        onMouseEnter {
            ui.focus(element)
        }
        onMouseExit {
            ui.unfocus()
        }
        onClick {
            clicked = true
            animate = true

            val percent = ((ui.mx - element.x) / element.width).coerceIn(0f, 1f)
            set(percent * (max - min) + min)
            knobPos.animate(to = percent, 0.4.seconds, Animation.Style.EaseOutQuint)
            this@slider.element.redraw()
            true
        }
        onMouseMove {
            if (clicked) {
                val percent = ((ui.mx - element.x) / element.width).coerceIn(0f, 1f)
                set(percent * (max - min) + min)
                knobPos.to(percent.coerce())
                element.redraw()
            }
        }
        onRelease {
            if (clicked) this@slider.element.redraw()
            clicked = false
        }

        onKeyPressed { (key) ->
            when(key) {
                CatKeys.KEY_LEFT -> {
                    set(value.toDouble() - increment)
                    animate = true
                    true
                }

                CatKeys.KEY_RIGHT -> {
                    set(value.toDouble() + increment)
                    animate = true
                    true
                }
                else -> false
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Number> ElementScope<*>.rangeSlider(
    ref: KMutableProperty0<Pair<T, T>>,
    min: T,
    max: T,
    increment: Number,
    colour: Colour = theme.accent,
    pos: Positions = at(),
    size: Sizes = size()
): ElementScope<*> {
    var value by ref

    val min = min.toDouble()
    val max = max.toDouble()
    val increment = increment.toDouble()

    fun set(first: Number, second: Number) {
        val v1 = (round(first.toDouble() / increment) * increment).coerceIn(min, max)
        val v2 = (round(second.toDouble() / increment) * increment).coerceIn(min, max)

        value =
            if (v1 > v2) v2 as T to v1 as T
            else
                v1 as T to v2 as T
    }
    fun getPercent(v: Number): Float = ((v.toDouble() - min) / (max - min)).toFloat()

    val height = size.height.calculateSize(element, horizontal = true)
    val width = size.width.calculateSize(element, horizontal = true)
    val radius = (height * 0.3125f).radius()
    val margin = if (width > 0) height * 0.75f / width else 0.015f

    fun Float.coerce() = coerceIn(margin, 1f - margin)

    return block(constrain(pos.x, pos.y, size.width, size.height), theme.panel, radius) {
        cursor(CursorShape.HAND)

        dropShadow(
            colour = Colour.BLACK.withAlpha(0.1f),
            blur = 5f,
            spread = 2f,
            offsetY = 1.5f,
            radius = radius
        )

        val knob1Pos = Animatable.Raw(getPercent(value.first).coerce())
        val knob2Pos = Animatable.Raw(getPercent(value.second).coerce())

        var animate = false
        var clicked = false
        var draggingKnob = 0

        val col = Colour.Animated(
            from = colour,
            to = colour { theme.accent.rgb.multiply(1.15f) }
        )

        watch(ref) {
            if (animate || !clicked) {
                knob1Pos.animate(to = getPercent(value.first).coerce(), 0.4.seconds, style = Animation.Style.EaseOutQuint)
                knob2Pos.animate(to = getPercent(value.second).coerce(), 0.4.seconds, style = Animation.Style.EaseOutQuint)
                animate = false
            }
            this@rangeSlider.element.redraw()
        }

        block(
            constrain(
                x = knob1Pos * 100.percent - 0.5.px,
                y = (-0.5).px,
                w = (knob2Pos - knob1Pos) * 100.percent + 1.px,
                h = 100.percent + 1.px
            ),
            colour = col,
            radius = radius
        ).outline(colour { col.rgb.multiply(0.9f) }, thickness = 2.px)


        fun knob(pos: Animatable.Raw) = block(
            constrain(
                x = (pos * 100.percent).alignCentre, y = Centre,
                w = AspectRatio(1f), h = Copying + 50.percent
            ),
            colour = colour { col.rgb.multiply(1.1f) },
            radius = radius
        ) {
            outline(col, thickness = 2.px)
            dropShadow(
                colour = Colour.BLACK.withAlpha(0.25f),
                blur = 5f,
                spread = 2f,
                radius = radius
            )
        }

        knob(knob1Pos)
        knob(knob2Pos)

        onMouseEnterExit {
            col.animate(0.25.seconds, Animation.Style.Linear)
        }
        onMouseEnter {
            ui.focus(element)
        }
        onMouseExit {
            ui.unfocus()
        }

        onClick {
            clicked = true
            animate = true

            val percent = ((ui.mx - element.x) / element.width).coerceIn(0f, 1f)
            val dist1 = abs(percent - getPercent(value.first))
            val dist2 = abs(percent - getPercent(value.second))

            draggingKnob = if (dist1 < dist2) 1 else 2

            val currentVal = percent * (max - min) + min
            if (draggingKnob == 1) {
                set(currentVal, value.second)
                knob1Pos.animate(to = percent, 0.4.seconds, Animation.Style.EaseOutQuint)
            } else {
                set(value.first, currentVal)
                knob2Pos.animate(to = percent, 0.4.seconds, Animation.Style.EaseOutQuint)
            }
            true
        }
        onMouseMove { // todo fix some day
            if (clicked) {
                val percent = ((ui.mx - element.x) / element.width).coerceIn(0f, 1f)
                val currentVal = percent * (max - min) + min

                if (draggingKnob == 1) {
                    set(currentVal.coerceAtMost(value.second.toDouble()), value.second)
                    knob1Pos.to(percent.coerceIn(margin, getPercent(value.second)))
                } else {
                    set(value.first, currentVal.coerceAtLeast(value.first.toDouble()))
                    knob2Pos.to(percent.coerceIn(getPercent(value.first), 1f - margin))
                }
                this@rangeSlider.element.redraw()
            }
        }
        onRelease {
            if (clicked) this@rangeSlider.element.redraw()
            clicked = false
            draggingKnob = 0
        }

        onKeyPressed { (key, mods) ->
            val knobToMove = if (mods.isShiftDown) 2 else 1
            val dir = when (key) {
                CatKeys.KEY_LEFT -> -1.0
                CatKeys.KEY_RIGHT -> 1.0
                else -> return@onKeyPressed false
            }

            animate = true
            if (knobToMove == 1) {
                set(value.first.toDouble() + (dir * increment), value.second)
            } else {
                set(value.first, value.second.toDouble() + (dir * increment))
            }
            true
        }
    }

}