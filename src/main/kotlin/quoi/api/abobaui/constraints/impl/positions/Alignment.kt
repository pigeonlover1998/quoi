package quoi.api.abobaui.constraints.impl.positions

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

sealed interface Alignment : Constraint.Position {

    val position: Constraint.Position

    // aligns to element's centre.
    // don't confuse with [quoi.api.abobaui.constraints.impl.positions.Centre] which places the element in the centre of the parent
    class Centre(override val position: Constraint.Position) : Alignment {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            return position.calculatePos(element, horizontal) - element.getSize(horizontal) / 2
        }
    }

    // right to left alignment (by default it's left to right)
    class Right(override val position: Constraint.Position) : Alignment {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            return position.calculatePos(element, horizontal) - element.getSize(horizontal)
        }
    }

    class Opposite(override val position: Constraint.Position) : Alignment {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            val width = (element.parent?.getSize(horizontal) ?: 0f)
            return (width - element.getSize(horizontal) - position.calculatePos(element, horizontal))
        }
    }

    class Relative(override val position: Constraint.Position, val factor: Float) : Alignment {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            return position.calculatePos(element, horizontal) - (element.getSize(horizontal) * factor)
        }
    }
}