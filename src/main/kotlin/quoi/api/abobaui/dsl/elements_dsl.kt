package quoi.api.abobaui.dsl

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.utils.ui.data.Radii

inline fun ElementScope<*>.outlineBlock(
    constraints: Constraints,
    colour: Colour,
    thickness: Constraint.Measurement,
    radius: Radii? = null,
    block: ElementScope<Block>.() -> Unit = {}
) = block(constraints, Colour.TRANSPARENT, radius, block).outline(colour, thickness)