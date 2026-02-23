package quoi.api.abobaui.constraints

import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.elements.Element

sealed interface Constraint {
    fun calculate(element: Element, type: Int = 0): Float

    fun reliesOnChildren(): Boolean = false

    fun reliesOnParent(): Boolean = false

    fun undefined() = this is Undefined

    interface Position : Constraint {
        fun calculatePos(element: Element, horizontal: Boolean): Float
        override fun calculate(element: Element, type: Int): Float = calculatePos(element, type == 0)
    }

    interface Size : Constraint {
        fun calculateSize(element: Element, horizontal: Boolean): Float
        override fun calculate(element: Element, type: Int): Float = calculateSize(element, type == 2)
    }

    interface Measurement : Position, Size {

        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            return calculate(element, if (horizontal) 0 else 1)
        }

        override fun calculateSize(element: Element, horizontal: Boolean): Float {
            return calculate(element, if (horizontal) 2 else 3)
        }

        override fun calculate(element: Element, type: Int): Float
    }
}