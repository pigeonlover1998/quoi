package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.popup
import quoi.api.colour.Colour
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor

inline fun <T> ElementScope<*>.selector(
    entries: List<T>,
    selected: Int? = null,
    displayString: (T) -> String = { it.toString() },
    colour: Colour = theme.panel,
    outline: Colour = theme.border,
    thickness: Constraint.Measurement = 2.px,
    pos: Positions = at(),
    size: Sizes = size(130.px, 30.px),
    crossinline onSelect: (T) -> Unit,
) = popup(copies(), smooth = false) {

    onClick {
        closePopup()
    }

    block(
        constrain(
            pos.x, pos.y,
            w = Bounding + thickness - 0.5.px,
            h = Bounding + thickness - 0.5.px
        ),
        colour = colour,
        5.radius()
    ) {
        outline(outline, thickness)
        cursor(CursorShape.HAND)
        onClick { true }

        column {
            entries.forEachIndexed { i, comp ->
                val col = if (selected == i) theme.accent else colour
                block(
                    size,
                    colour = col,
                    3.5.radius()
                ) {
                    hoverEffect(1.1f)

                    text(
                        string = displayString(comp),
                        colour = theme.textPrimary
                    )

                    onClick {
                        onSelect(comp)
                        closePopup()
                        true
                    }
                }
            }
        }
    }
}