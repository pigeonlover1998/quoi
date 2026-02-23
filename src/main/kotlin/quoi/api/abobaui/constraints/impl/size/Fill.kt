package quoi.api.abobaui.constraints.impl.size

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

// element stretches to parent's size
object Fill : Constraint.Size {

    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        var p = element.parent ?: return 0f
        if (p.constraints.getSize(horizontal).reliesOnChildren()) p = p.parent ?: return 0f
        return p.getSize(horizontal) - element.getPosition(horizontal)
    }
}