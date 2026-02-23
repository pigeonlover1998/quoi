package quoi.api.abobaui.constraints.impl.size

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

// copies parent's size
object Copying : Constraint.Size {

    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        return (element.parent?.getSize(horizontal) ?: 0f)
    }

    override fun reliesOnParent() = true
}