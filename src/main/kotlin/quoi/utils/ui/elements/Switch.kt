package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.AspectRatio
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import kotlin.getValue
import kotlin.reflect.KMutableProperty0
import kotlin.setValue

// https://m3.material.io/components/switch/specs
inline fun ElementScope<*>.switch(
    ref: KMutableProperty0<Boolean>,
    size: Constraint.Size = 16.px,
    colour: Colour = theme.accent,
    pos: Positions = at(),
    crossinline onToggle: () -> Unit = {},
): ElementScope<*> {

    var value by ref

    val trackCol = Colour.Animated(
        from = theme.panel,
        to = colour { colour.rgb.multiply(1.15f) },
        swapIf = value
    )
    val handleCol = Colour.Animated(
        from = colour,
        to = colour { colour.rgb.multiply(0.5f) },
        swapIf = value
    )
    val handlePos = Animatable(
        from = 13.5.percent ,
        to = 46.15.percent,
        swapIf = value
    )
    val handleSize = Animatable(
        from = 50.percent,
        to = 75.percent,
        swapIf = value
    )

    return block(
        constrain(x = pos.x, y = pos.y, w = AspectRatio(1.625f), h = size),
        colour = trackCol,
        (size.pixels / 2f).radius()
    ) {
        outline(colour, thickness = (size * 0.0625.px).coerceAtLeast(2.px))
        hoverEffect(factor = 1.15f)
        cursor(CursorShape.HAND)

        block(
            constrain(
                x = handlePos, y = Centre,
                w = AspectRatio(1.0f), h = handleSize
            ),
            colour = handleCol,
            (size.pixels * 0.375f).radius()
        )
        onClick {
            onToggle()
            value = !value
            trackCol.animate(0.35.seconds, Animation.Style.EaseInOutQuint)
            handleCol.animate(0.35.seconds, Animation.Style.EaseInOutQuint)
            handlePos.animate(0.35.seconds, Animation.Style.EaseInOutQuint)
            handleSize.animate(0.35.seconds, Animation.Style.EaseInOutQuint)
            redraw()
            true
        }
    }
}