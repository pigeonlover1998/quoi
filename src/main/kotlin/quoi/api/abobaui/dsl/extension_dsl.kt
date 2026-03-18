package quoi.api.abobaui.dsl

import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.alpha
import quoi.api.colour.blue
import quoi.api.colour.colour
import quoi.api.colour.getRGBA
import quoi.api.colour.green
import quoi.api.colour.multiply
import quoi.api.colour.red
import quoi.api.colour.toHSB
import quoi.utils.ThemeManager.theme

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
            val maxWidth = ui.main.width - moves.screenWidth()
            val maxHeight = ui.main.height - moves.screenHeight()

            if (maxWidth > 0) {
                newX = newX.coerceIn(0f, maxWidth)
            }
            if (maxHeight > 0) {
                newY = newY.coerceIn(0f, maxHeight)
            }
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

fun ElementScope<Block>.tonalHover(
    contentColour: Colour? = null,
    duration: Float = 0.2.seconds,
    style: Animation.Style = Animation.Style.Linear
) {
    val before = element.colour!!

    val anim = Colour.Animated(
        from = before,
        to = colour {
            val b = before.rgb
            val t = theme

            val col = contentColour ?: when (b) {
                t.surface.rgb,
                t.surfaceContainerLow.rgb,
                t.surfaceContainer.rgb,
                t.surfaceContainerHigh.rgb,
                t.surfaceContainerHighest.rgb,
                t.background.rgb -> t.onSurface

                t.surfaceVariant.rgb -> t.onSurfaceVariant

                t.primary.rgb -> t.onPrimary
                t.primaryContainer.rgb -> t.onPrimaryContainer

                t.secondary.rgb -> t.onSecondary
                t.secondaryContainer.rgb -> t.onSecondaryContainer

                t.tertiary.rgb -> t.onTertiary
                t.tertiaryContainer.rgb -> t.onTertiaryContainer

                t.error.rgb -> t.onError
                t.errorContainer.rgb -> t.onErrorContainer

                else -> if (before.toHSB().brightness < 0.5f) {
                    if (theme.isDark) theme.onSurface else theme.onPrimary
                } else {
                    if (theme.isDark) theme.onPrimary else theme.onSurface
                }
            }

            val c = col.rgb
            getRGBA(
                (b.red + (c.red - b.red) * 0.08f).toInt(),
                (b.green + (c.green - b.green) * 0.08f).toInt(),
                (b.blue + (c.blue - b.blue) * 0.08f).toInt(),
                b.alpha
            )
        }
    )

    element.colour = anim
    onMouseEnterExit { anim.animate(duration, style) }
}
