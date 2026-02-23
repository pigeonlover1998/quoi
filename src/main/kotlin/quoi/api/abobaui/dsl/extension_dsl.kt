package quoi.api.abobaui.dsl

import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply

inline fun ElementScope<*>.onDrag(button: Int = 0, crossinline block: () -> Boolean) {
    var pressed = false
    onClick(button) {
        pressed = true
        block.invoke()
    }
    onRelease(button) {
        pressed = false
    }
    onMouseMove {
        if (pressed) block.invoke() else false
    }
}

inline fun ElementScope<*>.onMouseDrag(crossinline block: (x: Float, y: Float) -> Boolean) {
    onDrag {
        block(
            ((ui.mx - element.x) / element.width).coerceIn(0f, 1f),
            ((ui.my - element.y) / element.height).coerceIn(0f, 1f),
        )
    }
}

fun ElementScope<*>.draggable(
    button: Int = 0,
    moves: Element = element,
    coerce: Boolean = true,
) {
    var initialized = false
    val px: Pixel = 0.px
    val py: Pixel = 0.px

    var clickedX = 0f
    var clickedY = 0f

    onClick(button) {
        if (!initialized) {
            initialized = true
            px.pixels = moves.internalX
            py.pixels = moves.internalY
            moves.constraints.x = px
            moves.constraints.y = py
        }
        clickedX = ui.mx - moves.internalX
        clickedY = ui.my - moves.internalY
        moves.moveToTop()
    }
    onDrag(button) {
        var newX = ui.mx - clickedX
        var newY = ui.my - clickedY
        if (coerce) {
            newX = newX.coerceIn(0f, ui.main.width - moves.screenWidth())
            newY = newY.coerceIn(0f, ui.main.height - moves.screenHeight())
        }
        px.pixels = newX
        py.pixels = newY
        redraw()
        false
    }
}

/**
 * Extension for [Block],
 * where the color gets darkened whenever the mouse is hovering over the element.
 *
 * It will mutate the color to a [Colour.Animated],
 * where first color is the original color, and the second is a darker version.
 */
fun ElementScope<Block>.hoverEffect(
    factor: Float,
    duration: Float = 0.2.seconds,
    style: Animation.Style = Animation.Style.Linear
) {
    val before = element.colour!!
    val hover = Colour.Animated(from = before, to = colour { before.rgb.multiply(factor = factor) })
    element.colour = hover
    onMouseEnterExit { hover.animate(duration, style) }
}
