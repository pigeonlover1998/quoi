package quoi.api.abobaui.constraints.impl.size

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

// element expands/shrinks to fit all its children
object Bounding : Constraint.Size {

    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        val children = element.children ?: return 0.0f

        var value = 0.0f
        children.forEach {
            if (!it.enabled) return@forEach
            if (!it.constraints.getSize(horizontal).reliesOnParent()) {
                val new = it.getPosition(horizontal) + it.getSize(horizontal)
                if (new > value) value = new
            }
        }
        return value
    }

    override fun reliesOnChildren(): Boolean = true
}