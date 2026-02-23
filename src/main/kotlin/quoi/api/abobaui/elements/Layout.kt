package quoi.api.abobaui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.elements.impl.layout.Row

abstract class Layout(
    constraints: Constraints,
    val gap: Constraint.Size?
) : BlankElement(constraints) {

    init {
        if (constraints.width.undefined()) constraints.width = Bounding
        if (constraints.height.undefined()) constraints.height = Bounding
    }

    final override fun getDefaultPositions() = Pair(Undefined, Undefined)

    internal class Divider(
        width: Constraint.Size = Copying,
        height: Constraint.Size = Copying,
    ) : BlankElement(size(width, height)) {

        init {
            require(width != height) {
                "When using divider, it's width and height must not be equal"
            }
        }
    }

    companion object {
        // Creates a divider, with a specified size, inside a layout.
        @AbobaDSL
        fun <E : Layout> ElementScope<E>.divider(size: Constraint.Size) {
            val divider = when (element) {
                is Column -> Divider(height = size)
                is Row -> Divider(width = size)
                else -> return
            }
            element.addElement(divider)
        }

        // Creates a section, with a specified size, inside a layout.
        // This is intended to hold elements or contain functionality unlike [divider].
        @AbobaDSL
        fun <E : Layout> ElementScope<E>.section(
            size: Constraint.Size,
            block: ElementScope<Group>.() -> Unit = {}
        ) {
            val group = when (element) {
                is Column -> Group(size(w = Copying, h = size))
                is Row -> Group(size(w = size, h = Copying))
                else -> return
            }
            group.scope(block)
        }
    }
}